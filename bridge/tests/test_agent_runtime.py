import asyncio
import json
from pathlib import Path

from agy_remote.agent_runtime import AntigravityCliSession


def test_new_cli_conversation_explicitly_selects_authorized_workspace(tmp_path: Path) -> None:
    session = AntigravityCliSession(tmp_path)

    command = session._command("hello", Path("C:/agy.exe"))

    assert f"--add-dir={tmp_path.resolve()}" in command
    assert "--sandbox" not in command
    assert "--dangerously-skip-permissions" in command
    assert "--model=gemini-3.6-flash-medium" in command
    assert "--new-project" in command
    assert not any(value.startswith("--conversation=") for value in command)


def test_existing_cli_conversation_keeps_workspace_and_runtime_id(tmp_path: Path) -> None:
    session = AntigravityCliSession(tmp_path, "runtime-id")

    command = session._command("hello", Path("C:/agy.exe"))

    assert f"--add-dir={tmp_path.resolve()}" in command
    assert "--sandbox" not in command
    assert "--dangerously-skip-permissions" in command
    assert "--model=gemini-3.6-flash-medium" in command
    assert "--conversation=runtime-id" in command
    assert "--new-project" not in command


def test_plan_mode_keeps_skip_disabled_and_avoids_interactive_tools(tmp_path: Path) -> None:
    command = AntigravityCliSession(tmp_path)._command(
        "inspect", Path("C:/agy.exe"), execution_mode="plan"
    )

    assert "--mode=plan" in command
    assert "--sandbox" in command
    assert "--dangerously-skip-permissions" not in command
    assert "Não use run_command" in command[-1]
    assert "Não use Edit" in command[-1]
    assert command[-1].endswith("[TAREFA DO USUÁRIO]\ninspect")


def test_autonomous_mode_is_scoped_and_does_not_request_approval(tmp_path: Path) -> None:
    command = AntigravityCliSession(tmp_path)._command(
        "implement", Path("C:/agy.exe"), execution_mode="autonomous_project"
    )

    assert "--sandbox" not in command
    assert "--dangerously-skip-permissions" in command
    assert "Execute as alterações" in command[-1]
    assert "sem pedir aprovação" in command[-1]
    assert str(tmp_path.resolve()) in command[-1]
    assert command[-1].endswith("[TAREFA DO USUÁRIO]\nimplement")


def test_existing_agy_project_is_reused_for_workspace(
    tmp_path: Path, monkeypatch
) -> None:
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    projects = tmp_path / "projects"
    projects.mkdir()
    (projects / "agy-project-id.json").write_text(
        json.dumps(
            {
                "id": "agy-project-id",
                "projectResources": {
                    "resources": [{"folderUri": workspace.as_uri()}],
                },
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(
        AntigravityCliSession,
        "_projects_path",
        staticmethod(lambda: projects),
    )

    command = AntigravityCliSession(workspace)._command(
        "inspect", Path("C:/agy.exe"), execution_mode="plan"
    )

    assert "--project=agy-project-id" in command
    assert "--new-project" not in command


class _FakePipe:
    def __init__(self, chunks: list[bytes]) -> None:
        self._chunks = iter(chunks)

    async def read(self, _size: int = -1) -> bytes:
        await asyncio.sleep(0)
        return next(self._chunks, b"")


class _FakeProcess:
    def __init__(self, stdout_chunks: list[bytes]) -> None:
        self.stdout = _FakePipe(stdout_chunks)
        self.stderr = _FakePipe([])
        self.returncode = 0

    async def wait(self) -> int:
        return self.returncode


async def test_plan_mode_recovers_final_text_after_soft_denied_edit(
    tmp_path: Path, monkeypatch
) -> None:
    commands: list[tuple[str, ...]] = []
    processes = iter(
        [
            _FakeProcess([]),
            _FakeProcess([b"Diagnostico e plano recuperados."]),
        ]
    )

    async def fake_create_subprocess_exec(*args: str, **_kwargs: object) -> _FakeProcess:
        commands.append(args)
        return next(processes)

    session = AntigravityCliSession(tmp_path)
    monkeypatch.setattr(asyncio, "create_subprocess_exec", fake_create_subprocess_exec)
    monkeypatch.setattr(session, "_discover_project_id", lambda: "agy-project-id")
    monkeypatch.setattr(session, "_discover_conversation_id", lambda: "runtime-recovery-id")

    events = [
        event
        async for event in session.stream(
            "analise o problema",
            model="test-model",
            execution_mode="plan",
        )
    ]

    assert len(commands) == 2
    assert "--conversation=runtime-recovery-id" in commands[1]
    assert any("RECUPERAÇÃO DA PONTE REMOTA" in arg for arg in commands[1])
    assert events == [
        ("text", {"text": "Diagnostico e plano recuperados."}),
        ("complete", {"cliConversationId": "runtime-recovery-id"}),
    ]
