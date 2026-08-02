from __future__ import annotations

import asyncio
import os
import subprocess
import time
from typing import Any
from uuid import uuid4

from .agent_runtime import SessionRegistry, list_available_models
from .builds import BuildManager
from .crypto import EnvelopeCrypto
from .database import Database
from .firebase_transport import FirebaseTransport
from .filesystem_access import (
    browse_directories,
    create_directory,
    create_project_directory,
    list_project_files,
    path_is_within,
    read_project_file,
    resolve_local_directory,
    validate_folder_name,
)
from .models import CommandPayload, Envelope, MessageType, Project


class BridgeService:
    def __init__(
        self,
        database: Database,
        crypto: EnvelopeCrypto,
        transport: FirebaseTransport,
    ) -> None:
        self.database = database
        self.crypto = crypto
        self.transport = transport
        self.sessions = SessionRegistry()
        self.builds = BuildManager()
        self.paused = False
        self._stop = asyncio.Event()
        self._approvals: dict[str, asyncio.Future[bool]] = {}
        self.available_models: list[str] = []

    async def emit(self, conversation_id: str, kind: MessageType, payload: dict[str, Any]) -> None:
        now = int(time.time() * 1000)
        envelope = self.crypto.encrypt(
            conversation_id=conversation_id,
            sequence=self.database.next_sequence("out", conversation_id),
            message_type=kind,
            payload=payload,
            created_at=now,
            expires_at=now + 7 * 24 * 60 * 60 * 1000,
        )
        last_error: Exception | None = None
        for attempt in range(6):
            try:
                await self.transport.publish_event(envelope)
                return
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                last_error = exc
                if attempt < 5:
                    await asyncio.sleep(min(2**attempt, 10))
        raise RuntimeError("Não foi possível enviar o evento ao celular") from last_error

    def _snapshot(self) -> dict[str, Any]:
        return {
            "projects": [
                {
                    "id": project.id,
                    "name": project.name,
                    "buildProfiles": [
                        {"id": profile.id, "name": profile.name}
                        for profile in project.build_profiles
                    ],
                }
                for project in self.database.list_projects()
            ],
            "tasks": [
                {
                    "taskId": task["id"],
                    "conversationId": task["conversation_id"],
                    "projectId": task["project_id"],
                    "status": task["status"],
                    "phase": task["phase"],
                    "createdAt": task["created_at"],
                    "updatedAt": task["updated_at"],
                    "elapsedSeconds": task["elapsed_seconds"],
                    "error": task["error"],
                }
                for task in self.database.list_tasks()
            ],
            "bridgeVersion": "0.2.6",
            "protocolVersion": 1,
            "availableModels": self.available_models,
        }

    def _effective_filesystem_roots(self) -> list[Any]:
        return self.database.list_filesystem_roots() + [
            project.root for project in self.database.list_projects()
        ]

    async def _git_output(self, root: Any, *arguments: str) -> str:
        creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        try:
            process = await asyncio.create_subprocess_exec(
                "git", *arguments, cwd=str(root),
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
                creationflags=creationflags,
            )
            stdout, _ = await asyncio.wait_for(process.communicate(), timeout=10)
            return stdout.decode("utf-8", errors="replace")[:200_000]
        except (OSError, asyncio.TimeoutError):
            return ""

    def _task_state_from_error(
        self,
        code: str | None,
        message: str | None,
    ) -> tuple[str, str, str | None]:
        normalized = (code or "").upper()
        detail = message or "Erro remoto"
        if normalized in {"USER_CANCELLED", "CANCELLED"}:
            return "CANCELLED", "cancelled", None
        if normalized == "TURN_TIMEOUT":
            return "ERROR", "timeout", detail
        if normalized == "SIGNAL_TIMEOUT":
            return "ERROR", "signal_timeout", detail
        if normalized == "EXECUTION_INTERRUPTED":
            return "ERROR", "interrupted", detail
        return "ERROR", "error", detail

    def _cancelled_payload_for_reason(
        self,
        reason: str | None,
    ) -> tuple[str, str, str | None, dict[str, Any]]:
        normalized = (reason or "").lower()
        if normalized == "user_cancelled":
            return "CANCELLED", "cancelled", None, {
                "code": "USER_CANCELLED",
                "message": "Tarefa cancelada por você",
            }
        if normalized == "signal_timeout":
            message = "A tarefa perdeu contato com o processo do Antigravity"
            return "ERROR", "signal_timeout", message, {
                "code": "SIGNAL_TIMEOUT",
                "message": message,
            }
        message = "A execução foi interrompida antes de concluir."
        return "ERROR", "interrupted", message, {
            "code": "EXECUTION_INTERRUPTED",
            "message": message,
        }

    def _error_payload_for_exception(self, exc: Exception) -> dict[str, Any]:
        message = str(exc).strip()
        code = type(exc).__name__
        if isinstance(exc, TimeoutError) or "timed out" in message.lower() or "timeout" in message.lower():
            return {
                "code": "TURN_TIMEOUT",
                "message": "A execução demorou mais do que o permitido e foi interrompida.",
            }
        return {
            "code": code,
            "message": message or "Erro remoto",
        }

    async def _run_turn(self, envelope: Envelope, payload: CommandPayload) -> None:
        if not payload.project_id or payload.prompt is None:
            raise ValueError("SEND_PROMPT requires projectId and prompt")
        project = self.database.get_project(payload.project_id)
        task_id = payload.task_id or str(envelope.message_id)
        self.database.update_task(task_id, "STARTING", "starting")
        runtime_id = self.database.get_runtime_conversation_id(envelope.conversation_id)
        session = self.sessions.get(envelope.conversation_id, project.root, runtime_id)
        started_at = time.monotonic()

        async def report_progress() -> None:
            while True:
                elapsed = int(time.monotonic() - started_at)
                self.database.update_task(task_id, "RUNNING", "working", elapsed)
                try:
                    await self.emit(
                        envelope.conversation_id,
                        MessageType.HEARTBEAT,
                    {"scope": "turn", "state": "running", "elapsedSeconds": elapsed, "taskId": task_id},
                    )
                except asyncio.CancelledError:
                    raise
                except Exception:
                    # A temporary Firebase failure must not stop future pulses.
                    pass
                # The phone renders elapsed time locally; an eight-second durable pulse
                # avoids a large Firebase replay backlog after Android backgrounding.
                await asyncio.sleep(8)

        await self.emit(
            envelope.conversation_id,
            MessageType.HEARTBEAT,
            {"scope": "turn", "state": "starting", "elapsedSeconds": 0, "taskId": task_id},
        )
        progress_task = asyncio.create_task(report_progress())
        try:
            async for kind, data in session.stream(
                payload.prompt,
                model=payload.model,
                execution_mode=payload.execution_mode or "autonomous_project",
            ):
                data["taskId"] = task_id
                event_type = {
                    "text": MessageType.TEXT_DELTA,
                    "thought": MessageType.THOUGHT_DELTA,
                    "tool": MessageType.TOOL_CALL,
                    "complete": MessageType.TURN_COMPLETE,
                    "error": MessageType.ERROR,
                }[kind]
                if kind == "text":
                    elapsed = int(time.monotonic() - started_at)
                    self.database.update_task(task_id, "RUNNING", "responding", elapsed)
                elif kind == "complete":
                    elapsed = int(time.monotonic() - started_at)
                    self.database.update_task(task_id, "COMPLETE", "complete", elapsed)
                elif kind == "error":
                    elapsed = int(time.monotonic() - started_at)
                    status, phase, error = self._task_state_from_error(
                        str(data.get("code") or ""),
                        data.get("message"),
                    )
                    self.database.update_task(
                        task_id, status, phase, elapsed, error
                    )
                await self.emit(envelope.conversation_id, event_type, data)
        finally:
            progress_task.cancel()
            await asyncio.gather(progress_task, return_exceptions=True)
        if session.cli_conversation_id and session.cli_conversation_id != runtime_id:
            self.database.save_runtime_conversation_id(
                envelope.conversation_id, project.id, session.cli_conversation_id
            )

    async def _run_turn_safely(self, envelope: Envelope, payload: CommandPayload) -> None:
        """Run a detached agent turn without losing background exceptions."""
        try:
            await self._run_turn(envelope, payload)
        except asyncio.CancelledError:
            envelope_task_id = getattr(envelope, "message_id", None)
            task_id = payload.task_id or (str(envelope_task_id) if envelope_task_id else None)
            reason = self.sessions.consume_cancel_reason(envelope.conversation_id)
            status, phase, error, cancelled_payload = self._cancelled_payload_for_reason(reason)
            if task_id and hasattr(self, "database"):
                existing = self.database.get_task(task_id)
                self.database.update_task(
                    task_id,
                    status,
                    phase,
                    existing["elapsed_seconds"] if existing else 0,
                    error,
                )
            if task_id:
                cancelled_payload["taskId"] = task_id
            try:
                await self.emit(
                    envelope.conversation_id,
                    MessageType.ERROR,
                    cancelled_payload,
                )
            except Exception:
                # O snapshot posterior ainda recupera o estado CANCELLED salvo.
                pass
        except Exception as exc:
            envelope_task_id = getattr(envelope, "message_id", None)
            task_id = payload.task_id or (str(envelope_task_id) if envelope_task_id else None)
            error_payload = self._error_payload_for_exception(exc)
            if task_id and hasattr(self, "database"):
                existing = self.database.get_task(task_id)
                status, phase, error = self._task_state_from_error(
                    str(error_payload.get("code") or ""),
                    str(error_payload.get("message") or ""),
                )
                self.database.update_task(
                    task_id,
                    status,
                    phase,
                    existing["elapsed_seconds"] if existing else 0,
                    error,
                )
            if task_id:
                error_payload["taskId"] = task_id
            await self.emit(
                envelope.conversation_id,
                MessageType.ERROR,
                error_payload,
            )

    async def _cancel_turn(self, conversation_id: str, task_id: str | None) -> None:
        """Cancel an active turn or replay its terminal state to unblock the phone."""
        task = self.database.get_task(task_id) if task_id else None
        status = str(task["status"]) if task else None

        if task_id and status not in {"COMPLETE", "ERROR", "CANCELLED"}:
            self.database.update_task(task_id, "CANCELLING", "cancelling")

        if await self.sessions.cancel(conversation_id, reason="user_cancelled"):
            # _run_turn_safely persists and emits CANCELLED after the task is cancelled.
            return

        if status == "COMPLETE":
            await self.emit(
                conversation_id,
                MessageType.TURN_COMPLETE,
                {"taskId": task_id, "reconciled": True},
            )
            return

        if status == "ERROR":
            await self.emit(
                conversation_id,
                MessageType.ERROR,
                {
                    "code": "TURN_ALREADY_FINISHED",
                    "message": str(task.get("error") or "A tarefa já terminou com erro"),
                    "taskId": task_id,
                },
            )
            return

        if task_id:
            self.database.update_task(
                task_id, "CANCELLED", "cancelled", error="Tarefa não estava mais ativa"
            )
        await self.emit(
            conversation_id,
            MessageType.ERROR,
            {
                "code": "USER_CANCELLED",
                "message": "Tarefa não estava mais ativa",
                **({"taskId": task_id} if task_id else {}),
            },
        )

    async def _run_build(self, envelope: Envelope, payload: CommandPayload) -> None:
        if not payload.project_id or not payload.build_profile_id:
            raise ValueError("RUN_BUILD requires projectId and buildProfileId")
        project = self.database.get_project(payload.project_id)
        profile = next(
            (item for item in project.build_profiles if item.id == payload.build_profile_id), None
        )
        if not profile:
            raise KeyError("unknown build profile")
        async for event in self.builds.run(project, profile):
            kind = MessageType.BUILD_LOG if event.kind in {"started", "log"} else MessageType.BUILD_RESULT
            await self.emit(envelope.conversation_id, kind, {"buildId": event.build_id, **event.data})
            if event.kind == "result":
                for relative in event.data.get("artifacts", []):
                    source = (project.root / relative).resolve(strict=True)
                    if project.root.resolve() not in source.parents:
                        continue
                    remote_name = f"{event.build_id}-{source.name}"
                    content = await asyncio.to_thread(source.read_bytes)
                    encrypted = self.crypto.encrypt_artifact(
                        envelope.conversation_id, remote_name, content
                    )
                    object_name = (
                        f"artifacts/{self.crypto.device_id}/{envelope.conversation_id}/{remote_name}"
                    )
                    await self.transport.upload_artifact(object_name, encrypted)
                    await self.emit(
                        envelope.conversation_id,
                        MessageType.ARTIFACT,
                        {"remoteName": remote_name, "displayName": source.name, "size": len(content)},
                    )

    async def dispatch(self, envelope: Envelope) -> None:
        now = int(time.time() * 1000)
        if envelope.expires_at < now:
            await self.transport.acknowledge(str(envelope.message_id))
            return
        if not self.database.accept_command(
            str(envelope.message_id), envelope.conversation_id, envelope.sequence, now
        ):
            # SYNC has no side effects and is safe to replay. This recovers a
            # phone whose local sequence storage was reset after reinstalling.
            if envelope.type == MessageType.SYNC:
                await self.emit(envelope.conversation_id, MessageType.SNAPSHOT, self._snapshot())
            await self.transport.acknowledge(str(envelope.message_id))
            return
        payload = CommandPayload.model_validate(self.crypto.decrypt(envelope))
        if envelope.type == MessageType.SEND_PROMPT:
            task_id = payload.task_id or str(envelope.message_id)
            self.database.start_task(
                task_id,
                envelope.conversation_id,
                payload.project_id or "",
            )
            task = asyncio.create_task(self._run_turn_safely(envelope, payload))
            self.sessions.set_task(envelope.conversation_id, task)
        elif envelope.type == MessageType.CANCEL_TURN:
            await self._cancel_turn(envelope.conversation_id, payload.task_id)
        elif envelope.type == MessageType.RUN_BUILD:
            asyncio.create_task(self._run_build(envelope, payload))
        elif envelope.type == MessageType.CANCEL_BUILD:
            await self.builds.cancel(str(payload.metadata.get("buildId", "")))
        elif envelope.type == MessageType.APPROVAL_DECISION:
            future = self._approvals.get(payload.approval_id or "")
            if future and not future.done():
                future.set_result(payload.approved is True)
        elif envelope.type == MessageType.LIST_DIRECTORIES:
            effective_roots = self._effective_filesystem_roots()
            # Bootstrap: if no roots registered yet, allow full filesystem so user can navigate to create first project
            bootstrap = not effective_roots and not self.database.allow_full_filesystem()
            listing = browse_directories(
                payload.metadata.get("path"),
                allowed_roots=effective_roots,
                allow_full_filesystem=self.database.allow_full_filesystem() or bootstrap,
            )
            listing["allowFullFilesystem"] = self.database.allow_full_filesystem()
            await self.emit(envelope.conversation_id, MessageType.DIRECTORY_LIST, listing)
        elif envelope.type == MessageType.CREATE_DIRECTORY:
            parent = str(payload.metadata.get("parent") or payload.metadata.get("path") or "")
            name = str(payload.metadata.get("name", "")).strip()
            navigate = bool(payload.metadata.get("navigate", True))
            use_as_project = bool(payload.metadata.get("use_as_project", False) or payload.metadata.get("add_as_project", False))
            effective_roots = self._effective_filesystem_roots()
            # Bootstrap: if no roots registered yet, allow full filesystem so user can create their first project folder
            bootstrap = not effective_roots and not self.database.allow_full_filesystem()
            listing = create_directory(
                parent,
                name,
                allowed_roots=effective_roots,
                allow_full_filesystem=self.database.allow_full_filesystem() or bootstrap,
                navigate=navigate,
            )
            listing["allowFullFilesystem"] = self.database.allow_full_filesystem()
            if use_as_project:
                new_dir = resolve_local_directory(parent) / validate_folder_name(name)
                # When bootstrapping (no roots yet), the new project dir itself becomes the first root
                allowed = self.database.allow_full_filesystem() or bootstrap or any(
                    path_is_within(new_dir, allowed_root.resolve(strict=True))
                    for allowed_root in effective_roots
                    if allowed_root.is_dir()
                )
                if not allowed:
                    raise PermissionError("A pasta está fora das raízes autorizadas no computador")
                if not any(project.root.resolve() == new_dir for project in self.database.list_projects()):
                    project_name = str(payload.metadata.get("projectName", "")).strip() or name
                    project = Project(id=str(payload.metadata.get("id") or uuid4()), name=project_name, root=new_dir)
                    self.database.upsert_project(project)
                    await self.emit(envelope.conversation_id, MessageType.SNAPSHOT, self._snapshot())
            await self.emit(envelope.conversation_id, MessageType.DIRECTORY_LIST, listing)
        elif envelope.type == MessageType.ADD_PROJECT:
            raw_root = str(payload.metadata.get("root", ""))
            root = resolve_local_directory(raw_root)
            effective_roots = self._effective_filesystem_roots()
            # Bootstrap: if no roots registered yet, allow any folder so user can add their first project
            bootstrap = not effective_roots and not self.database.allow_full_filesystem()
            allowed = self.database.allow_full_filesystem() or bootstrap or any(
                path_is_within(root, allowed_root.resolve(strict=True))
                for allowed_root in effective_roots
                if allowed_root.is_dir()
            )
            if not allowed:
                raise PermissionError("A pasta está fora das raízes autorizadas no computador")
            if any(project.root.resolve() == root for project in self.database.list_projects()):
                raise ValueError("Esta pasta já está no portfólio de projetos")
            name = str(payload.metadata.get("name", "")).strip() or root.name
            project = Project(id=str(payload.metadata.get("id") or uuid4()), name=name, root=root)
            self.database.upsert_project(project)
            await self.emit(envelope.conversation_id, MessageType.SNAPSHOT, self._snapshot())
        elif envelope.type == MessageType.LIST_PROJECT_FILES:
            project = self.database.get_project(payload.project_id or "")
            listing = list_project_files(project.root, str(payload.metadata.get("path", "")))
            listing["projectId"] = project.id
            await self.emit(envelope.conversation_id, MessageType.PROJECT_FILE_LIST, listing)
        elif envelope.type == MessageType.CREATE_PROJECT_DIRECTORY:
            project = self.database.get_project(payload.project_id or "")
            rel_path = str(payload.metadata.get("path", ""))
            name = str(payload.metadata.get("name", "")).strip()
            listing = create_project_directory(project.root, rel_path, name)
            listing["projectId"] = project.id
            await self.emit(envelope.conversation_id, MessageType.PROJECT_FILE_LIST, listing)
        elif envelope.type == MessageType.READ_PROJECT_FILE:
            project = self.database.get_project(payload.project_id or "")
            content = read_project_file(project.root, str(payload.metadata.get("path", "")))
            content["projectId"] = project.id
            await self.emit(envelope.conversation_id, MessageType.PROJECT_FILE_CONTENT, content)
        elif envelope.type == MessageType.GET_PROJECT_STATUS:
            project = self.database.get_project(payload.project_id or "")
            status, diff_stat, diff = await asyncio.gather(
                self._git_output(project.root, "status", "--short", "--branch"),
                self._git_output(project.root, "diff", "--stat"),
                self._git_output(project.root, "diff", "--no-color"),
            )
            await self.emit(
                envelope.conversation_id,
                MessageType.PROJECT_STATUS,
                {"projectId": project.id, "gitStatus": status, "diffStat": diff_stat, "diff": diff},
            )
        elif envelope.type == MessageType.SYNC:
            await self.emit(envelope.conversation_id, MessageType.SNAPSHOT, self._snapshot())
        await self.transport.acknowledge(str(envelope.message_id))

    async def run(self) -> None:
        heartbeat_due = 0.0
        models_due = 0.0
        while not self._stop.is_set():
            try:
                if not self.paused:
                    if time.monotonic() >= models_due:
                        try:
                            self.available_models = await asyncio.to_thread(list_available_models)
                        except Exception:
                            pass
                        models_due = time.monotonic() + 10 * 60
                    for task in self.database.expire_stale_tasks():
                        cancelled = await self.sessions.cancel(
                            task["conversation_id"],
                            reason="signal_timeout",
                        )
                        if cancelled:
                            continue
                        self.database.update_task(
                            task["id"],
                            "ERROR",
                            "signal_timeout",
                            task["elapsed_seconds"],
                            "A tarefa perdeu contato com o processo do Antigravity",
                        )
                        await self.emit(
                            task["conversation_id"],
                            MessageType.ERROR,
                            {
                                "code": "SIGNAL_TIMEOUT",
                                "message": "A tarefa perdeu contato com o processo do Antigravity",
                                "taskId": task["id"],
                                "elapsedSeconds": task["elapsed_seconds"],
                            },
                        )
                    if time.monotonic() >= heartbeat_due:
                        await self.transport.heartbeat()
                        heartbeat_due = time.monotonic() + 25
                    for envelope in await self.transport.poll_commands():
                        try:
                            await self.dispatch(envelope)
                        except Exception as exc:
                            await self.emit(
                                envelope.conversation_id,
                                MessageType.ERROR,
                                {"code": type(exc).__name__, "message": str(exc)},
                            )
                            await self.transport.acknowledge(str(envelope.message_id))
                await asyncio.sleep(1.5)
            except asyncio.CancelledError:
                break
            except Exception:
                await asyncio.sleep(5)

    async def close(self) -> None:
        self._stop.set()
        for future in self._approvals.values():
            if not future.done():
                future.set_result(False)
        await self.sessions.close()
        await self.transport.close()
