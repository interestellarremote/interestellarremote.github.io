from __future__ import annotations

from enum import StrEnum
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

from pydantic import BaseModel, Field, field_validator


class MessageType(StrEnum):
    CREATE_CONVERSATION = "CREATE_CONVERSATION"
    ARCHIVE_CONVERSATION = "ARCHIVE_CONVERSATION"
    SEND_PROMPT = "SEND_PROMPT"
    CANCEL_TURN = "CANCEL_TURN"
    RUN_BUILD = "RUN_BUILD"
    CANCEL_BUILD = "CANCEL_BUILD"
    APPROVAL_DECISION = "APPROVAL_DECISION"
    LIST_DIRECTORIES = "LIST_DIRECTORIES"
    CREATE_DIRECTORY = "CREATE_DIRECTORY"
    ADD_PROJECT = "ADD_PROJECT"
    LIST_PROJECT_FILES = "LIST_PROJECT_FILES"
    CREATE_PROJECT_DIRECTORY = "CREATE_PROJECT_DIRECTORY"
    READ_PROJECT_FILE = "READ_PROJECT_FILE"
    GET_PROJECT_STATUS = "GET_PROJECT_STATUS"
    SYNC = "SYNC"
    SNAPSHOT = "SNAPSHOT"
    TEXT_DELTA = "TEXT_DELTA"
    THOUGHT_DELTA = "THOUGHT_DELTA"
    TOOL_CALL = "TOOL_CALL"
    APPROVAL_REQUEST = "APPROVAL_REQUEST"
    BUILD_LOG = "BUILD_LOG"
    BUILD_RESULT = "BUILD_RESULT"
    ARTIFACT = "ARTIFACT"
    TURN_COMPLETE = "TURN_COMPLETE"
    ERROR = "ERROR"
    HEARTBEAT = "HEARTBEAT"
    DIRECTORY_LIST = "DIRECTORY_LIST"
    PROJECT_FILE_LIST = "PROJECT_FILE_LIST"
    PROJECT_FILE_CONTENT = "PROJECT_FILE_CONTENT"
    PROJECT_STATUS = "PROJECT_STATUS"
    ACK = "ACK"


class Envelope(BaseModel):
    version: int = 1
    message_id: UUID = Field(default_factory=uuid4, alias="messageId")
    device_id: str = Field(alias="deviceId", min_length=16, max_length=128)
    conversation_id: str = Field(alias="conversationId", min_length=1, max_length=128)
    sequence: int = Field(ge=1)
    type: MessageType
    created_at: int = Field(alias="createdAt", ge=0)
    expires_at: int = Field(alias="expiresAt", ge=0)
    key_version: int = Field(1, alias="keyVersion", ge=1)
    nonce: str
    ciphertext: str

    model_config = {"populate_by_name": True}


class BuildProfile(BaseModel):
    id: str
    name: str
    working_directory: str = Field(".", alias="workingDirectory")
    executable: str
    arguments: list[str] = Field(default_factory=list)
    timeout_seconds: int = Field(900, alias="timeoutSeconds", ge=1, le=7200)
    artifact_globs: list[str] = Field(default_factory=list, alias="artifactGlobs")

    model_config = {"populate_by_name": True}

    @field_validator("working_directory")
    @classmethod
    def relative_working_directory(cls, value: str) -> str:
        if Path(value).is_absolute() or ".." in Path(value).parts:
            raise ValueError("workingDirectory must stay inside the project")
        return value


class Project(BaseModel):
    id: str
    name: str
    root: Path
    build_profiles: list[BuildProfile] = Field(default_factory=list, alias="buildProfiles")

    model_config = {"populate_by_name": True}


class CommandPayload(BaseModel):
    task_id: str | None = Field(None, alias="taskId")
    project_id: str | None = Field(None, alias="projectId")
    prompt: str | None = None
    build_profile_id: str | None = Field(None, alias="buildProfileId")
    approval_id: str | None = Field(None, alias="approvalId")
    approved: bool | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)
    model: str | None = None
    execution_mode: str | None = Field(None, alias="executionMode")

    model_config = {"populate_by_name": True}
