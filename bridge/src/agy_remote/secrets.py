from __future__ import annotations

import base64
import os
import sys
from pathlib import Path


class SecretStore:
    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def put(self, name: str, value: bytes) -> None:
        protected = self._protect(value)
        target = self.directory / f"{name}.bin"
        temp = target.with_suffix(".tmp")
        temp.write_bytes(protected)
        os.replace(temp, target)

    def get(self, name: str) -> bytes | None:
        target = self.directory / f"{name}.bin"
        return self._unprotect(target.read_bytes()) if target.exists() else None

    @staticmethod
    def _protect(value: bytes) -> bytes:
        if sys.platform == "win32":
            import win32crypt

            return win32crypt.CryptProtectData(value, "Antigravity Remote", None, None, None, 0)
        # Development-only fallback; production packaging is Windows-only.
        return base64.b64encode(value)

    @staticmethod
    def _unprotect(value: bytes) -> bytes:
        if sys.platform == "win32":
            import win32crypt

            return win32crypt.CryptUnprotectData(value, None, None, None, 0)[1]
        return base64.b64decode(value)

