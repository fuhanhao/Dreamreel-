"""Generate the Dreamreel auth-page hero visual (no text, brand palette)."""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "apps" / "web" / "public" / "auth-hero.png"

W, H = 1200, 1600


def lerp(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def radial_glow(size: tuple[int, int], center: tuple[float, float], radius: float, color: tuple[int, int, int], alpha: int) -> Image.Image:
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 90
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * (1 - t)
        a = int(alpha * t * t)
        x0 = center[0] - r
        y0 = center[1] - r
        x1 = center[0] + r
        y1 = center[1] + r
        d.ellipse([x0, y0, x1, y1], fill=color + (a,))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.08))


def star_points(cx: float, cy: float, r_outer: float, r_inner: float, n: int = 4) -> list[tuple[float, float]]:
    pts = []
    for i in range(n * 2):
        r = r_outer if i % 2 == 0 else r_inner
        ang = -90 + i * (360 / (n * 2))
        pts.append((cx + r * math.cos(math.radians(ang)), cy + r * math.sin(math.radians(ang))))
    return pts


def draw_film_strip(d: ImageDraw.ImageDraw, x0: float, y0: float, width: float, height: float, angle: float, color: tuple[int, int, int, int]) -> None:
    pts = [
        (x0, y0),
        (x0 + width, y0),
        (x0 + width + height * math.tan(math.radians(angle)), y0 + height),
        (x0 + height * math.tan(math.radians(angle)), y0 + height),
    ]
    d.polygon(pts, fill=color)
    hole_w = 26
    hole_h = 18
    step = 64
    n = int((width - 40) // step)
    for i in range(n):
        hx = x0 + 34 + i * step
        hy = y0 + (height - hole_h) / 2
        d.rounded_rectangle([hx, hy, hx + hole_w, hy + hole_h], radius=4, fill=(0, 0, 0, 0))


def main() -> None:
    random.seed(20260821)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Base vertical gradient: deep ink-violet
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bd = ImageDraw.Draw(base)
    for y in range(H):
        t = y / (H - 1)
        c = lerp((18, 14, 31), (42, 24, 82), t)
        bd.line([(0, y), (W, y)], fill=c + (255,))
    img = base
    img.paste(radial_glow((W, H), (W * 0.18, H * 0.22), 620, (124, 58, 237), 150), (0, 0), radial_glow((W, H), (W * 0.18, H * 0.22), 620, (124, 58, 237), 150))
    img.paste(radial_glow((W, H), (W * 0.85, H * 0.75), 720, (217, 70, 239), 130), (0, 0), radial_glow((W, H), (W * 0.85, H * 0.75), 720, (217, 70, 239), 130))
    img.paste(radial_glow((W, H), (W * 0.5, H * 0.05), 500, (139, 92, 246), 90), (0, 0), radial_glow((W, H), (W * 0.5, H * 0.05), 500, (139, 92, 246), 90))

    d = ImageDraw.Draw(img, "RGBA")

    # Film strips (angled, translucent)
    draw_film_strip(d, -160, 260, 1500, 120, 4, (255, 255, 255, 18))
    draw_film_strip(d, -120, 1220, 1500, 150, -3, (255, 255, 255, 14))
    draw_film_strip(d, 560, -120, 120, 1900, 82, (255, 255, 255, 10))

    # Clapperboard silhouette (center, subtle white)
    cb_x, cb_y, cb_w, cb_h = 250, 560, 700, 460
    d.rounded_rectangle([cb_x, cb_y + 120, cb_x + cb_w, cb_y + cb_h], radius=28, fill=(255, 255, 255, 22))
    bar = [(cb_x + 10, cb_y), (cb_x + cb_w - 40, cb_y + 30), (cb_x + cb_w - 20, cb_y + 120), (cb_x + 30, cb_y + 130)]
    d.polygon(bar, fill=(255, 255, 255, 30))
    for i in range(4):
        x = cb_x + 90 + i * 150
        d.line([(x, cb_y + 26), (x + 78, cb_y + 126)], fill=(124, 58, 237, 120), width=22)
    d.line([(cb_x + 190, cb_y + 132), (cb_x + 190, cb_y + 150)], fill=(124, 58, 237, 140), width=18)
    d.line([(cb_x + 300, cb_y + 132), (cb_x + 300, cb_y + 150)], fill=(124, 58, 237, 140), width=18)
    for x0 in (cb_x + 120, cb_x + 300, cb_x + 480):
        d.rounded_rectangle([x0, cb_y + 250, x0 + 110, cb_y + 330], radius=14, fill=(124, 58, 237, 110))

    # Sparkles
    sparks = [
        (150, 170, 44, 24, (255, 255, 255, 230)),
        (980, 240, 30, 16, (232, 121, 249, 220)),
        (1040, 980, 54, 28, (255, 255, 255, 210)),
        (180, 1020, 24, 12, (232, 121, 249, 200)),
        (620, 1480, 40, 20, (255, 255, 255, 170)),
        (330, 1310, 18, 9, (255, 255, 255, 190)),
        (880, 430, 20, 10, (255, 255, 255, 180)),
    ]
    for cx, cy, ro, ri, col in sparks:
        d.polygon(star_points(cx, cy, ro, ri), fill=col)

    # Vignette
    vig = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    vd = ImageDraw.Draw(vig)
    for i in range(140, 0, -1):
        t = i / 140
        inset = int((1 - t) * 300)
        vd.rounded_rectangle([inset, inset, W - inset, H - inset], radius=120, outline=(0, 0, 0, int(90 * (1 - t) ** 2)))
    img.paste(vig, (0, 0), vig)

    # Subtle grain
    noise = Image.new("L", (W, H), 0)
    nd = ImageDraw.Draw(noise)
    for _ in range(70000):
        x = random.randint(0, W - 1)
        y = random.randint(0, H - 1)
        nd.point((x, y), fill=random.randint(0, 55))
    grain_a = noise.filter(ImageFilter.GaussianBlur(0.5))
    grain_img = Image.merge(
        "RGBA",
        (grain_a.point(lambda v: 8), grain_a.point(lambda v: 6), grain_a.point(lambda v: 16), grain_a),
    )
    img = Image.alpha_composite(img, grain_img)

    img.save(OUT)
    print(f"saved {OUT} {img.size}")


if __name__ == "__main__":
    main()
