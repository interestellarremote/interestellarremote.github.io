from __future__ import annotations

import json
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any

from .models import Project


class Database:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        self._migrate()

    def _migrate(self) -> None:
        with self._connection:
            self._connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS projects (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    root TEXT NOT NULL UNIQUE,
                    build_profiles TEXT NOT NULL DEFAULT '[]'
                );
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    project_id TEXT NOT NULL REFERENCES projects(id),
                    sdk_conversation_id TEXT,
                    archived INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS message_sequences (
                    direction TEXT NOT NULL,
                    conversation_id TEXT NOT NULL,
                    last_sequence INTEGER NOT NULL,
                    PRIMARY KEY(direction, conversation_id)
                );
                CREATE TABLE IF NOT EXISTS processed_commands (
                    command_id TEXT PRIMARY KEY,
                    processed_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS filesystem_roots (
                    path TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS bridge_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    project_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    phase TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    elapsed_seconds INTEGER NOT NULL DEFAULT 0,
                    error TEXT
                );
                """
            )
            self._connection.execute(
                """UPDATE tasks SET status='ERROR', phase='interrupted',
                error='A ponte foi reiniciada durante a execução', updated_at=?
                WHERE status IN ('QUEUED','STARTING','RUNNING','CANCELLING')""",
                (int(time.time() * 1000),),
            )

    def start_task(self, task_id: str, conversation_id: str, project_id: str) -> None:
        now = int(time.time() * 1000)
        with self._lock, self._connection:
            self._connection.execute(
                """INSERT INTO tasks(id,conversation_id,project_id,status,phase,created_at,updated_at)
                VALUES(?,?,?,'QUEUED','received',?,?)
                ON CONFLICT(id) DO NOTHING""",
                (task_id, conversation_id, project_id, now, now),
            )

    def update_task(
        self,
        task_id: str,
        status: str,
        phase: str,
        elapsed_seconds: int = 0,
        error: str | None = None,
    ) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                """UPDATE tasks SET status=?,phase=?,elapsed_seconds=?,error=?,updated_at=?
                WHERE id=?""",
                (status, phase, elapsed_seconds, error, int(time.time() * 1000), task_id),
            )

    def list_tasks(self, limit: int = 50) -> list[dict[str, Any]]:
        with self._lock:
            rows = self._connection.execute(
                "SELECT * FROM tasks ORDER BY updated_at DESC LIMIT ?", (limit,)
            ).fetchall()
        return [dict(row) for row in rows]

    def expire_stale_tasks(self, stale_after_seconds: int = 90) -> list[dict[str, Any]]:
        """Close active tasks whose three-second progress pulse stopped unexpectedly."""
        cutoff = int(time.time() * 1000) - stale_after_seconds * 1000
        now = int(time.time() * 1000)
        with self._lock, self._connection:
            rows = self._connection.execute(
                """SELECT * FROM tasks
                WHERE status IN ('QUEUED','STARTING','RUNNING','CANCELLING')
                AND updated_at < ?""",
                (cutoff,),
            ).fetchall()
            if rows:
                self._connection.executemany(
                    """UPDATE tasks SET status='ERROR', phase='signal_timeout', error=?, updated_at=?
                    WHERE id=?""",
                    [
                        ("A tarefa perdeu contato com o processo do Antigravity", now, row["id"])
                        for row in rows
                    ],
                )
        return [dict(row) for row in rows]

    def get_task(self, task_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT * FROM tasks WHERE id=?", (task_id,)
            ).fetchone()
        return dict(row) if row else None

    def upsert_project(self, project: Project) -> None:
        root = project.root.resolve(strict=True)
        if not root.is_dir():
            raise ValueError("project root must be a directory")
        profiles = json.dumps([p.model_dump(by_alias=True) for p in project.build_profiles])
        with self._lock, self._connection:
            self._connection.execute(
                """INSERT INTO projects(id,name,root,build_profiles) VALUES(?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,root=excluded.root,
                build_profiles=excluded.build_profiles""",
                (project.id, project.name, str(root), profiles),
            )

    def list_projects(self) -> list[Project]:
        with self._lock:
            rows = self._connection.execute("SELECT * FROM projects ORDER BY name").fetchall()
        return [
            Project(
                id=row["id"],
                name=row["name"],
                root=Path(row["root"]),
                buildProfiles=json.loads(row["build_profiles"]),
            )
            for row in rows
        ]

    def get_project(self, project_id: str) -> Project:
        return next((p for p in self.list_projects() if p.id == project_id), None) or self._missing(
            project_id
        )

    def list_filesystem_roots(self) -> list[Path]:
        with self._lock:
            rows = self._connection.execute(
                "SELECT path FROM filesystem_roots ORDER BY path"
            ).fetchall()
        return [Path(row[0]) for row in rows]

    def add_filesystem_root(self, root: Path) -> Path:
        resolved = root.resolve(strict=True)
        if not resolved.is_dir():
            raise ValueError("filesystem root must be a directory")
        with self._lock, self._connection:
            self._connection.execute(
                "INSERT OR IGNORE INTO filesystem_roots(path,created_at) VALUES(?,?)",
                (str(resolved), int(time.time() * 1000)),
            )
        return resolved

    def remove_filesystem_root(self, root: Path) -> None:
        resolved = root.resolve(strict=False)
        with self._lock, self._connection:
            self._connection.execute("DELETE FROM filesystem_roots WHERE path=?", (str(resolved),))

    def allow_full_filesystem(self) -> bool:
        with self._lock:
            row = self._connection.execute(
                "SELECT value FROM bridge_settings WHERE key='allow_full_filesystem'"
            ).fetchone()
        return bool(row and row[0] == "1")

    def set_allow_full_filesystem(self, enabled: bool) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                """INSERT INTO bridge_settings(key,value) VALUES('allow_full_filesystem',?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value""",
                ("1" if enabled else "0",),
            )

    def get_runtime_conversation_id(self, conversation_id: str) -> str | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT sdk_conversation_id FROM conversations WHERE id=?",
                (conversation_id,),
            ).fetchone()
        return str(row[0]) if row and row[0] else None

    def save_runtime_conversation_id(
        self, conversation_id: str, project_id: str, runtime_conversation_id: str
    ) -> None:
        with self._lock, self._connection:
            self._connection.execute(
                """INSERT INTO conversations(id,project_id,sdk_conversation_id,updated_at)
                VALUES(?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    project_id=excluded.project_id,
                    sdk_conversation_id=excluded.sdk_conversation_id,
                    updated_at=excluded.updated_at""",
                (conversation_id, project_id, runtime_conversation_id, int(time.time() * 1000)),
            )

    @staticmethod
    def _missing(project_id: str) -> Any:
        raise KeyError(f"unknown project: {project_id}")

    def next_sequence(self, direction: str, conversation_id: str) -> int:
        with self._lock, self._connection:
            row = self._connection.execute(
                "SELECT last_sequence FROM message_sequences WHERE direction=? AND conversation_id=?",
                (direction, conversation_id),
            ).fetchone()
            value = (row[0] if row else 0) + 1
            self._connection.execute(
                """INSERT INTO message_sequences(direction,conversation_id,last_sequence) VALUES(?,?,?)
                ON CONFLICT(direction,conversation_id) DO UPDATE SET last_sequence=excluded.last_sequence""",
                (direction, conversation_id, value),
            )
            return value

    def accept_command(self, command_id: str, conversation_id: str, sequence: int, now_ms: int) -> bool:
        with self._lock, self._connection:
            if self._connection.execute(
                "SELECT 1 FROM processed_commands WHERE command_id=?", (command_id,)
            ).fetchone():
                return False
            row = self._connection.execute(
                "SELECT last_sequence FROM message_sequences WHERE direction='in' AND conversation_id=?",
                (conversation_id,),
            ).fetchone()
            if row and sequence <= row[0]:
                return False
            self._connection.execute(
                "INSERT INTO processed_commands(command_id,processed_at) VALUES(?,?)",
                (command_id, now_ms),
            )
            self._connection.execute(
                """INSERT INTO message_sequences(direction,conversation_id,last_sequence) VALUES('in',?,?)
                ON CONFLICT(direction,conversation_id) DO UPDATE SET last_sequence=excluded.last_sequence""",
                (conversation_id, sequence),
            )
            return True
