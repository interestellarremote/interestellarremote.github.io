from __future__ import annotations

import argparse
import asyncio
import os
import webbrowser
from datetime import datetime
from pathlib import Path

import uvicorn

from .config import Settings
from .crypto import EnvelopeCrypto
from .dashboard import DashboardState, create_dashboard
from .database import Database
from .firebase_transport import FirebaseConfig, FirebaseTransport
from .secrets import SecretStore
from .service import BridgeService
from .tray import start_tray


def _startup_trace(message: str) -> None:
    if os.getenv("AGY_REMOTE_DIAGNOSTICS") != "1":
        return
    directory = Path(os.getenv("LOCALAPPDATA", ".")) / "AntigravityRemote"
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / "bridge-startup.log").open("a", encoding="utf-8") as stream:
        stream.write(f"{datetime.now().isoformat(timespec='seconds')} {message}\n")


async def serve(settings: Settings) -> None:
    _startup_trace("serve: entered")
    database = Database(settings.data_dir / "bridge.db")
    secrets = SecretStore(settings.data_dir / "secrets")
    transport = FirebaseTransport(
        FirebaseConfig(
            database_url=settings.firebase_database_url,
            api_key=settings.firebase_api_key,
            functions_base_url=settings.functions_base_url,
            storage_bucket=settings.firebase_storage_bucket,
        ),
        settings.device_id,
    )
    refresh = secrets.get("firebase_refresh_token")
    if refresh:
        transport.refresh_token = refresh.decode()

    state = DashboardState(settings, database, secrets, transport)
    app = create_dashboard(state)
    _startup_trace("serve: dashboard created")
    server = uvicorn.Server(
        uvicorn.Config(
            app,
            host="127.0.0.1",
            port=settings.dashboard_port,
            log_level="info",
            # PyInstaller's --windowed mode sets stdout/stderr to None. Uvicorn's
            # default formatter probes isatty(), which crashes before the server
            # starts in that environment. The tray app does not need console logs.
            log_config=None,
            access_log=False,
        )
    )
    async def launch_server() -> None:
        _startup_trace("server: starting")
        await server.serve()
        _startup_trace("server: stopped")

    tasks = [asyncio.create_task(launch_server())]
    url = f"http://127.0.0.1:{settings.dashboard_port}"
    tray_holder: dict[str, object] = {}

    async def launch_tray() -> None:
        # Some frozen/windowed Windows environments block while pystray selects
        # its backend. Keep that work away from the asyncio server thread so the
        # local dashboard can start independently.
        _startup_trace("tray: starting")
        tray_holder["tray"] = await asyncio.to_thread(
            start_tray, url, lambda: setattr(server, "should_exit", True)
        )
        _startup_trace("tray: started")

    tasks.append(asyncio.create_task(launch_tray()))
    service_holder: dict[str, BridgeService] = {}

    def apply_rotated_key(root_key: bytes, key_version: int) -> None:
        service = service_holder.get("service")
        if service:
            service.crypto = EnvelopeCrypto(settings.device_id, root_key, key_version)

    state.on_key_ready = apply_rotated_key

    async def supervise_service() -> None:
        while not server.should_exit:
            if "service" not in service_holder:
                current_root_key = secrets.get("root_key")
                current_refresh = secrets.get("firebase_refresh_token")
                if current_root_key and current_refresh:
                    transport.refresh_token = current_refresh.decode()
                    service_holder["service"] = BridgeService(
                        database,
                        EnvelopeCrypto(
                            settings.device_id,
                            current_root_key,
                            int((secrets.get("key_version") or b"1").decode()),
                        ),
                        transport,
                    )
                    await service_holder["service"].run()
                    return
            await asyncio.sleep(2)

    tasks.append(asyncio.create_task(supervise_service()))
    if os.getenv("AGY_REMOTE_NO_BROWSER") != "1":
        webbrowser.open(url)
    _startup_trace("serve: awaiting tasks")
    try:
        await asyncio.gather(*tasks)
    finally:
        tray = tray_holder.get("tray")
        if tray is not None:
            tray.stop()
        service = service_holder.get("service")
        if service:
            await service.close()
        else:
            await transport.close()


def run() -> None:
    _startup_trace("run: entered")
    parser = argparse.ArgumentParser(description="Antigravity Remote Windows bridge")
    parser.add_argument("--config", help="Path to config.json")
    args = parser.parse_args()
    settings = Settings.load(None if not args.config else __import__("pathlib").Path(args.config))
    _startup_trace("run: settings loaded")
    asyncio.run(serve(settings))


if __name__ == "__main__":
    run()
