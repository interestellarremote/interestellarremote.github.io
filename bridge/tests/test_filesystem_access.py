from pathlib import Path

import pytest

from agy_remote.filesystem_access import (
    browse_directories,
    create_directory,
    create_project_directory,
    list_project_files,
    read_project_file,
)


def test_protected_browser_cannot_escape_authorized_root(tmp_path: Path) -> None:
    allowed = tmp_path / "allowed"
    allowed.mkdir()
    (allowed / "project").mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()

    listing = browse_directories(str(allowed), allowed_roots=[allowed])

    assert listing["parent"] is None
    assert [entry["name"] for entry in listing["entries"]] == ["project"]
    with pytest.raises(ValueError, match="fora das raízes"):
        browse_directories(str(outside), allowed_roots=[allowed])


def test_full_filesystem_mode_can_open_path_outside_roots(tmp_path: Path) -> None:
    outside = tmp_path / "outside"
    outside.mkdir()

    listing = browse_directories(
        str(outside), allowed_roots=[], allow_full_filesystem=True
    )

    assert listing["current"] == str(outside.resolve())


def test_project_file_browser_reads_text_and_cannot_escape(tmp_path: Path) -> None:
    project = tmp_path / "project"
    project.mkdir()
    (project / "src").mkdir()
    (project / "src" / "main.kt").write_text("fun main() = Unit", encoding="utf-8")
    outside = tmp_path / "secret.txt"
    outside.write_text("secret", encoding="utf-8")

    listing = list_project_files(project, "src")
    assert listing["entries"][0]["path"] == "src/main.kt"
    assert read_project_file(project, "src/main.kt")["content"] == "fun main() = Unit"
    with pytest.raises(PermissionError):
        read_project_file(project, "../secret.txt")


def test_create_directory_creates_folder_in_allowed_root(tmp_path: Path) -> None:
    allowed = tmp_path / "allowed"
    allowed.mkdir()

    listing = create_directory(str(allowed), "nova_pasta", allowed_roots=[allowed])
    assert (allowed / "nova_pasta").is_dir()
    assert any(entry["name"] == "nova_pasta" for entry in listing["entries"])

    with pytest.raises(ValueError, match="Já existe"):
        create_directory(str(allowed), "nova_pasta", allowed_roots=[allowed])


def test_create_directory_with_navigate_returns_new_folder(tmp_path: Path) -> None:
    allowed = tmp_path / "allowed"
    allowed.mkdir()

    listing = create_directory(str(allowed), "minha_subpasta", allowed_roots=[allowed], navigate=True)
    assert (allowed / "minha_subpasta").is_dir()
    assert listing["current"] == str((allowed / "minha_subpasta").resolve())
    assert listing["parent"] == str(allowed.resolve())


def test_create_project_directory_creates_folder_in_project(tmp_path: Path) -> None:
    project = tmp_path / "project"
    project.mkdir()
    (project / "src").mkdir()

    listing = create_project_directory(project, "src", "components")
    assert (project / "src" / "components").is_dir()
    assert any(entry["name"] == "components" for entry in listing["entries"])

    with pytest.raises(ValueError, match="Já existe"):
        create_project_directory(project, "src", "components")


def test_create_directory_with_full_filesystem_allows_any_path(tmp_path: Path) -> None:
    """Bootstrap scenario: create first project folder when no authorized roots are registered."""
    parent = tmp_path / "anywhere"
    parent.mkdir()

    listing = create_directory(str(parent), "primeiro_projeto", allowed_roots=[], allow_full_filesystem=True)
    assert (parent / "primeiro_projeto").is_dir()
    assert any(entry["name"] == "primeiro_projeto" for entry in listing["entries"])


def test_browse_directories_with_no_roots_returns_system_roots() -> None:
    """Bootstrap scenario: browsing with no authorized roots and full filesystem shows system roots."""
    listing = browse_directories(None, allowed_roots=[], allow_full_filesystem=True)
    # System roots (drives / home) should be returned
    assert isinstance(listing["entries"], list)
    assert len(listing["entries"]) > 0
