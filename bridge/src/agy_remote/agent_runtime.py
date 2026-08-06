from __future__ import annotations

import asyncio
import codecs
import json
import os
import re
import shutil
import subprocess
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any
from urllib.parse import unquote


_ANSI_ESCAPE = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")
_NON_INTERACTIVE_PLAN_POLICY = """[POLÍTICA DA PONTE REMOTA]
Esta execução é não interativa e o usuário não ativou Skip de permissões.
Não use run_command, Bash, terminal, shell nem qualquer ferramenta que peça confirmação.
Não use Edit, replace_file_content, write_file ou qualquer ferramenta que altere arquivos.
Não use grep_search neste Windows. Para inspecionar o projeto, use somente ferramentas
não interativas de leitura, como list_dir e view_file/read_file. Se uma informação exigir
terminal, prossiga com as demais evidências, mencione a limitação e produza sempre uma
resposta final em texto. Ao terminar a análise, não tente implementar o plano: pare de
usar ferramentas e entregue o plano ao usuário.

[TAREFA DO USUÁRIO]
"""

_PLAN_EMPTY_RECOVERY_PROMPT = """[RECUPERAÇÃO DA PONTE REMOTA]
Sua execução anterior terminou sem uma resposta final, possivelmente porque tentou usar
uma ferramenta de edição que o modo planejamento recusou. Não chame nenhuma ferramenta
agora e não altere arquivos. Com base somente na análise e nas evidências que já estão
nesta conversa, entregue imediatamente a resposta final no idioma do usuário. Inclua o
diagnóstico e um plano objetivo de implementação. Se alguma evidência for insuficiente,
declare a limitação sem tentar inspecionar novamente.
"""

_READ_ONLY_EMPTY_RECOVERY_PROMPT = """[RECUPERAÇÃO DA PONTE REMOTA]
Sua execução anterior terminou sem uma resposta final. Não chame nenhuma ferramenta agora
e não altere arquivos. Com base somente na análise e nas evidências que já estão nesta
conversa, entregue imediatamente a resposta final no idioma do usuário. Se alguma
evidência for insuficiente, declare a limitação sem tentar inspecionar novamente.
"""

_AUTONOMOUS_PROJECT_POLICY = """[POLÍTICA DO MODO AUTÔNOMO]
O usuário ativou explicitamente o modo autônomo para este projeto. Execute as alterações,
comandos e testes necessários sem pedir aprovação. Trabalhe somente dentro da pasta do
projeto autorizada abaixo. Não leia, altere nem execute conteúdo fora dela. Se a tarefa
realmente exigir acesso externo, explique a limitação na resposta final em vez de tentar
ultrapassar esse limite.

[PASTA AUTORIZADA]
{root}

[TAREFA DO USUÁRIO]
"""


def sanitize_cli_output(value: str) -> str:
    """Turn terminal-oriented CLI output into clean chat markdown."""
    value = _ANSI_ESCAPE.sub("", value).replace("\r\n", "\n").replace("\r", "\n")
    value = "\n".join(line.rstrip() for line in value.splitlines())
    return re.sub(r"\n{3,}", "\n\n", value).strip()


def sanitize_cli_chunk(value: str) -> str:
    """Clean a streamed chunk without trimming meaningful delta whitespace."""
    return _ANSI_ESCAPE.sub("", value).replace("\r\n", "\n").replace("\r", "\n")


def find_agy_cli() -> Path:
    """Locate the official Antigravity CLI without depending on shell PATH refreshes."""
    configured = os.environ.get("AGY_CLI_PATH")
    candidates = [
        Path(configured) if configured else None,
        Path(os.environ.get("LOCALAPPDATA", "")) / "agy" / "bin" / "agy.exe",
        Path(shutil.which("agy") or "") if shutil.which("agy") else None,
    ]
    for candidate in candidates:
        if candidate and candidate.is_file():
            return candidate.resolve()
    raise FileNotFoundError(
        "Antigravity CLI não encontrado. Instale o agy e faça login uma vez no Windows."
    )


def list_available_models() -> list[str]:
    """Return model identifiers advertised by the installed Antigravity CLI."""
    fallback_models = [
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-pro",
        "gemini-1.5-flash",
        "claude-3-5-sonnet-20241022",
    ]
    try:
        creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        result = subprocess.run(
            [str(find_agy_cli()), "models"],
            capture_output=True,
            text=True,
            timeout=15,
            creationflags=creationflags,
            check=True,
        )
        models = [line.strip() for line in result.stdout.splitlines() if line.strip()]
        return models if models else fallback_models
    except Exception:
        return fallback_models

