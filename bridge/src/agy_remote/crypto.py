from __future__ import annotations

import base64
import json
import os
from dataclasses import dataclass
from typing import Any

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from .models import Envelope, MessageType


def b64e(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def b64d(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def derive_key(root_key: bytes, device_id: str, conversation_id: str, key_version: int) -> bytes:
    info = f"agy-remote/v1/{device_id}/{conversation_id}/{key_version}".encode()
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=None, info=info).derive(root_key)


def associated_data(
    device_id: str, conversation_id: str, sequence: int, message_type: MessageType, key_version: int
) -> bytes:
    return f"1|{device_id}|{conversation_id}|{sequence}|{message_type.value}|{key_version}".encode()


@dataclass(slots=True)
class EnvelopeCrypto:
    device_id: str
    root_key: bytes
    key_version: int = 1

    def encrypt(
        self,
        *,
        conversation_id: str,
        sequence: int,
        message_type: MessageType,
        payload: dict[str, Any],
        created_at: int,
        expires_at: int,
    ) -> Envelope:
        nonce = os.urandom(12)
        key = derive_key(self.root_key, self.device_id, conversation_id, self.key_version)
        aad = associated_data(
            self.device_id, conversation_id, sequence, message_type, self.key_version
        )
        plaintext = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        ciphertext = AESGCM(key).encrypt(nonce, plaintext, aad)
        return Envelope(
            deviceId=self.device_id,
            conversationId=conversation_id,
            sequence=sequence,
            type=message_type,
            createdAt=created_at,
            expiresAt=expires_at,
            keyVersion=self.key_version,
            nonce=b64e(nonce),
            ciphertext=b64e(ciphertext),
        )

    def decrypt(self, envelope: Envelope) -> dict[str, Any]:
        if envelope.device_id != self.device_id:
            raise ValueError("envelope is addressed to another device")
        key = derive_key(
            self.root_key, envelope.device_id, envelope.conversation_id, envelope.key_version
        )
        aad = associated_data(
            envelope.device_id,
            envelope.conversation_id,
            envelope.sequence,
            envelope.type,
            envelope.key_version,
        )
        plaintext = AESGCM(key).decrypt(b64d(envelope.nonce), b64d(envelope.ciphertext), aad)
        return json.loads(plaintext)

    def encrypt_artifact(self, conversation_id: str, remote_name: str, content: bytes) -> bytes:
        if len(content) > 95 * 1024 * 1024:
            raise ValueError("artifact exceeds the 95 MiB encrypted upload limit")
        nonce = os.urandom(12)
        key = derive_key(self.root_key, self.device_id, conversation_id, self.key_version)
        aad = f"artifact|{self.device_id}|{conversation_id}|{remote_name}|{self.key_version}".encode()
        return b"AGYR1" + nonce + AESGCM(key).encrypt(nonce, content, aad)
