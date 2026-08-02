import json
import time
from pathlib import Path

import pytest

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from agy_remote.crypto import EnvelopeCrypto, associated_data, b64d, derive_key
from agy_remote.models import MessageType


def test_encrypt_decrypt_and_tamper_detection():
    crypto = EnvelopeCrypto("d" * 32, bytes(range(32)))
    now = int(time.time() * 1000)
    envelope = crypto.encrypt(
        conversation_id="conversation-1",
        sequence=1,
        message_type=MessageType.SEND_PROMPT,
        payload={"prompt": "olá"},
        created_at=now,
        expires_at=now + 1000,
    )
    assert crypto.decrypt(envelope) == {"prompt": "olá"}
    envelope.sequence = 2
    with pytest.raises(Exception):
        crypto.decrypt(envelope)


def test_shared_android_python_vector():
    vector_path = Path(__file__).parents[2] / "contracts" / "crypto-vector-v1.json"
    vector = json.loads(vector_path.read_text(encoding="utf-8"))
    key = derive_key(b64d(vector["rootKey"]), vector["deviceId"], vector["conversationId"], 1)
    plaintext = AESGCM(key).decrypt(
        b64d(vector["nonce"]),
        b64d(vector["ciphertext"]),
        associated_data(
            vector["deviceId"], vector["conversationId"], 7, MessageType.SEND_PROMPT, 1
        ),
    )
    assert plaintext.decode() == vector["plaintext"]
