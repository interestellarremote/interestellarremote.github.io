from pathlib import Path
import time

from agy_remote.database import Database
from agy_remote.models import Project


def test_sequences_are_monotonic_and_commands_idempotent(tmp_path: Path):
    db = Database(tmp_path / "bridge.db")
    project_root = tmp_path / "repo"
    project_root.mkdir()
    db.upsert_project(Project(id="p1", name="Repo", root=project_root))
    assert db.next_sequence("out", "c1") == 1
    assert db.next_sequence("out", "c1") == 2
    assert db.accept_command("cmd1", "c1", 1, 10)
    assert not db.accept_command("cmd1", "c1", 1, 11)
    assert not db.accept_command("cmd2", "c1", 1, 12)

    assert db.get_runtime_conversation_id("c1") is None
    db.save_runtime_conversation_id("c1", "p1", "agy-conversation-1")
    assert db.get_runtime_conversation_id("c1") == "agy-conversation-1"

    assert db.allow_full_filesystem() is False
    db.set_allow_full_filesystem(True)
    assert db.allow_full_filesystem() is True
    assert db.add_filesystem_root(project_root) == project_root.resolve()
    assert db.list_filesystem_roots() == [project_root.resolve()]
    db.remove_filesystem_root(project_root)
    assert db.list_filesystem_roots() == []

    db.start_task("t1", "c1", "p1")
    db.update_task("t1", "RUNNING", "working", 4)
    assert db.get_task("missing") is None
    assert db.get_task("t1")["status"] == "RUNNING"
    assert db.list_tasks()[0]["status"] == "RUNNING"


def test_expire_stale_tasks_closes_orphaned_turn(tmp_path: Path):
    db = Database(tmp_path / "bridge.db")
    db.start_task("stale", "conversation", "project")
    with db._connection:
        db._connection.execute(
            "UPDATE tasks SET status='RUNNING', updated_at=? WHERE id='stale'",
            (int(time.time() * 1000) - 120_000,),
        )

    expired = db.expire_stale_tasks(stale_after_seconds=90)

    assert [task["id"] for task in expired] == ["stale"]
    task = db.get_task("stale")
    assert task["status"] == "ERROR"
    assert task["phase"] == "signal_timeout"
    assert db.expire_stale_tasks(stale_after_seconds=90) == []
