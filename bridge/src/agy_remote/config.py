from __future__ import annotations

import json
import os
from pathlib import Path
from uuid import uuid4

from pydantic import BaseModel


def default_data_dir() -> Path:
    base = os.getenv("LOCALAPPDATA") or str(Path.home() / ".local" / "share")
    return Path(base) / "AntigravityRemote"


class Settings(BaseModel):
    firebase_database_url: str
    firebase_api_key: str
    functions_base_url: str
    firebase_storage_bucket: str = ""
    device_id: str
    device_name: str = "Meu computador"
    dashboard_port: int = 8765
    data_dir: Path = default_data_dir()

    @classmethod
    def load(cls, path: Path | None = None) -> "Settings":
        config_path = path or default_data_dir() / "config.json"
        if not config_path.exists():
            raise FileNotFoundError(
                f"Missing {config_path}. Copy bridge/config.example.json and fill Firebase values."
            )
        data = json.loads(config_path.read_text(encoding="utf-8"))
        data.setdefault("device_id", uuid4().hex)
        data.setdefault("data_dir", str(config_path.parent))
        settings = cls.model_validate(data)
        config_path.parent.mkdir(parents=True, exist_ok=True)
        config_path.write_text(
            json.dumps(settings.model_dump(mode="json"), indent=2, ensure_ascii=False), encoding="utf-8"
        )
        return settings
