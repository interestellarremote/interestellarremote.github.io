from pathlib import Path

import pytest

from agy_remote.builds import BuildManager, confined_path
from agy_remote.models import BuildProfile, Project


def test_confined_path_rejects_escape(tmp_path: Path):
    root = tmp_path / "root"
    root.mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()
    with pytest.raises((PermissionError, FileNotFoundError)):
        confined_path(root, "../outside")


@pytest.mark.asyncio
async def test_build_streams_output(tmp_path: Path):
    project = Project(id="p", name="P", root=tmp_path)
    profile = BuildProfile(
        id="test", name="Test", executable="python", arguments=["-c", "print('ok')"]
    )
    events = [event async for event in BuildManager().run(project, profile)]
    assert any(event.kind == "log" and "ok" in event.data["text"] for event in events)
    assert events[-1].data["status"] == "success"

