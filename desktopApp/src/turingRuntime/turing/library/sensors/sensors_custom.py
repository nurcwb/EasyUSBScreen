# SPDX-License-Identifier: GPL-3.0-or-later
#
# turing-smart-screen-python - a Python system monitor and library for USB-C displays like Turing Smart Screen or XuanFang
# https://github.com/mathoudebine/turing-smart-screen-python/
#
# Copyright (C) 2021 Matthieu Houdebine (mathoudebine)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# This file allows to add custom data source as sensors and display them in System Monitor themes
# There is no limitation on how much custom data source classes can be added to this file
# See CustomDataExample theme for the theme implementation part

import math
import platform
from abc import ABC, abstractmethod
from typing import List


# Custom data classes must be implemented in this file, inherit the CustomDataSource and implement its 2 methods
class CustomDataSource(ABC):
    @abstractmethod
    def as_numeric(self) -> float:
        # Numeric value will be used for graph and radial progress bars
        # If there is no numeric value, keep this function empty
        pass

    @abstractmethod
    def as_string(self) -> str:
        # Text value will be used for text display and radial progress bar inner text
        # Numeric value can be formatted here to be displayed as expected
        # It is also possible to return a text unrelated to the numeric value
        # If this function is empty, the numeric value will be used as string without formatting
        pass

    @abstractmethod
    def last_values(self) -> List[float]:
        # List of last numeric values will be used for plot graph
        # If you do not want to draw a line graph or if your custom data has no numeric values, keep this function empty
        pass


# Example for a custom data class that has numeric and text values
class ExampleCustomNumericData(CustomDataSource):
    # This list is used to store the last 10 values to display a line graph
    last_val = [math.nan] * 10  # By default, it is filed with math.nan values to indicate there is no data stored

    def as_numeric(self) -> float:
        # Numeric value will be used for graph and radial progress bars
        # Here a Python function from another module can be called to get data
        # Example: self.value = my_module.get_rgb_led_brightness() / audio.system_volume() ...
        self.value = 75.845

        # Store the value to the history list that will be used for line graph
        self.last_val.append(self.value)
        # Also remove the oldest value from history list
        self.last_val.pop(0)

        return self.value

    def as_string(self) -> str:
        # Text value will be used for text display and radial progress bar inner text.
        # Numeric value can be formatted here to be displayed as expected
        # It is also possible to return a text unrelated to the numeric value
        # If this function is empty, the numeric value will be used as string without formatting
        # Example here: format numeric value: add unit as a suffix, and keep 1 digit decimal precision
        return f'{self.value:>5.1f}%'
        # Important note! If your numeric value can vary in size, be sure to display it with a default size.
        # E.g. if your value can range from 0 to 9999, you need to display it with at least 4 characters every time.
        # --> return f'{self.as_numeric():>4}%'
        # Otherwise, part of the previous value can stay displayed ("ghosting") after a refresh

    def last_values(self) -> List[float]:
        # List of last numeric values will be used for plot graph
        return self.last_val


# Example for a custom data class that only has text values
class ExampleCustomTextOnlyData(CustomDataSource):
    def as_numeric(self) -> float:
        # If there is no numeric value, keep this function empty
        pass

    def as_string(self) -> str:
        # If a custom data class only has text values, it won't be possible to display graph or radial bars
        return "Python: " + platform.python_version()

    def last_values(self) -> List[float]:
        # If a custom data class only has text values, it won't be possible to display line graph
        pass


# SPDX-License-Identifier: GPL-3.0-or-later


# SPDX-License-Identifier: GPL-3.0-or-later

# BEGIN CICLOPE CUSTOM SENSORS
# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeLocalIPv4(CustomDataSource):
    def as_numeric(self):
        return None

    def as_string(self):
        import socket

        import psutil

        from library.config import CONFIG_DATA

        interface = CONFIG_DATA["config"].get("ETH", "")
        for address in psutil.net_if_addrs().get(interface, ()):
            if address.family == socket.AF_INET:
                return address.address
        return "Sem IPv4"

    def last_values(self):
        return None


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeRamGB(CiclopeLocalIPv4):
    def as_string(self):
        import psutil

        memory = psutil.virtual_memory()
        return f"{(memory.total - memory.available) / 1e9:.1f}/{memory.total / 1e9:.1f} GB"


class CiclopeVramGB(CiclopeLocalIPv4):
    def as_string(self):
        from math import isfinite

        from library.stats import sensors

        _, _, used_mib, total_mib, _ = sensors.Gpu.stats()
        if not all(map(isfinite, (used_mib, total_mib))):
            return "—"
        return f"{used_mib * 2**20 / 1e9:.1f}/{total_mib * 2**20 / 1e9:.1f} GB"


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeSshUsers(CiclopeLocalIPv4):
    def as_string(self):
        import json
        import subprocess

        def loginctl(*args):
            return subprocess.run(
                ["loginctl", *args], capture_output=True, text=True,
                check=True, timeout=2,
            ).stdout

        try:
            sessions = json.loads(loginctl("list-sessions", "--json=short")) or []
            ids = [session["session"] for session in sessions if session["class"] == "user"]
            if not ids:
                return "SSH: sem sessão"
            details = loginctl(
                "show-session", "-p", "Remote", "-p", "Service",
                "-p", "Name", "-p", "State", *ids,
            )
        except (OSError, ValueError, subprocess.SubprocessError):
            return "SSH: indisponível"

        users = set()
        for block in details.strip().split("\n\n"):
            values = dict(line.split("=", 1) for line in block.splitlines() if "=" in line)
            if (values.get("Remote") == "yes"
                    and values.get("Service") in {"sshd", "sshd-session"}
                    and values.get("State") != "closing" and values.get("Name")):
                users.add(values["Name"])

        label = "SSH: " + (", ".join(sorted(users)) if users else "sem sessão")
        return label if len(label) <= 37 else label[:36] + "…"


