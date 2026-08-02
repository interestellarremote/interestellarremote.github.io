from __future__ import annotations

import asyncio
import os
import signal
from collections.abc import AsyncIterator
from dataclasses import dataclass
from pathlib import Path
from uuid import uuid4

from .models import BuildProfile, Project


@dataclass(slots=True)
class BuildEvent:
    build_id: str
    kind: str
    data: dict


def confined_path(root: Path, relative: str) -> Path:
    resolved_root = root.resolve(strict=True)
    candidate = (resolved_root / relative).resolve(strict=True)
    if candidate != resolved_root and resolved_root not in candidate.parents:
        raise PermissionError("path escapes project root")
    return candidate


class BuildManager:
    def __init__(self) -> None:
        self._processes: dict[str, asyncio.subprocess.Process] = {}

    async def run(self, project: Project, profile: BuildProfile) -> AsyncIterator[BuildEvent]:
        build_id = str(uuid4())
        cwd = confined_path(project.root, profile.working_directory)
        creationflags = 0
        start_new_session = os.name != "nt"
        if os.name == "nt":
            creationflags = 0x00000200  # CREATE_NEW_PROCESS_GROUP
        process = await asyncio.create_subprocess_exec(
            profile.executable,
            *profile.arguments,
            cwd=cwd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            creationflags=creationflags,
            start_new_session=start_new_session,
        )
        self._processes[build_id] = process
        yield BuildEvent(build_id, "started", {"profileId": profile.id, "pid": process.pid})

        queue: asyncio.Queue[tuple[str, bytes | None]] = asyncio.Queue()

        async def pump(channel: str, stream: asyncio.StreamReader | None) -> None:
            if stream is None:
                await queue.put((channel, None))
                return
            while line := await stream.readline():
                await queue.put((channel, line))
            await queue.put((channel, None))

        tasks = [
            asyncio.create_task(pump("stdout", process.stdout)),
            asyncio.create_task(pump("stderr", process.stderr)),
        ]
        completed_streams = 0
        try:
            async with asyncio.timeout(profile.timeout_seconds):
                while completed_streams < 2:
                    channel, line = await queue.get()
                    if line is None:
                        completed_streams += 1
                    else:
                        yield BuildEvent(
                            build_id,
                            "log",
                            {"channel": channel, "text": line.decode(errors="replace")},
                        )
                exit_code = await process.wait()
        except TimeoutError:
            await self.cancel(build_id)
            yield BuildEvent(build_id, "result", {"status": "timeout", "exitCode": None})
            return
        finally:
            await asyncio.gather(*tasks, return_exceptions=True)
            self._processes.pop(build_id, None)

        artifacts: list[str] = []
        for pattern in profile.artifact_globs:
            for match in cwd.glob(pattern):
                if match.is_file():
                    resolved = match.resolve()
                    if resolved == project.root.resolve() or project.root.resolve() in resolved.parents:
                        artifacts.append(str(resolved.relative_to(project.root.resolve())))
        yield BuildEvent(
            build_id,
            "result",
            {"status": "success" if exit_code == 0 else "failed", "exitCode": exit_code, "artifacts": artifacts},
        )

    async def cancel(self, build_id: str) -> bool:
        process = self._processes.get(build_id)
        if not process or process.returncode is not None:
            return False
        if os.name == "nt":
            # taskkill is used only with the numeric PID created by this process.
            killer = await asyncio.create_subprocess_exec(
                "taskkill", "/PID", str(process.pid), "/T", "/F",
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
            await killer.wait()
        else:
            os.killpg(process.pid, signal.SIGTERM)
        await process.wait()
        return True

