from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

import httpx

from .models import Envelope


@dataclass(slots=True)
class FirebaseConfig:
    database_url: str
    api_key: str
    functions_base_url: str
    storage_bucket: str = ""


class FirebaseTransport:
    def __init__(self, config: FirebaseConfig, device_id: str) -> None:
        self.config = config
        self.device_id = device_id
        self.client = httpx.AsyncClient(timeout=30)
        self.id_token: str | None = None
        self.refresh_token: str | None = None
        self.expires_at = 0.0

    async def close(self) -> None:
        await self.client.aclose()

    async def exchange_custom_token(self, custom_token: str) -> None:
        response = await self.client.post(
            f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key={self.config.api_key}",
            json={"token": custom_token, "returnSecureToken": True},
        )
        response.raise_for_status()
        data = response.json()
        self.id_token = data["idToken"]
        self.refresh_token = data["refreshToken"]
        self.expires_at = time.time() + int(data["expiresIn"]) - 60

    async def _ensure_token(self) -> None:
        if self.id_token and time.time() < self.expires_at:
            return
        if not self.refresh_token:
            raise RuntimeError("bridge is not paired")
        response = await self.client.post(
            f"https://securetoken.googleapis.com/v1/token?key={self.config.api_key}",
            data={"grant_type": "refresh_token", "refresh_token": self.refresh_token},
        )
        response.raise_for_status()
        data = response.json()
        self.id_token = data["id_token"]
        self.refresh_token = data["refresh_token"]
        self.expires_at = time.time() + int(data["expires_in"]) - 60

    async def _request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        await self._ensure_token()
        url = f"{self.config.database_url.rstrip('/')}/{path}.json"
        response = await self.client.request(method, url, params={"auth": self.id_token}, **kwargs)
        response.raise_for_status()
        return response

    async def poll_commands(self) -> list[Envelope]:
        response = await self._request("GET", f"mailboxes/{self.device_id}/commands")
        raw = response.json() or {}
        envelopes: list[Envelope] = []
        for command_id, value in raw.items():
            value.setdefault("messageId", command_id)
            try:
                envelopes.append(Envelope.model_validate(value))
            except Exception:
                continue
        return sorted(envelopes, key=lambda item: item.sequence)

    async def publish_event(self, envelope: Envelope) -> None:
        await self._request(
            "PUT",
            f"mailboxes/{self.device_id}/events/{envelope.message_id}",
            json=envelope.model_dump(by_alias=True, mode="json"),
        )
        if envelope.type.value in {"APPROVAL_REQUEST", "TURN_COMPLETE", "BUILD_RESULT", "ERROR"}:
            await self._request(
                "PUT",
                f"notifications/{self.device_id}/{envelope.message_id}",
                json={"type": envelope.type.value, "createdAt": envelope.created_at},
            )

    async def acknowledge(self, command_id: str) -> None:
        await self._request(
            "PUT",
            f"mailboxes/{self.device_id}/acks/{command_id}",
            json={"processedAt": int(time.time() * 1000)},
        )
        await self._request("DELETE", f"mailboxes/{self.device_id}/commands/{command_id}")

    async def heartbeat(self) -> None:
        await self._request(
            "PUT",
            f"deviceStatus/{self.device_id}",
            json={"online": True, "lastSeen": int(time.time() * 1000)},
        )

    async def upload_artifact(self, object_name: str, encrypted_content: bytes) -> None:
        if not self.config.storage_bucket:
            raise RuntimeError("firebase_storage_bucket is not configured")
        await self._ensure_token()
        response = await self.client.post(
            f"https://firebasestorage.googleapis.com/v0/b/{self.config.storage_bucket}/o",
            params={"uploadType": "media", "name": object_name},
            headers={
                "Authorization": f"Bearer {self.id_token}",
                "Content-Type": "application/octet-stream",
            },
            content=encrypted_content,
        )
        response.raise_for_status()

    async def start_pairing(self, secret_verifier: str, encrypted_name: str) -> dict[str, Any]:
        response = await self.client.post(
            f"{self.config.functions_base_url.rstrip('/')}/startPairing",
            json={"deviceId": self.device_id, "secretVerifier": secret_verifier, "encryptedName": encrypted_name},
        )
        response.raise_for_status()
        return response.json()

    async def complete_pairing(self, pairing_id: str, secret: str) -> bool:
        response = await self.client.post(
            f"{self.config.functions_base_url.rstrip('/')}/completePairing",
            json={"deviceId": self.device_id, "pairingId": pairing_id, "secret": secret},
        )
        if response.status_code == 202:
            return False
        response.raise_for_status()
        await self.exchange_custom_token(response.json()["customToken"])
        return True
