from __future__ import annotations

import os
import string
from pathlib import Path
from typing import Any


def resolve_local_directory(raw_path: str) -> Path:
    if not raw_path or not raw_path.strip():
        raise ValueError("Informe um caminho válido.")
    cleaned = raw_path.strip()
    if "\x00" in cleaned or (os.name == "nt" and cleaned.startswith(("\\\\", "//"))):
        raise ValueError("Somente pastas locais são permitidas.")
    if os.name == "nt" and len(cleaned) == 2 and cleaned[1] == ":":
        cleaned += "\\"
    candidate = Path(cleaned).expanduser()
    if not candidate.is_absolute():
        raise ValueError("Informe um caminho absoluto.")
    try:
        resolved = candidate.resolve(strict=True)
    except OSError as exc:
        raise ValueError("A pasta não existe ou não está acessível.") from exc
    if not resolved.is_dir():
        raise ValueError("O caminho selecionado não é uma pasta.")
    return resolved


def path_is_within(candidate: Path, root: Path) -> bool:
    try:
        candidate.relative_to(root)
        return True
    except ValueError:
        return False


def resolve_project_path(root: Path, relative_path: str, *, directory: bool | None = None) -> Path:
    if "\x00" in relative_path or Path(relative_path).is_absolute():
        raise ValueError("Caminho inválido dentro do projeto.")
    project_root = root.resolve(strict=True)
    candidate = (project_root / relative_path).resolve(strict=True)
    if not path_is_within(candidate, project_root):
        raise PermissionError("O caminho está fora do projeto autorizado.")
    if directory is True and not candidate.is_dir():
        raise ValueError("A pasta não existe.")
    if directory is False and not candidate.is_file():
        raise ValueError("O arquivo não existe.")
    return candidate


def list_project_files(root: Path, relative_path: str = "") -> dict[str, Any]:
    current = resolve_project_path(root, relative_path, directory=True)
    project_root = root.resolve(strict=True)
    entries: list[dict[str, Any]] = []
    with os.scandir(current) as iterator:
        for item in iterator:
            if item.name in {".git", ".gradle", "node_modules", "build", "dist"}:
                continue
            try:
                is_dir = item.is_dir()
                entries.append({
                    "name": item.name,
                    "path": str(Path(item.path).resolve().relative_to(project_root)).replace("\\", "/"),
                    "kind": "folder" if is_dir else "file",
                    "size": 0 if is_dir else item.stat().st_size,
                })
            except OSError:
                continue
    entries.sort(key=lambda item: (item["kind"] != "folder", item["name"].casefold()))
    parent = None if current == project_root else str(current.parent.relative_to(project_root)).replace("\\", "/")
    return {
        "current": str(current.relative_to(project_root)).replace("\\", "/"),
        "parent": parent,
        "entries": entries[:500],
        "truncated": len(entries) > 500,
    }


def read_project_file(root: Path, relative_path: str, max_bytes: int = 1_000_000) -> dict[str, Any]:
    source = resolve_project_path(root, relative_path, directory=False)
    size = source.stat().st_size
    if size > max_bytes:
        raise ValueError("O arquivo é grande demais para visualização remota.")
    content = source.read_text(encoding="utf-8", errors="replace")
    return {"path": relative_path.replace("\\", "/"), "content": content, "size": size}


def system_roots() -> list[dict[str, str]]:
    roots: list[dict[str, str]] = []
    home = Path.home()
    if home.is_dir():
        roots.append({"name": f"Pasta do usuário ({home})", "path": str(home), "kind": "folder"})
    if os.name == "nt":
        for letter in string.ascii_uppercase:
            drive = Path(f"{letter}:\\")
            if drive.is_dir():
                roots.append({"name": f"Unidade {letter}:", "path": str(drive), "kind": "drive"})
    else:
        roots.append({"name": "Sistema de arquivos", "path": "/", "kind": "drive"})
    return roots


