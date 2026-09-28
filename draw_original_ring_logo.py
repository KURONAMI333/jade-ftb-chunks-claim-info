"""Draw the addon logo from geometric primitives; no upstream logo asset is read."""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


OUT = Path(__file__).with_name("jade-ftb-original-smooth-ring.png")
SIZE = 2048
SCALE = SIZE / 512

y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
u, v = x / (SIZE - 1), y / (SIZE - 1)
t = np.clip(0.15 + 0.48 * u + 0.42 * v, 0, 1)
gold = np.array([255, 210, 77], dtype=np.float32)
orange = np.array([229, 103, 30], dtype=np.float32)
rgb = gold[None, None, :] * (1 - t[:, :, None]) + orange[None, None, :] * t[:, :, None]

# A quiet warm glow gives the field depth without copying another logo's texture.
glow = np.exp(-(((u - 0.20) / 0.48) ** 2 + ((v - 0.16) / 0.50) ** 2))
rgb += glow[:, :, None] * np.array([5, 8, 4], dtype=np.float32)
rgb = np.uint8(np.clip(rgb, 0, 255))
base = Image.fromarray(rgb, mode="RGB").convert("RGBA")

cx = cy = SIZE // 2
outer = round(174 * SCALE)
inner = round(106 * SCALE)

ring_mask = Image.new("L", (SIZE, SIZE), 0)
draw = ImageDraw.Draw(ring_mask)
draw.ellipse((cx - outer, cy - outer, cx + outer, cy + outer), fill=255)
draw.ellipse((cx - inner, cy - inner, cx + inner, cy + inner), fill=0)

shadow = Image.new("L", (SIZE, SIZE), 0)
shadow.paste(ring_mask, (round(7 * SCALE), round(11 * SCALE)))
shadow = shadow.filter(ImageFilter.GaussianBlur(round(13 * SCALE)))
base.alpha_composite(Image.merge("RGBA", (*([Image.new("L", (SIZE, SIZE), 42)] * 3), shadow.point(lambda p: round(p * 0.34)))))

shade = np.clip((0.52 * u + 0.48 * v), 0, 1)
light = np.array([231, 255, 236], dtype=np.float32)
dark = np.array([178, 226, 192], dtype=np.float32)
ring_rgb = np.uint8(light[None, None, :] * (1 - shade[:, :, None]) + dark[None, None, :] * shade[:, :, None])
ring = Image.fromarray(ring_rgb, mode="RGB").convert("RGBA")
ring.putalpha(ring_mask)
base.alpha_composite(ring)

base = base.resize((512, 512), Image.Resampling.LANCZOS)
base.save(OUT)
base.resize((128, 128), Image.Resampling.LANCZOS).save(OUT.with_name("jade-ftb-original-smooth-ring-128.png"))
print(OUT)
