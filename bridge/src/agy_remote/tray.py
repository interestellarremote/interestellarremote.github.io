from __future__ import annotations

import webbrowser
from collections.abc import Callable


def start_tray(url: str, on_quit: Callable[[], None]):
    import pystray
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (64, 64), "#10131a")
    draw = ImageDraw.Draw(image)
    draw.ellipse((10, 10, 54, 54), fill="#8ba8ff")
    draw.rectangle((29, 18, 35, 46), fill="#10131a")
    draw.rectangle((18, 29, 46, 35), fill="#10131a")
    icon = pystray.Icon(
        "AntigravityRemote",
        image,
        "Interestellar Remote Bridge",
        menu=pystray.Menu(
            pystray.MenuItem("Abrir painel", lambda: webbrowser.open(url), default=True),
            pystray.MenuItem("Sair", lambda: (on_quit(), icon.stop())),
        ),
    )
    icon.run_detached()
    return icon
