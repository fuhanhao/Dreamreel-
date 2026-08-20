"""Generate Dreamreel brand icons (gradient clapperboard + spark)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
S = 256


def make_icon(size: int) -> Image.Image:
    # 1) gradient rounded square
    grad = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    top = (124, 58, 237)  # violet-600
    bottom = (217, 70, 239)  # fuchsia-500
    for y in range(S):
        t = y / (S - 1)
        c = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,)
        gd.line([(0, y), (S, y)], fill=c)

    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=58, fill=255)

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)

    # 2) clapperboard body
    body = (56, 112, 200, 194)
    d.rounded_rectangle(body, radius=12, fill=(255, 255, 255, 255))

    # 3) slanted top bar with violet stripes
    bar = [(58, 78), (190, 62), (200, 100), (68, 116)]
    d.polygon(bar, fill=(255, 255, 255, 255))
    # hinge line
    d.line([(68, 116), (200, 100)], fill=(217, 70, 239, 255), width=7)
    # diagonal stripes on top bar
    for x in range(88, 174, 24):
        d.line([(x, 66), (x + 28, 116)], fill=(124, 58, 237, 255), width=9)
    # clamp/legs
    d.line([(118, 100), (118, 112)], fill=(124, 58, 237, 255), width=10)
    d.line([(146, 100), (146, 112)], fill=(124, 58, 237, 255), width=10)

    # 4) two violet accents on body (filmstrip)
    for x0 in (86, 122, 158):
        d.rounded_rectangle([x0, 138, x0 + 20, 166], radius=5, fill=(124, 58, 237, 255))
    d.line([(80, 132), (180, 132)], fill=(124, 58, 237, 255), width=6)

    # 5) sparkle (4-point star) top-right
    cx, cy = 186, 58
    r_outer, r_inner = 30, 11
    pts = []
    for i in range(8):
        r = r_outer if i % 2 == 0 else r_inner
        ang = -90 + i * 45
        pts.append((cx + r * __import__("math").cos(__import__("math").radians(ang)),
                    cy + r * __import__("math").sin(__import__("math").radians(ang))))
    d.polygon(pts, fill=(255, 255, 255, 255))

    if size != S:
        img = img.resize((size, size), Image.LANCZOS)
    return img


def main() -> None:
    public = ROOT / "apps" / "web" / "public"
    app = ROOT / "apps" / "web" / "src" / "app"

    make_icon(256).save(public / "brand-icon.png")
    make_icon(32).save(public / "favicon-32x32.png")
    make_icon(16).save(public / "favicon-16x16.png")
    make_icon(256).save(app / "icon.png")
    make_icon(180).save(app / "apple-icon.png")

    ico = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    base = make_icon(48)
    ico.paste(base, (8, 8), base)
    ico.save(public / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    ico.save(app / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    print("icons generated")


if __name__ == "__main__":
    main()