class CiclopeSshName(CiclopeSshUsers):
    def as_string(self):
        name = super().as_string().removeprefix("SSH: ")
        return name if len(name) <= 9 else name[:8] + "…"


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeOrbital(CiclopeLocalIPv4):
    bounds = (125, 30, 355, 252)

    @staticmethod
    def render(background, font_path, cpu, gpu):
        from math import isfinite

        from PIL import ImageDraw, ImageFont

        bounds = (125, 30, 355, 252)
        panel = background.crop(bounds).convert("RGB")
        draw = ImageDraw.Draw(panel)
        cx, cy = 115, 112
        cyan, magenta = "#69f6ff", "#ff8df3"

        for value, radius, start, width, color in (
            (cpu, 94, 206, 9, cyan), (gpu, 79, 22, 7, magenta),
        ):
            if isfinite(value) and value > 0:
                draw.arc((cx-radius, cy-radius, cx+radius, cy+radius),
                         start, start + min(value, 100)*2.85, fill=color, width=width)

        def label(text, y, size, color):
            font = ImageFont.truetype(font_path, size)
            draw.text((cx, y), text, font=font, fill=color, anchor="mm")

        label(f"{cpu:.0f}%" if isfinite(cpu) else "—", 91, 35, cyan)
        label("CPU", 115, 11, "#e3f5ff")
        label(f"{gpu:.0f}% GPU" if isfinite(gpu) else "— GPU", 141, 18, magenta)
        return panel

    def as_string(self):
        from pathlib import Path

        import psutil

        from library import config
        from library.display import display
        from library.stats import sensors

        cpu = psutil.cpu_percent(interval=0.15)
        gpu = sensors.Gpu.stats()[0]
        background = Path(config.THEME_DATA["PATH"]) / "background.png"
        font = Path(config.FONTS_DIR) / "jetbrains-mono/JetBrainsMono-Bold.ttf"
        panel = self.render(display.lcd.open_image(str(background)), str(font), cpu, gpu)
        display.lcd.DisplayPILImage(panel, *self.bounds[:2])
        return ""


class CiclopeOrbitalCinema(CiclopeOrbital):
    bounds = (119, 47, 361, 273)

    @classmethod
    def render(cls, background, font_path, cpu, gpu):
        from math import isfinite
        from PIL import ImageDraw, ImageFont

        panel = background.crop(cls.bounds).convert("RGB")
        draw = ImageDraw.Draw(panel)
        cx, cy = 121, 113
        for value, radius, count, color in (
            (cpu, 101, 60, "#00f0ff"), (gpu, 79, 60, "#ff188a"),
        ):
            if not isfinite(value):
                continue
            step = 360 / count
            for index in range(min(count, max(0, round(value * count / 100)))):
                start = -90 + index * step
                draw.arc((cx-radius, cy-radius, cx+radius, cy+radius),
                         start, start + step - 1.25, fill=color, width=10)
        font = ImageFont.truetype(font_path, 28)
        for value, y, color in ((cpu, 92, "#00f0ff"), (gpu, 136, "#ff188a")):
            draw.text((cx, y), f"{value:.0f}%" if isfinite(value) else "—",
                      font=font, fill=color, anchor="mm")
        return panel


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeViaLactea(CiclopeLocalIPv4):
    bounds = (12, 34, 468, 284)

    @classmethod
    def render(cls, background, font_path, cpu, cpu_temp, gpu, gpu_temp, ram, vram):
        from math import isfinite
        from PIL import ImageDraw, ImageFont

        left, top, _, _ = cls.bounds
        panel = background.crop(cls.bounds).convert("RGB")
        draw = ImageDraw.Draw(panel)
        cyan, amber, pink, white = "#47e8ff", "#ffbf52", "#ff5b91", "#f5faff"

        def text(x, y, value, size, color, anchor="mm"):
            font = ImageFont.truetype(font_path, size)
            draw.text((x-left, y-top), value, font=font, fill=color, anchor=anchor)

        def arc(x, y, radius, width, value, color):
            if isfinite(value) and value > 0:
                x, y = x-left, y-top
                draw.arc((x-radius, y-radius, x+radius, y+radius),
                         -90, -90 + min(value, 100)*3.6, fill=color, width=width)

        for x, y, use, temperature, color in (
            (142, 134, cpu, cpu_temp, cyan), (344, 181, gpu, gpu_temp, amber),
        ):
            arc(x, y, 55, 7, use, color)
            text(x, y-4, f"{use:.0f}%" if isfinite(use) else "—", 25, white)
            text(x, y+23, f"{temperature:.0f} °C" if isfinite(temperature) else "—", 14, color)
        text(142, 173, "TCTL", 8, cyan)

        for (used, total), x, y, color, tx, ty, anchor in (
            (ram, 58, 234, cyan, 88, 233, "lt"),
            (vram, 422, 72, pink, 393, 72, "rt"),
        ):
            if all(map(isfinite, (used, total))) and total > 0:
                arc(x, y, 20, 4, 100*used/total, color)
                value = f"{used:.1f} / {total:.1f} GB".replace(".", ",")
            else:
                value = "—"
            dot_color = amber if color == pink else cyan
            draw.ellipse((x-left-4, y-top-4, x-left+4, y-top+4), fill=dot_color)
            text(tx, ty, value, 12, white, anchor)
        return panel

    def as_string(self):
        from pathlib import Path

        import psutil

        from library import config
        from library.display import display
        from library.stats import sensors

        cpu = psutil.cpu_percent(interval=0.15)
        cpu_temp = sensors.Cpu.temperature()
        gpu, _, vram_used, vram_total, gpu_temp = sensors.Gpu.stats()
        memory = psutil.virtual_memory()
        ram = ((memory.total-memory.available)/1e9, memory.total/1e9)
        vram = (vram_used*2**20/1e9, vram_total*2**20/1e9)
        background = Path(config.THEME_DATA["PATH"]) / "background.png"
        font = Path(config.FONTS_DIR) / "jetbrains-mono/JetBrainsMono-Bold.ttf"
        panel = self.render(display.lcd.open_image(str(background)), str(font),
                            cpu, cpu_temp, gpu, gpu_temp, ram, vram)
        display.lcd.DisplayPILImage(panel, *self.bounds[:2])
        return ""


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeSentinel(CiclopeViaLactea):
    bounds = (8, 36, 473, 281)

    @classmethod
    def render(cls, background, font_path, cpu, cpu_temp, gpu, gpu_temp, ram, vram):
        from math import isfinite

        from PIL import ImageDraw, ImageFont

        left, top, _, _ = cls.bounds
        panel = background.crop(cls.bounds).convert("RGB")
        draw = ImageDraw.Draw(panel)
        yellow, orange, white = "#ffdc18", "#ff690b", "#f5f4ff"

        def text(x, y, value, size, color, anchor="lt"):
            font = ImageFont.truetype(font_path, size)
            draw.text((x-left, y-top), value, font=font, fill=color, anchor=anchor)

        def segments(x, y, value, count, pitch, width, height, color, reverse=False):
            if not isfinite(value):
                return
            lit = max(0, min(count, round(value * count / 100)))
            indices = range(count-lit, count) if reverse else range(lit)
            for index in indices:
                start = x-left + index*pitch
                draw.rectangle((start, y-top, start+width-1, y-top+height-1), fill=color)

        for x, use, temperature, color in (
            (24, cpu, cpu_temp, yellow), (332, gpu, gpu_temp, orange),
        ):
            text(x, 83, f"{use:.0f}%" if isfinite(use) else "—", 29, color)
            text(x+124, 115, f"{temperature:.0f} °C" if isfinite(temperature) else "—",
                 17, white, "rt")
            segments(x, 144, use, 14, 9, 7, 10, color)

        text(240, 108, f"{cpu:.0f}%" if isfinite(cpu) else "—", 10, "#211200", "mm")
        text(240, 123, f"{gpu:.0f}%" if isfinite(gpu) else "—", 10, "#572400", "mm")

        for (used, total), x, tx, anchor, reverse in (
            (ram, 20, 216, "rt", False), (vram, 266, 264, "lt", True),
        ):
            available = all(map(isfinite, (used, total))) and total > 0
            value = f"{used:.1f} / {total:.1f} GB".replace(".", ",") if available else "—"
            text(tx, 193, value, 12, white, anchor)
            if available:
                segments(x, 235, 100*used/total, 22, 8.8, 6, 33, "#ffbb12", reverse)
        return panel