def normalize_model_name(raw_model: str) -> str:
    """Normalize the UI model string to the canonical API model ID."""
    mapping = {
        "gemini-3.1-pro-high": "gemini-3.1-pro",
        "gemini-3.1-pro-low": "gemini-3.1-pro",
        "gemini-3.5-flash-high": "gemini-3.5-flash",
        "gemini-3.5-flash-medium": "gemini-3.5-flash",
        "gemini-3.5-flash-low": "gemini-3.5-flash",
        "gemini-3.6-flash-high": "gemini-3.6-flash",
        "gemini-3.6-flash-medium": "gemini-3.6-flash-medium",
        "gemini-3.6-flash-low": "gemini-3.6-flash",
        "claude-sonnet-4-6": "claude-sonnet-4.6",
        "claude-opus-4-6-thinking": "claude-opus-4.6",
        "gpt-oss-120b-medium": "gpt-120b",
    }
    return mapping.get(raw_model, raw_model)


class AntigravityCliSession:
    """Runs account-authenticated Antigravity CLI turns for one remote conversation."""

    _cli_lock = asyncio.Lock()

    def __init__(
        self,
        root: Path,
        cli_conversation_id: str | None = None,
    ) -> None:
        self.root = root.resolve(strict=True)
        self.cli_conversation_id = cli_conversation_id
        self._process: asyncio.subprocess.Process | None = None

    @staticmethod
    def _last_conversations_path() -> Path:
        return Path.home() / ".gemini" / "antigravity-cli" / "cache" / "last_conversations.json"

    @staticmethod
    def _projects_path() -> Path:
        return Path.home() / ".gemini" / "config" / "projects"

    def _discover_conversation_id(self) -> str | None:
        path = self._last_conversations_path()
        try:
            values = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None
        normalized_root = os.path.normcase(str(self.root))
        for workspace, conversation_id in values.items():
            if os.path.normcase(os.path.abspath(workspace)) == normalized_root:
                return str(conversation_id)
        return None

    def _discover_project_id(self) -> str | None:
        """Reuse agy's project for this workspace so its permission grants persist."""
        projects_path = self._projects_path()
        try:
            candidates = sorted(
                projects_path.glob("*.json"),
                key=lambda item: item.stat().st_mtime,
                reverse=True,
            )
        except OSError:
            return None
        normalized_root = os.path.normcase(str(self.root))
        for path in candidates:
            try:
                project = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            resources = project.get("projectResources", {}).get("resources", [])
            for resource in resources:
                folder_uri = str(resource.get("folderUri", ""))
                if not folder_uri.lower().startswith("file://"):
                    continue
                raw_path = unquote(folder_uri[7:]).replace("/", os.sep)
                if os.name == "nt" and re.match(r"^\\[A-Za-z]:", raw_path):
                    raw_path = raw_path[1:]
                if os.path.normcase(os.path.abspath(raw_path)) == normalized_root:
                    project_id = str(project.get("id", "")).strip()
                    if project_id:
                        return project_id
        return None

    def _command(
        self,
        prompt: str,
        cli: Path | None = None,
        model: str | None = None,
        execution_mode: str = "autonomous_project",
    ) -> list[str]:
        executable = cli or find_agy_cli()
        raw_model = model or os.environ.get("AGY_MODEL", "gemini-2.5-flash")
        selected_model = normalize_model_name(raw_model)
        is_non_editing_mode = execution_mode in {"read_only", "plan"}
        cli_mode = "plan" if is_non_editing_mode else "accept-edits"
        if is_non_editing_mode:
            effective_prompt = f"{_NON_INTERACTIVE_PLAN_POLICY}[MODELO ATIVO: {selected_model}]\n\n{prompt}"
        elif execution_mode == "autonomous_project":
            policy = _AUTONOMOUS_PROJECT_POLICY.format(root=self.root)
            effective_prompt = f"{policy}[MODELO ATIVO: {selected_model}]\n\n{prompt}"
        else:
            effective_prompt = f"[MODELO ATIVO: {selected_model}]\n\n{prompt}"
        args = [
            str(executable),
            "--print-timeout=30m",
            f"--mode={cli_mode}",
            f"--model={selected_model}",
        ]
        # The CLI's Windows terminal sandbox can open a local ShellExecute/UAC
        # dialog that a remote phone cannot answer. Autonomous mode is the
        # user's explicit opt-in to direct execution, so it uses Skip without
        # that sandbox. Planning/read-only retain the sandbox and normal
        # permission policy.
        if is_non_editing_mode:
            args.append("--sandbox")
        if execution_mode == "autonomous_project":
            args.append("--dangerously-skip-permissions")
        args.append(f"--add-dir={self.root}")
        if self.cli_conversation_id:
            args.append(f"--conversation={self.cli_conversation_id}")
        else:
            # Reuse the workspace's agy project so user permission grants survive
            # across remote conversations. Create it only on the first use.
            project_id = self._discover_project_id()
            args.append(f"--project={project_id}" if project_id else "--new-project")
        args.append(f"--print={effective_prompt}")
        return args

    async def stream(
        self,
        prompt: str,
        model: str | None = None,
        execution_mode: str = "autonomous_project",
        _allow_empty_recovery: bool = True,
    ) -> AsyncIterator[tuple[str, dict[str, Any]]]:
        args = self._command(prompt, model=model, execution_mode=execution_mode)

        creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        async with self._cli_lock:
            stderr_task: asyncio.Task[bytes] | None = None
            emitted_text = False
            try:
                self._process = await asyncio.create_subprocess_exec(
                    *args,
                    cwd=str(self.root),
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    creationflags=creationflags,
                )
                assert self._process.stdout is not None
                assert self._process.stderr is not None
                stderr_task = asyncio.create_task(self._process.stderr.read())
                decoder = codecs.getincrementaldecoder("utf-8")("replace")
                while True:
                    chunk = await self._process.stdout.read(1024)
                    if not chunk:
                        break
                    text = sanitize_cli_chunk(decoder.decode(chunk))
                    if text:
                        emitted_text = True
                        yield "text", {"text": text}
                tail = sanitize_cli_chunk(decoder.decode(b"", final=True))
                if tail:
                    emitted_text = True
                    yield "text", {"text": tail}
                stderr = await stderr_task
                await self._process.wait()
                return_code = self._process.returncode
            except asyncio.CancelledError:
                await self._stop_process_tree()
                raise
            finally:
                if stderr_task and not stderr_task.done():
                    stderr_task.cancel()
                self._process = None

        if return_code != 0:
            detail = stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(detail or f"Antigravity CLI encerrou com código {return_code}")
        if not emitted_text:
            # The first non-editing turn for a new workspace may create the agy
            # project and then soft-deny its first read permission. Retry once
            # using that newly persisted project, which preserves its grants.
            if (
                execution_mode in {"read_only", "plan"}
                and "--new-project" in args
                and self._discover_project_id()
            ):
                async for kind, data in self.stream(
                    prompt,
                    model=model,
                    execution_mode=execution_mode,
                    _allow_empty_recovery=_allow_empty_recovery,
                ):
                    yield kind, data
                return
            # Print mode can soft-deny a tool (most commonly Edit in plan mode)
            # after doing all of the analysis, then exit successfully with empty
            # stdout. Resume the conversation once and explicitly ask for the
            # final text without any more tool calls so that work is not lost.
            if execution_mode in {"read_only", "plan"} and _allow_empty_recovery:
                recovered_conversation_id = self._discover_conversation_id()
                if recovered_conversation_id:
                    self.cli_conversation_id = recovered_conversation_id
                    recovery_prompt = (
                        _PLAN_EMPTY_RECOVERY_PROMPT
                        if execution_mode == "plan"
                        else _READ_ONLY_EMPTY_RECOVERY_PROMPT
                    )
                    async for kind, data in self.stream(
                        recovery_prompt,
                        model=model,
                        execution_mode=execution_mode,
                        _allow_empty_recovery=False,
                    ):
                        yield kind, data
                    return
            raise RuntimeError(
                "O Antigravity encerrou a tarefa sem produzir uma resposta. "
                "A recuperação automática também não gerou texto. "
                "Verifique a sessão do Antigravity no computador."
            )

        self.cli_conversation_id = self.cli_conversation_id or self._discover_conversation_id()
        yield "complete", {"cliConversationId": self.cli_conversation_id}

    async def _stop_process_tree(self) -> None:
        process = self._process
        if not process or process.returncode is not None:
            return
        if os.name == "nt":
            killer = await asyncio.create_subprocess_exec(
                "taskkill",
                "/PID",
                str(process.pid),
                "/T",
                "/F",
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            await killer.wait()
        else:
            process.terminate()
            await process.wait()

    async def close(self) -> None:
        await self._stop_process_tree()


class SessionRegistry:
    def __init__(self) -> None:
        self._sessions: dict[str, AntigravityCliSession] = {}
        self._tasks: dict[str, asyncio.Task[Any]] = {}
        self._cancel_reasons: dict[str, str] = {}

    def get(
        self,
        conversation_id: str,
        root: Path,
        cli_conversation_id: str | None = None,
    ) -> AntigravityCliSession:
        session = self._sessions.get(conversation_id)
        if not session:
            session = self._sessions[conversation_id] = AntigravityCliSession(
                root, cli_conversation_id
            )
        return session

    def set_task(self, conversation_id: str, task: asyncio.Task[Any]) -> None:
        current = self._tasks.get(conversation_id)
        if current and not current.done():
            task.cancel()
            raise RuntimeError("conversation already has an active turn")
        self._cancel_reasons.pop(conversation_id, None)
        self._tasks[conversation_id] = task

    def consume_cancel_reason(self, conversation_id: str) -> str | None:
        return self._cancel_reasons.pop(conversation_id, None)

    async def cancel(self, conversation_id: str, reason: str = "execution_interrupted") -> bool:
        task = self._tasks.get(conversation_id)
        if not task or task.done():
            self._cancel_reasons.pop(conversation_id, None)
            return False
        self._cancel_reasons[conversation_id] = reason
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)
        return True

    async def close(self) -> None:
        for conversation_id, task in self._tasks.items():
            self._cancel_reasons[conversation_id] = "service_shutdown"
            task.cancel()
        await asyncio.gather(*self._tasks.values(), return_exceptions=True)
        await asyncio.gather(
            *(session.close() for session in self._sessions.values()), return_exceptions=True
        )
        self._sessions.clear()
        self._tasks.clear()
        self._cancel_reasons.clear()
