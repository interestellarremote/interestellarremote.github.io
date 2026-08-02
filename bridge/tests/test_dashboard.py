from pathlib import Path

from fastapi.testclient import TestClient

from agy_remote.config import Settings
from agy_remote.dashboard import DashboardState, browse_directories, create_dashboard
from agy_remote.database import Database


class PendingPairing:
    def done(self) -> bool:
        return False


def test_start_pairing_returns_existing_qr_while_pending(tmp_path: Path) -> None:
    settings = Settings(
        firebase_database_url="https://example.invalid",
        firebase_api_key="public-key",
        functions_base_url="https://example.invalid/functions",
        device_id="d" * 32,
        data_dir=tmp_path,
    )
    state = DashboardState(
        settings=settings,
        database=Database(tmp_path / "bridge.db"),
        secrets=object(),  # Not accessed when an existing QR can be reused.
        transport=object(),
        pairing_qr="existing-token",
        pairing_task=PendingPairing(),
    )

    response = TestClient(create_dashboard(state)).post("/api/pair/start")

    assert response.status_code == 200
    assert response.json() == {"qrToken": "existing-token"}


def test_directory_browser_lists_only_subdirectories(tmp_path: Path) -> None:
    (tmp_path / "project-a").mkdir()
    (tmp_path / "project-b").mkdir()
    (tmp_path / "notes.txt").write_text("not a directory", encoding="utf-8")

    listing = browse_directories(str(tmp_path), unrestricted_local_admin=True)

    assert listing["current"] == str(tmp_path.resolve())
    assert [entry["name"] for entry in listing["entries"]] == ["project-a", "project-b"]
    assert listing["parent"] == str(tmp_path.parent.resolve())


def test_directory_browser_api_rejects_relative_paths(tmp_path: Path) -> None:
    settings = Settings(
        firebase_database_url="https://example.invalid",
        firebase_api_key="public-key",
        functions_base_url="https://example.invalid/functions",
        device_id="d" * 32,
        data_dir=tmp_path,
    )
    state = DashboardState(settings, Database(tmp_path / "bridge.db"), object(), object())

    response = TestClient(create_dashboard(state)).get(
        "/api/filesystem/directories", params={"path": "relative/path"}
    )

    assert response.status_code == 400
    assert "absoluto" in response.json()["detail"]


def test_project_name_is_normalized_when_browser_sends_full_path(tmp_path: Path) -> None:
    project_root = tmp_path / "my-project"
    project_root.mkdir()
    settings = Settings(
        firebase_database_url="https://example.invalid",
        firebase_api_key="public-key",
        functions_base_url="https://example.invalid/functions",
        device_id="d" * 32,
        data_dir=tmp_path,
    )
    database = Database(tmp_path / "bridge.db")
    state = DashboardState(settings, database, object(), object())

    response = TestClient(create_dashboard(state)).post(
        "/api/projects",
        json={
            "id": "project-1",
            "name": str(project_root),
            "root": str(project_root),
            "buildProfiles": [],
        },
    )

    assert response.status_code == 200
    assert database.get_project("project-1").name == "my-project"