# SPDX-License-Identifier: GPL-3.0-or-later
class CiclopeHudBase(CiclopeViaLactea):
    bounds = (8, 34, 472, 284)

    @classmethod
    def render(cls, background, font_path, cpu, cpu_temp, gpu, gpu_temp, ram, vram):
        from math import isfinite

        from PIL import ImageDraw, ImageFont

        left, top = cls.bounds[:2]
        panel = background.crop(cls.bounds).convert("RGB")
        draw = ImageDraw.Draw(panel)
        white = "#f5f4ff"

        def text(x, y, value, size, color=white, anchor="lt"):
            font = ImageFont.truetype(font_path, size)
            draw.text((x-left, y-top), value, font=font, fill=color, anchor=anchor)

        def box(x, y, w, h, color):
            draw.rectangle((round(x-left), round(y-top),
                            round(x-left+w-1), round(y-top+h-1)), fill=color)

        def segments(x, y, percent, count, pitch, w, h, color, dx=1, dy=0):
            if not isfinite(percent):
                return
            lit = max(0, min(count, round(count*percent/100)))
            for i in range(lit):
                box(x+i*pitch*dx, y+i*pitch*dy, w, h, color)

        def arc_segments(radius, percent, color):
            if not isfinite(percent):
                return
            x, y = 240-left, 152-top
            for i in range(max(0, min(60, round(percent*0.6)))):
                start = -90 + i*6
                draw.arc((x-radius, y-radius, x+radius, y+radius),
                         start, start+4.7, fill=color, width=9)

        def pct(value):
            return f"{value:.0f}%" if isfinite(value) else "—"

        def temp(value):
            return f"{value:.0f} °C" if isfinite(value) else "—"

        def memory(values):
            used, total = values
            if not all(map(isfinite, values)) or total <= 0:
                return "—", float("nan")
            return f"{used:.1f} / {total:.1f} GB".replace(".", ","), used/total*100

        ram_text, ram_pct = memory(ram)
        vram_text, vram_pct = memory(vram)
        yellow, orange = cls.colors
        cls.draw_values(text, segments, arc_segments, pct(cpu), temp(cpu_temp),
                        pct(gpu), temp(gpu_temp), cpu, gpu,
                        ram_text, ram_pct, vram_text, vram_pct, yellow, orange)
        return panel


class CiclopeTrask(CiclopeHudBase):
    colors = ("#ffdd16", "#ff690b")

    @staticmethod
    def draw_values(text, segments, arc, cpu, cpu_temp, gpu, gpu_temp, cpu_pct, gpu_pct,
                    ram, ram_pct, vram, vram_pct, yellow, orange):
        arc(99, cpu_pct, yellow)
        arc(81, gpu_pct, orange)
        text(18, 80, cpu, 27, yellow)
        text(18, 120, cpu_temp, 19)
        text(462, 80, gpu, 27, orange, "rt")
        text(462, 120, gpu_temp, 19, anchor="rt")
        text(240, 133, cpu, 9, "#271700", "mm")
        text(240, 145, gpu, 9, "#572400", "mm")
        text(18, 197, ram, 11)
        text(460, 197, vram, 11, anchor="rt")
        segments(19, 230, ram_pct, 12, 8.8, 6, 30, yellow)
        segments(454, 230, vram_pct, 12, 8.8, 6, 30, orange, dx=-1)