def browse_directories(
    raw_path: str | None,
    *,
    allowed_roots: list[Path] | None = None,
    allow_full_filesystem: bool = False,
    unrestricted_local_admin: bool = False,
) -> dict[str, Any]:
    resolved_roots = sorted(
        {root.resolve(strict=True) for root in allowed_roots or [] if root.is_dir()},
        key=lambda value: str(value).casefold(),
    )
    unrestricted = unrestricted_local_admin or allow_full_filesystem
    if not raw_path:
        if unrestricted:
            entries = system_roots()
        else:
            entries = [
                {"name": root.name or str(root), "path": str(root), "kind": "folder"}
                for root in resolved_roots
            ]
        return {"current": None, "parent": None, "entries": entries, "truncated": False}

    current = resolve_local_directory(raw_path)
    if not unrestricted and not any(path_is_within(current, root) for root in resolved_roots):
        raise ValueError("Esta pasta está fora das raízes autorizadas.")

    entries: list[dict[str, str]] = []
    try:
        with os.scandir(current) as iterator:
            for item in iterator:
                try:
                    if item.is_dir():
                        entries.append({"name": item.name, "path": item.path, "kind": "folder"})
                except OSError:
                    continue
    except PermissionError as exc:
        raise ValueError("Sem permissão para abrir esta pasta.") from exc
    entries.sort(key=lambda item: item["name"].casefold())
    truncated = len(entries) > 1000
    entries = entries[:1000]

    parent_path = current.parent
    if parent_path == current:
        parent = None
    elif unrestricted or any(path_is_within(parent_path, root) for root in resolved_roots):
        parent = str(parent_path)
    else:
        parent = None
    return {"current": str(current), "parent": parent, "entries": entries, "truncated": truncated}


def validate_folder_name(name: str) -> str:
    cleaned = name.strip()
    if not cleaned:
        raise ValueError("O nome da pasta não pode ser vazio.")
    if "\x00" in cleaned or "/" in cleaned or "\\" in cleaned or ".." in cleaned:
        raise ValueError("Nome de pasta inválido.")
    if os.name == "nt":
        invalid_chars = set('<>:"|?*')
        if any(char in invalid_chars for char in cleaned):
            raise ValueError("O nome da pasta contém caracteres inválidos.")
    return cleaned


def create_directory(
    raw_parent_path: str,
    folder_name: str,
    *,
    allowed_roots: list[Path] | None = None,
    allow_full_filesystem: bool = False,
    unrestricted_local_admin: bool = False,
    navigate: bool = False,
) -> dict[str, Any]:
    name = validate_folder_name(folder_name)
    parent = resolve_local_directory(raw_parent_path)

    resolved_roots = sorted(
        {root.resolve(strict=True) for root in allowed_roots or [] if root.is_dir()},
        key=lambda value: str(value).casefold(),
    )
    unrestricted = unrestricted_local_admin or allow_full_filesystem
    if not unrestricted and not any(path_is_within(parent, root) for root in resolved_roots):
        raise PermissionError("Esta pasta está fora das raízes autorizadas.")

    new_dir = parent / name
    if new_dir.exists():
        raise ValueError("Já existe uma pasta ou arquivo com este nome.")

    try:
        new_dir.mkdir(parents=True, exist_ok=False)
    except OSError as exc:
        raise ValueError(f"Não foi possível criar a pasta: {exc}") from exc

    target = new_dir if navigate else parent
    return browse_directories(
        str(target),
        allowed_roots=allowed_roots,
        allow_full_filesystem=allow_full_filesystem,
        unrestricted_local_admin=unrestricted_local_admin,
    )


def create_project_directory(root: Path, relative_path: str, folder_name: str) -> dict[str, Any]:
    name = validate_folder_name(folder_name)
    parent = resolve_project_path(root, relative_path, directory=True)

    new_dir = parent / name
    project_root = root.resolve(strict=True)
    if not path_is_within(new_dir.resolve(strict=False), project_root):
        raise PermissionError("O caminho está fora do projeto autorizado.")

    if new_dir.exists():
        raise ValueError("Já existe uma pasta ou arquivo com este nome.")

    try:
        new_dir.mkdir(parents=False, exist_ok=False)
    except OSError as exc:
        raise ValueError(f"Não foi possível criar a pasta: {exc}") from exc

    return list_project_files(root, relative_path)

