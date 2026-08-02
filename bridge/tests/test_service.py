import asyncio
from types import SimpleNamespace

from agy_remote.models import CommandPayload, MessageType
from agy_remote.service import BridgeService


async def test_background_turn_failure_is_emitted_to_phone() -> None:
    service = object.__new__(BridgeService)
    emitted: list[tuple[str, MessageType, dict[str, str]]] = []

    async def failing_turn(envelope: object, payload: object) -> None:
        raise RuntimeError("agent failed")

    async def capture(conversation_id: str, kind: MessageType, payload: dict[str, str]) -> None:
        emitted.append((conversation_id, kind, payload))

    service._run_turn = failing_turn  # type: ignore[method-assign]
    service.emit = capture  # type: ignore[method-assign]

    await service._run_turn_safely(
        SimpleNamespace(conversation_id="conversation-1"), CommandPayload()
    )

    assert emitted == [
        (
            "conversation-1",
            MessageType.ERROR,
            {"code": "RuntimeError", "message": "agent failed"},
        )
    ]


async def test_cancelled_turn_is_persisted_and_emitted_to_phone() -> None:
    service = object.__new__(BridgeService)
    emitted: list[tuple[str, MessageType, dict[str, object]]] = []
    updates: list[tuple[object, ...]] = []

    async def cancelled_turn(envelope: object, payload: object) -> None:
        raise asyncio.CancelledError

    async def capture(conversation_id: str, kind: MessageType, payload: dict[str, object]) -> None:
        emitted.append((conversation_id, kind, payload))

    service._run_turn = cancelled_turn  # type: ignore[method-assign]
    service.emit = capture  # type: ignore[method-assign]
    service.sessions = SimpleNamespace(consume_cancel_reason=lambda cid: None)
    service.database = SimpleNamespace(
        get_task=lambda task_id: None,
        update_task=lambda *args, **kwargs: updates.append((*args, kwargs)),
    )

    await service._run_turn_safely(
        SimpleNamespace(conversation_id="conversation-1", message_id="command-1"),
        CommandPayload(taskId="task-1"),
    )

    assert updates == [
        (
            "task-1",
            "ERROR",
            "interrupted",
            0,
            "A execução foi interrompida antes de concluir.",
            {},
        )
    ]
    assert emitted == [
        (
            "conversation-1",
            MessageType.ERROR,
            {
                "code": "EXECUTION_INTERRUPTED",
                "message": "A execução foi interrompida antes de concluir.",
                "taskId": "task-1",
            },
        )
    ]


async def test_late_cancel_replays_completed_state_to_unblock_phone() -> None:
    service = object.__new__(BridgeService)
    emitted: list[tuple[str, MessageType, dict[str, object]]] = []
    updates: list[tuple[object, ...]] = []

    async def no_active_turn(conversation_id: str, reason: str | None = None) -> bool:
        return False

    async def capture(conversation_id: str, kind: MessageType, payload: dict[str, object]) -> None:
        emitted.append((conversation_id, kind, payload))

    service.sessions = SimpleNamespace(cancel=no_active_turn)
    service.emit = capture  # type: ignore[method-assign]
    service.database = SimpleNamespace(
        get_task=lambda task_id: {"id": task_id, "status": "COMPLETE", "error": None},
        update_task=lambda *args, **kwargs: updates.append((*args, kwargs)),
    )

    await service._cancel_turn("conversation-1", "task-1")

    assert updates == []
    assert emitted == [
        (
            "conversation-1",
            MessageType.TURN_COMPLETE,
            {"taskId": "task-1", "reconciled": True},
        )
    ]


async def test_late_cancel_closes_orphaned_nonterminal_task() -> None:
    service = object.__new__(BridgeService)
    emitted: list[tuple[str, MessageType, dict[str, object]]] = []
    updates: list[tuple[object, ...]] = []

    async def no_active_turn(conversation_id: str, reason: str | None = None) -> bool:
        return False

    async def capture(conversation_id: str, kind: MessageType, payload: dict[str, object]) -> None:
        emitted.append((conversation_id, kind, payload))

    service.sessions = SimpleNamespace(cancel=no_active_turn)
    service.emit = capture  # type: ignore[method-assign]
    service.database = SimpleNamespace(
        get_task=lambda task_id: {"id": task_id, "status": "RUNNING", "error": None},
        update_task=lambda *args, **kwargs: updates.append((*args, kwargs)),
    )

    await service._cancel_turn("conversation-1", "task-1")

    assert updates == [
        ("task-1", "CANCELLING", "cancelling", {}),
        (
            "task-1",
            "CANCELLED",
            "cancelled",
            {"error": "Tarefa não estava mais ativa"},
        ),
    ]
    assert emitted == [
        (
            "conversation-1",
            MessageType.ERROR,
            {
                "code": "USER_CANCELLED",
                "message": "Tarefa não estava mais ativa",
                "taskId": "task-1",
            },
        )
    ]