class CiclopeTarsMonolith(CiclopeHudBase):
    colors = ("#f5e750", "#ffb42e")

    @staticmethod
    def draw_values(text, segments, arc, cpu, cpu_temp, gpu, gpu_temp, cpu_pct, gpu_pct,
                    ram, ram_pct, vram, vram_pct, yellow, orange):
        text(26, 74, cpu, 32, yellow)
        text(190, 112, cpu_temp, 19, anchor="rt")
        text(290, 74, gpu, 32, orange)
        text(454, 112, gpu_temp, 19, anchor="rt")
        segments(26, 152, cpu_pct, 14, 11.9, 10, 24, yellow)
        segments(444, 152, gpu_pct, 14, 11.9, 10, 24, orange, dx=-1)
        text(190, 202, ram, 11, anchor="rt")
        text(290, 202, vram, 11)
        segments(26, 245, ram_pct, 16, 10.3, 8, 25, yellow)
        segments(446, 245, vram_pct, 16, 10.3, 8, 25, orange, dx=-1)


class CiclopeTarsTesseract(CiclopeHudBase):
    bounds = (8, 36, 357, 284)
    colors = ("#f5e750", "#ff9a26")

    @staticmethod
    def draw_values(text, segments, arc, cpu, cpu_temp, gpu, gpu_temp, cpu_pct, gpu_pct,
                    ram, ram_pct, vram, vram_pct, yellow, orange):
        text(14, 98, cpu, 24, yellow)
        text(14, 150, cpu_temp, 17)
        text(130, 98, gpu, 24, orange)
        text(130, 150, gpu_temp, 17)
        segments(84, 263, cpu_pct, 18, 10.5, 26, 9, yellow, dx=0, dy=-1)
        segments(200, 263, gpu_pct, 18, 10.5, 26, 9, orange, dx=0, dy=-1)
        text(252, 84, ram, 11)
        text(252, 173, vram, 11)
        segments(252, 106, ram_pct, 10, 10, 8, 22, yellow)
        segments(252, 194, vram_pct, 10, 10, 8, 22, orange)


# SPDX-License-Identifier: GPL-3.0-or-later
from collections import deque
from dataclasses import dataclass
from functools import lru_cache
from math import isfinite, nan


@dataclass
class CiclopeReadings:
    cpu: float = nan
    cpu_temp: float = nan
    cpu_temp_label: str = ""
    gpu: float = nan
    gpu_temp: float = nan
    ram: tuple = (nan, nan)
    vram: tuple = (nan, nan)
    disk: float = nan
    ip: str = "—"
    ssh: str = "—"
    rx: float = nan
    tx: float = nan
    history: tuple = ()
    cpu_model: str | None = None
    gpu_model: str | None = None


class CiclopeCombo(CiclopeLocalIPv4):
    palettes = {
        "intel-nvidia": ("#00d5f5", "#a9fa00"),
        "intel-radeon": ("#00d5f5", "#ff324a"),
        "ryzen-nvidia": ("#ff9c00", "#a9fa00"),
        "ryzen-radeon": ("#ffad00", "#ff324a"),
        "matrix": ("#00ff48", "#00ff48"),
        "anaheim": ("#00efa6", "#ff2345"),
        "visor": ("#00dfff", "#ffbd00"),
        "hulkbuster": ("#57efff", "#ffc000"),
        "jarvis": ("#00ddfa", "#ffb900"),
        "cerebro-gold": ("#ffd000", "#00d6f5"),
        "dark-phoenix": ("#ffc000", "#ff244e"),
        "phoenix-force": ("#ffe879", "#00e6a0"),
        "msdos": ("#ffff57", "#5bffff"),
        "windows95": ("#00ff00", "#00ffff"),
    }
    network_history = deque(maxlen=36)
    network_sample = None
    model_positions = {
        # CPU x/y/anchor, GPU x/y/anchor, available width, font size.
        "intel-nvidia": ((45, 51, "lt"), (435, 51, "rt"), 176, 10),
        "intel-radeon": ((45, 51, "lt"), (435, 51, "rt"), 176, 10),
        "ryzen-nvidia": ((120, 37, "mt"), (360, 37, "mt"), 180, 10),
        "ryzen-radeon": ((46, 45, "lt"), (433, 45, "rt"), 150, 10),
        "matrix": ((40, 49, "lt"), (155, 49, "lt"), 74, 9),
        "anaheim": ((18, 53, "lt"), (462, 53, "rt"), 120, 10),
        "visor": ((116, 209, "mt"), (364, 209, "mt"), 180, 10),
        "hulkbuster": ((47, 57, "lt"), (281, 57, "lt"), 176, 10),
        "jarvis": ((274, 54, "lt"), (274, 114, "lt"), 126, 10),
        "cerebro-gold": ((46, 45, "lt"), (433, 45, "rt"), 150, 10),
        "dark-phoenix": ((46, 45, "lt"), (433, 45, "rt"), 150, 10),
        "phoenix-force": ((46, 45, "lt"), (433, 45, "rt"), 150, 10),
        "msdos": ((56, 34, "lt"), (296, 34, "lt"), 155, 10),
        "windows95": ((52, 55, "lt"), (284, 55, "lt"), 107, 12),
    }

    @staticmethod
    def fit_model(model, font, width):
        value = model or "—"
        if font.getlength(value) <= width:
            return value
        value = value.removeprefix("GeForce ")
        if font.getlength(value) <= width:
            return value
        while font.getlength(value + "…") > width:
            value = value[:-1]
        return value + "…"

    @staticmethod
    def centered_text(draw, x, center_y, value, font, color, anchor):
        if not value:
            return
        mask, (_, offset_y) = font.getmask2(value, anchor=anchor)
        bounds = mask.getbbox()
        if bounds:
            _, top, _, bottom = bounds
            y = round(center_y - offset_y - (top + bottom - 1) / 2)
            draw.text((x, y), value, font=font, fill=color, anchor=anchor)

    @classmethod
    def render(cls, background, font_path, layout, data):
        from PIL import ImageColor, ImageDraw, ImageFont

        image = background.copy()
        draw = ImageDraw.Draw(image)
        cpu_color, gpu_color = cls.palettes[layout]
        white = "#d9ffea" if layout == "matrix" else "#f5f8ff"

        def label(x, y, value, size, color=white, anchor="lt"):
            draw.text((x, y), value, font=ImageFont.truetype(font_path, size),
                      fill=color, anchor=anchor)

        def centered(x, y, value, size, color=white, anchor="lt", width=None):
            font = ImageFont.truetype(font_path, size)
            while width and font.getlength(value) > width and size > 8:
                size -= 1
                font = ImageFont.truetype(font_path, size)
            cls.centered_text(draw, x, y, value, font, color, anchor)

        if data.cpu_model is not None or data.gpu_model is not None:
            cpu_pos, gpu_pos, width, size = cls.model_positions[layout]
            font = ImageFont.truetype(font_path, size if layout == "windows95" else size + 1)
            for model, (x, y, anchor), color in (
                (data.cpu_model, cpu_pos, cpu_color), (data.gpu_model, gpu_pos, gpu_color),
            ):
                value = cls.fit_model(model, font, width)
                if layout in {"msdos", "windows95"}:
                    color = "#000000"
                cls.centered_text(draw, x, y + size // 2, value, font, color, anchor)

        def percent(value):
            return f"{value:.0f}%" if isfinite(value) else "—"

        def temperature(value):
            return f"{value:.0f} °C" if isfinite(value) else "—"

        def memory(values):
            used, total = values
            if not all(map(isfinite, values)) or used < 0 or total <= 0:
                return "—", nan
            return f"{used:.1f} / {total:.1f} GB".replace(".", ","), 100 * used / total

        def count(value, segments):
            return round(segments * min(100, max(0, value)) / 100) if isfinite(value) else 0

        def bar(x, y, value, color, segments, pitch, width, height, slant=0, vertical=False):
            for i in range(count(value, segments)):
                left = round(x if vertical else x + pitch * i)
                top = round(y - pitch * i if vertical else y)
                right, bottom = left + width - 1, top + height - 1
                draw.polygon(((left + max(slant, 0), top), (right + max(slant, 0), top),
                              (right + max(-slant, 0), bottom), (left + max(-slant, 0), bottom)),
                             fill=color)

        def dial(x, y, value, color, radius, start, sweep, segments, width, gradient=None):
            step = sweep / segments
            if gradient:
                start_rgb = ImageColor.getrgb(gradient[0])
                end_rgb = ImageColor.getrgb(color)
            for i in range(count(value, segments)):
                angle = start + i * step
                fill = color
                if gradient:
                    ratio = min(1, i / (segments * gradient[1]))
                    fill = tuple(round(a + (b-a)*ratio) for a, b in zip(start_rgb, end_rgb))
                draw.arc((x-radius, y-radius, x+radius, y+radius),
                         angle, angle + step * .8, fill=fill, width=width)

        def rate(value):
            if not isfinite(value):
                return "—"
            for scale, unit in ((1e9, "GB/s"), (1e6, "MB/s"), (1e3, "kB/s")):
                if value >= scale:
                    return f"{value/scale:.1f} {unit}".replace(".", ",")
            return f"{value:.0f} B/s"

        themed_armor = layout in {"anaheim", "visor", "hulkbuster", "jarvis"}
        xmen = layout in {"cerebro-gold", "dark-phoenix", "phoenix-force"}
        retro = layout in {"msdos", "windows95"}
        if not retro:
            ip_left, ip_right = (54, 154) if layout == "matrix" else (34, 126)
            header_y = 19 if layout == "matrix" else 11
            centered((ip_left + ip_right) // 2, header_y, data.ip, 12,
                     anchor="mt", width=ip_right-ip_left)
            centered(466, header_y, data.ssh, 12,
                     cpu_color if themed_armor else gpu_color, "rt", 72)
        ram, ram_pct = memory(data.ram)
        vram, vram_pct = memory(data.vram)
        rx, tx = "RX " + rate(data.rx), "TX " + rate(data.tx)

        if layout == "msdos":
            centered(94, 10, data.ip, 13, anchor="mt", width=116)
            centered(472, 10, data.ssh, 12, cpu_color, "rt", 68)
            for x, usage, temp, color in (
                (24, data.cpu, data.cpu_temp, cpu_color), (264, data.gpu, data.gpu_temp, gpu_color),
            ):
                label(x, 70, percent(usage), 35, color)
                label(x+192, 86, temperature(temp), 20, "#59ff55", "rt")
                bar(x+3, 145, usage, color, 16, 11.75, 9, 18)
            label(216, 109, data.cpu_temp_label, 9, "#59ff55", "rt")
            label(216, 214, ram, 13, anchor="rt")
            label(264, 214, vram, 13)
            bar(27, 247, ram_pct, cpu_color, 14, 13.4, 11, 16)
            bar(267, 247, vram_pct, gpu_color, 14, 13.4, 11, 16)
            label(92, 299, percent(data.disk), 11)
            bar(137, 303, data.disk, cpu_color, 8, 8.4, 6, 7)
            centered(469, 306, rx + "  " + tx, 12, "#000000", "rt", 222)
        elif layout == "windows95":
            black, navy = "#000000", "#000080"
            centered(232, 42, data.ip, 14, black, "mt", 142)
            centered(463, 42, data.ssh, 14, navy, "rt", 116)
            for x, usage, temp, color in (
                (32, data.cpu, data.cpu_temp, cpu_color), (264, data.gpu, data.gpu_temp, gpu_color),
            ):
                label(x, 86, percent(usage), 32, color)
                label(x+186, 98, temperature(temp), 19, "#ffff00", "rt")
                bar(x-6, 162, usage, navy, 16, 12.35, 10, 18)
            label(218, 121, data.cpu_temp_label, 9, "#ffff00", "rt")
            label(220, 218, ram, 12, black, "rt")
            label(259, 218, vram, 12, black)
            bar(26, 249, ram_pct, navy, 14, 14, 12, 16)
            bar(258, 249, vram_pct, navy, 14, 14, 12, 16)
            label(134, 298, percent(data.disk), 10, navy)
            bar(176, 302, data.disk, navy, 8, 7, 5, 7)
            centered(469, 305, rx + "  " + tx, 12, black, "rt", 218)
        elif xmen:
            cpu_gradient = {"dark-phoenix": ("#ed0639", .54),
                            "phoenix-force": ("#ffb600", .54)}.get(layout)
            gpu_gradient = None
            if layout == "dark-phoenix":
                # Dark Phoenix heats from red at the base to orange at the leading edge.
                gpu_gradient = (gpu_color, .30)
            for x, usage, temp, color, gradient in (
                (106, data.cpu, data.cpu_temp, cpu_color, cpu_gradient),
                (374, data.gpu, data.gpu_temp, gpu_color, gpu_gradient),
            ):
                tip = "#ff6800" if x == 374 and gpu_gradient else color
                dial(x, 124, usage, tip, 52, 150, 240, 26, 13, gradient)
                label(x, 120, percent(usage), 26, color, "mm")
                label(x, 169, temperature(temp), 17, anchor="mm")
            label(106, 181, data.cpu_temp_label, 8, cpu_color, "mt")
            label(222, 212, ram, 14, anchor="rt")
            label(258, 212, vram, 14)
            ram_color = {"dark-phoenix": "#ff6000", "phoenix-force": "#ffb600"}.get(layout, cpu_color)
            bar(18, 250, ram_pct, ram_color, 14, 14.8, 11, 20, 5)
            bar(254, 250, vram_pct, gpu_color, 14, 14.8, 11, 20, -5)
        elif layout == "anaheim":
            for x, usage in ((102, data.cpu), (378, data.gpu)):
                label(x, 112, percent(usage), 41, anchor="mm")
            label(184, 51, temperature(data.cpu_temp), 14, "#ffc000", "rt")
            label(296, 51, temperature(data.gpu_temp), 14, "#ffc000")
            label(184, 68, data.cpu_temp_label, 9, cpu_color, "rt")
            bar(20, 162, data.cpu, cpu_color, 14, 12.2, 9, 22, 5)
            bar(291, 162, data.gpu, gpu_color, 14, 12.2, 9, 22, -5)
            for x, temp, color in ((214, data.cpu_temp, cpu_color), (246, data.gpu_temp, gpu_color)):
                bar(x, 153, temp, color, 14, 7, 20, 5, vertical=True)
                label(x+10, 169, f"{temp:.0f}°" if isfinite(temp) else "—", 12, color, "mt")
            label(186, 231, ram, 11, anchor="rt")
            label(294, 231, vram, 11)
            bar(18, 254, ram_pct, cpu_color, 14, 12.2, 9, 16, 4)
            bar(290, 254, vram_pct, "#ffb900", 14, 12.2, 9, 16, -4)
            label(240, 242, percent(data.disk), 17, anchor="mm")
            bar(210, 262, data.disk, "#ffb900", 8, 7.7, 6, 10)
        elif layout == "visor":
            for x, usage, temp, color, inner in (
                (116, data.cpu, data.cpu_temp, cpu_color, "#00bfff"),
                (364, data.gpu, data.gpu_temp, gpu_color, "#ff224d"),
            ):
                dial(x, 150, usage, color, 83, 150, 240, 42, 10)
                if isfinite(usage) and usage > 0:
                    draw.arc((x-65, 85, x+65, 215), 150, 150 + min(100, usage)*2.4,
                             fill=inner, width=5)
                label(x, 147, percent(usage), 30, anchor="mm")
                label(x, 185, temperature(temp), 20, color, "mm")
            label(116, 197, data.cpu_temp_label, 8, cpu_color, "mt")
            label(194, 240, ram, 12, anchor="rt")
            label(286, 240, vram, 12)
            bar(18, 260, ram_pct, cpu_color, 16, 11.2, 8, 8, 3)
            bar(283, 260, vram_pct, gpu_color, 16, 11.2, 8, 8, -3)
        elif layout == "hulkbuster":
            for x, usage, temp, color in (
                (21, data.cpu, data.cpu_temp, cpu_color), (255, data.gpu, data.gpu_temp, gpu_color),
            ):
                label(x, 82, percent(usage), 36, color)
                label(x+202, 101, temperature(temp), 21, anchor="rt")
                bar(x+1, 152, usage, color, 16, 12.7, 10, 20, 6)
            label(223, 125, data.cpu_temp_label, 9, cpu_color, "rt")
            for x, value, usage, color in ((22, ram, ram_pct, cpu_color), (256, vram, vram_pct, gpu_color)):
                label(x+200, 206, value, 12, anchor="rt")
                bar(x, 250, usage, color, 20, 10.1, 8, 12)
        elif layout == "jarvis":
            dial(118, 162, data.cpu, cpu_color, 93, -90, 360, 60, 10)
            dial(118, 162, data.gpu, gpu_color, 74, -90, 360, 60, 9)
            label(118, 141, percent(data.cpu), 23, anchor="mm")
            label(118, 175, percent(data.gpu), 21, gpu_color, "mm")
            for y, usage, temp, color in (
                (61, data.cpu, data.cpu_temp, cpu_color), (121, data.gpu, data.gpu_temp, gpu_color),
            ):
                label(458, y, temperature(temp), 21, anchor="rt")
                bar(248, y+17, usage, color, 14, 9, 7, 10)
            label(458, 84, data.cpu_temp_label, 8, cpu_color, "rt")
            for y, value, usage, color in ((174, ram, ram_pct, cpu_color), (222, vram, vram_pct, gpu_color)):
                label(458, y, value, 12, anchor="rt")
                bar(246, y+24, usage, color, 18, 11.85, 9, 12, 4)
        elif layout == "matrix":
            for x, usage, temp, tag in ((16, data.cpu, data.cpu_temp, data.cpu_temp_label),
                                        (132, data.gpu, data.gpu_temp, "")):
                label(x, 90, percent(usage), 25)
                label(x, 145, temperature(temp), 18, cpu_color)
                label(x, 166, tag, 9, cpu_color)
                bar(x+68, 262, usage, cpu_color, 20, 10.1, 26, 8, vertical=True)
            for y, value, usage in ((50, ram, ram_pct), (102, vram, vram_pct)):
                label(462, y, value, 12, anchor="rt")
                bar(250, y+26, usage, cpu_color, 20, 10.7, 9, 14)
            centered(250, 178, rx, 12, width=101)
            centered(462, 178, tx, 12, gpu_color, "rt", 101)
            peak = max((value for pair in data.history for value in pair if isfinite(value)), default=0)
            for channel, color in enumerate((cpu_color, "#009d37")):
                previous = None
                for i, pair in enumerate(data.history):
                    value = pair[channel]
                    if not isfinite(value):
                        previous = None
                        continue
                    point = (462 - (len(data.history)-1-i)*6, 268 - round(74*value/max(peak, 1)))
                    if previous is not None:
                        draw.line((previous, point), fill=color, width=2 if channel == 0 else 1)
                    previous = point
        else:
            if layout.startswith("intel"):
                label(18, 76, percent(data.cpu), 36, cpu_color)
                label(258, 76, percent(data.gpu), 36, gpu_color)
                label(222, 95, temperature(data.cpu_temp), 21, anchor="rt")
                label(462, 95, temperature(data.gpu_temp), 21, anchor="rt")
                label(222, 119, data.cpu_temp_label, 9, cpu_color, "rt")
                bar(18, 154, data.cpu, cpu_color, 16, 12.9, 11, 18)
                bar(258 if layout.endswith("nvidia") else 253, 154, data.gpu, gpu_color,
                    16, 12.9, 10, 18, 5 if layout.endswith("nvidia") else -5)
            else:
                full = layout == "ryzen-nvidia"
                centers = (120, 360) if full else (106, 374)
                for x, usage, temp, color, tag in (
                    (centers[0], data.cpu, data.cpu_temp, cpu_color, data.cpu_temp_label),
                    (centers[1], data.gpu, data.gpu_temp, gpu_color, ""),
                ):
                    dial_color = "#ff6500" if x == centers[0] else color
                    dial(x, 122 if full else 126, usage, dial_color, 69 if full else 58,
                         -90 if full else 145, 360 if full else 250, 60 if full else 44, 9)
                    label(x, 112 if full else 123, percent(usage), 27, color, "mm")
                    label(x, 142 if full else 168, temperature(temp), 16, anchor="mm")
                    label(x, 159 if full else 181, tag, 9, color, "mt")
                if not full:
                    label(240, 79, "CPU", 8, cpu_color, "mt")
                    label(240, 91, temperature(data.cpu_temp).replace(" °C", "°C"), 11, anchor="mt")
                    label(240, 131, "GPU", 8, gpu_color, "mt")
                    label(240, 143, temperature(data.gpu_temp).replace(" °C", "°C"), 11, anchor="mt")
            intel = layout.startswith("intel")
            label(222, 204 if intel else 211, ram, 12, anchor="rt")
            label(258, 204 if intel else 211, vram, 12)
            bar(18, 248 if intel else 250, ram_pct, cpu_color if intel else "#ff6500",
                16, 12.9, 11 if intel else 10,
                16 if intel else 18, 0 if intel else 5)
            bar(258 if layout == "intel-nvidia" else 253, 248 if intel else 250, vram_pct, gpu_color,
                16, 12.9, 10, 16 if intel else 18, 5 if layout == "intel-nvidia" else -5)
        if retro:
            return image
        footer_y = 296 if xmen or layout in {"anaheim", "jarvis"} else 294
        label(68, footer_y, percent(data.disk), 11)
        if layout != "anaheim":
            disk_color = gpu_color if layout in {"ryzen-radeon", "hulkbuster"} else cpu_color
            if xmen:
                disk_color = "#ffc000"
            bar(114 if layout == "matrix" else 108, 296 if layout == "matrix" else 299,
                data.disk, disk_color, 12 if layout == "matrix" else 10,
                8.1 if layout == "matrix" else 7.7, 6,
                11 if xmen else 5 if layout in {"visor", "jarvis"} else 8)
        network_color = "#ffc000" if layout == "anaheim" else white
        if not themed_armor:
            network_color = cpu_color if layout == "ryzen-radeon" else gpu_color
        centered(466, footer_y + 6, rx + "  " + tx, 12, network_color, "rt", 226)
        return image

    def read_network(self, interface):
        from time import monotonic

        import psutil

        cls = type(self)
        try:
            counters = (psutil.net_io_counters(pernic=True) or {}).get(interface)
        except (OSError, RuntimeError):
            counters = None
        previous = cls.network_sample
        now = monotonic()
        cls.network_sample = (interface, now, counters) if counters else None
        rx, tx = nan, nan
        if counters and previous and previous[0] == interface and now > previous[1]:
            elapsed = now - previous[1]
            received = counters.bytes_recv - previous[2].bytes_recv
            sent = counters.bytes_sent - previous[2].bytes_sent
            rx = received / elapsed if received >= 0 else nan
            tx = sent / elapsed if sent >= 0 else nan
        else:
            cls.network_history.clear()
        cls.network_history.append((rx, tx))
        return rx, tx

    @staticmethod
    @lru_cache(maxsize=1)
    def read_models():
        from pathlib import Path
        import re
        import shlex
        import subprocess
        from library.stats import sensors

        # Themes consume hardware names already exposed by Turing's native LHM backend.
        if hasattr(sensors, "get_hw_and_update"):
            hardware = sensors.get_hw_and_update(sensors.Hardware.HardwareType.Cpu)
            cpu = str(hardware.Name) if hardware is not None else ""
            return cpu, sensors.Gpu.gpu_name

        def command(*args):
            try:
                return subprocess.check_output(args, text=True, timeout=2,
                                               stderr=subprocess.DEVNULL).strip()
            except (OSError, subprocess.SubprocessError):
                return ""

        cpu = gpu = ""
        try:
            for line in Path("/proc/cpuinfo").read_text().splitlines():
                key, _, value = line.partition(":")
                if key.strip() == "model name":
                    cpu = re.sub(r"\s+\d+-Core Processor$|\s+@.*$", "", value.strip())
                    cpu = cpu.removeprefix("AMD ").replace("(R)", "").replace("(TM)", "")
                    break
        except OSError:
            pass

        models = set()
        for info in Path("/proc/driver/nvidia/gpus").glob("*/information"):
            try:
                for line in info.read_text().splitlines():
                    if line.startswith("Model:"):
                        models.add(line.partition(":")[2].strip().removeprefix("NVIDIA "))
            except OSError:
                continue
        if not models:
            models = {name.strip().removeprefix("NVIDIA ") for name in
                      command("nvidia-smi", "--query-gpu=name", "--format=csv,noheader").splitlines()
                      if name.strip()}
        if len(models) == 1:
            gpu = models.pop()
        elif not models:
            amd_models = set()
            for card in Path("/sys/class/drm").glob("card[0-9]*"):
                device = card / "device"
                try:
                    if (device / "vendor").read_text().strip() != "0x1002":
                        continue
                except OSError:
                    continue
                fields = shlex.split(command("lspci", "-s", device.resolve().name, "-mm"))
                if len(fields) >= 4:
                    model = fields[3]
                    if "[" in model and model.endswith("]"):
                        model = model.rsplit("[", 1)[1][:-1]
                    amd_models.add(model)
            if len(amd_models) == 1:
                gpu = amd_models.pop()
        return cpu, gpu

    def as_string(self):
        from pathlib import Path

        import psutil

        from library import config
        from library.display import display
        from library.stats import sensors

        theme = config.THEME_DATA
        layout = theme["combo"]["layout"]
        try:
            ip = super().as_string()
        except (OSError, RuntimeError):
            ip = "—"
        ssh = CiclopeSshUsers().as_string().removeprefix("SSH: ")
        if ssh in {"sem sessão", "indisponível"}:
            ssh = "—"
        elif len(ssh) > 10:
            ssh = ssh[:9] + "…"
        cpu = sensors.Cpu.percentage(0.15)
        try:
            temperatures = psutil.sensors_temperatures()
        except (AttributeError, OSError):
            temperatures = {}
        tctl = next((entry.current for driver in ("k10temp", "zenpower")
                     for entry in temperatures.get(driver, ())
                     if entry.label.casefold() == "tctl" and isfinite(entry.current)), None)
        try:
            gpu, _, used, total, gpu_temp = sensors.Gpu.stats()
        except (OSError, RuntimeError, IndexError):
            gpu = used = total = gpu_temp = nan
        ram = psutil.virtual_memory()
        rx, tx = self.read_network(config.CONFIG_DATA["config"].get("ETH", ""))
        cpu_model, gpu_model = self.read_models() if theme["combo"].get("show_hardware_models") else (None, None)
        data = CiclopeReadings(
            cpu=cpu, cpu_temp=sensors.Cpu.temperature() if tctl is None else tctl,
            cpu_temp_label="" if tctl is None else "TCTL", gpu=gpu, gpu_temp=gpu_temp,
            ram=((ram.total-ram.available)/1e9, ram.total/1e9),
            vram=(used*2**20/1e9, total*2**20/1e9), disk=sensors.Disk.disk_usage_percent(),
            ip="—" if ip == "Sem IPv4" else ip, ssh=ssh, rx=rx, tx=tx,
            history=tuple(self.network_history),
            cpu_model=cpu_model, gpu_model=gpu_model,
        )
        font = Path(config.FONTS_DIR) / "jetbrains-mono/JetBrainsMono-Bold.ttf"
        background = display.lcd.open_image(str(Path(theme["PATH"]) / "background.png"))
        display.lcd.DisplayPILImage(self.render(background, str(font), layout, data))
        return ""


class CiclopeHardwareModels(CiclopeLocalIPv4):
    _last_linux_models = None

    @staticmethod
    def render(background, font_path, fields, models):
        from PIL import ImageDraw, ImageFont

        for (x, y, width, size, color, alignment), model in zip(fields, models):
            tile = background.crop((x, y, x + width, y + size + 2))
            font = ImageFont.truetype(font_path, size + 1)
            anchor_x = {"lt": 0, "mt": width // 2, "rt": width - 1}[alignment]
            value = CiclopeCombo.fit_model(model, font, width - 1)
            CiclopeCombo.centered_text(ImageDraw.Draw(tile), anchor_x, (size + 1) / 2,
                                       value, font, color, alignment)
            yield tile, x, y

    def as_string(self):
        from pathlib import Path

        from library import config
        from library.display import display

        theme = config.THEME_DATA
        theme_path = Path(theme["PATH"])
        models = CiclopeCombo.read_models()
        # On CICLOPE-Linux the labels occupy an area separate from all periodic draws.
        cache_labels = theme_path.name == "CICLOPE-Linux"
        cls = type(self)
        if cache_labels and cls._last_linux_models == models:
            return ""

        font = Path(config.FONTS_DIR) / "jetbrains-mono/JetBrainsMono-Bold.ttf"
        background = display.lcd.open_image(str(theme_path / "background.png"))
        for tile, x, y in self.render(background, str(font), theme["hardware_models"], models):
            display.lcd.DisplayPILImage(tile, x, y)
        if cache_labels:
            cls._last_linux_models = models
        return ""
# END CICLOPE CUSTOM SENSORS
