"""Собирает зимнюю иконку из логотипа: градиент, снежинки и белая лиса-R."""
from PIL import Image, ImageDraw, ImageFilter
import random, os, math

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "desktop/src/main/resources/icon.png")

im = Image.open(SRC).convert("RGBA")
w, h = im.size
# Если исходник уже зимний (синий угол), повторно не перекрашиваем.
_r, _g, _b, _a = im.getpixel((w // 2, 6))
if _a > 200 and _b > _r + 20:
    print("winter icon already generated")
    raise SystemExit(0)
logo_main = Image.new("L", (w, h), 0)
logo_acc = Image.new("L", (w, h), 0)
for y in range(h):
    for x in range(w):
        r, g, b, a = im.getpixel((x, y))
        if a < 20:
            continue
        mx, mn = max(r, g, b), min(r, g, b)
        if mx - mn < 30:
            continue
        if r > 120 and g < 165 and b > 195:
            logo_acc.putpixel((x, y), 255)
        else:
            logo_main.putpixel((x, y), 255)


def gradient(size):
    top, bottom = (10, 24, 45), (50, 92, 126)
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x * 0.35 + y * 0.65) / size
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return img.convert("RGBA")


def make(size=1024, rounded=True, maskable=False, logo_scale=0.62):
    img = gradient(size)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    cx, cy, rr = int(size * 0.78), int(size * 0.2), int(size * 0.28)
    gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 255, 255, 42))
    glow = glow.filter(ImageFilter.GaussianBlur(size // 22))
    img = Image.alpha_composite(img, glow)

    flakes = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fd = ImageDraw.Draw(flakes)
    rnd = random.Random(20251201)
    for i in range(90):
        x = rnd.random() * size
        y = rnd.random() * size
        r = rnd.uniform(size * 0.002, size * 0.009)
        alpha = rnd.randint(70, 225)
        fd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, alpha))
        if rnd.random() < 0.18:
            arm = r * 2.6
            for ang in (0, 60, 120):
                dx, dy = math.cos(math.radians(ang)) * arm, math.sin(math.radians(ang)) * arm
                fd.line([x - dx, y - dy, x + dx, y + dy], fill=(255, 255, 255, min(210, alpha + 20)), width=max(1, int(size * 0.002)))

    drift = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dd = ImageDraw.Draw(drift)
    dd.ellipse([-size * 0.3, size * 0.82, size * 0.75, size * 1.35], fill=(255, 255, 255, 46))
    dd.ellipse([size * 0.35, size * 0.88, size * 1.3, size * 1.4], fill=(255, 255, 255, 32))
    drift = drift.filter(ImageFilter.GaussianBlur(size // 45))
    img = Image.alpha_composite(img, flakes)
    img = Image.alpha_composite(img, drift)

    scale = logo_scale if not maskable else 0.5
    lw = int(size * scale)
    main = logo_main.resize((lw, lw), Image.LANCZOS)
    acc = logo_acc.resize((lw, lw), Image.LANCZOS)
    layer = Image.new("RGBA", (lw, lw), (0, 0, 0, 0))
    layer.paste(Image.new("RGBA", (lw, lw), (255, 255, 255, 255)), (0, 0), main)
    layer.paste(Image.new("RGBA", (lw, lw), (191, 232, 255, 255)), (0, 0), acc)
    img.alpha_composite(layer, (int((size - lw) / 2), int((size - lw) / 2)))

    if rounded and not maskable:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    return img


out = os.path.join(ROOT, "website/public")
app = os.path.join(out, "app")
os.makedirs(app, exist_ok=True)

big = make(1024)
big.save(os.path.join(out, "winter-icon.png"))
big.save(os.path.join(ROOT, "desktop/src/main/resources/icon.png"))
big.resize((512, 512), Image.LANCZOS).save(os.path.join(app, "icon-512.png"))
big.resize((192, 192), Image.LANCZOS).save(os.path.join(app, "icon-192.png"))
make(512, maskable=True).save(os.path.join(app, "icon-maskable-512.png"))
big.resize((180, 180), Image.LANCZOS).save(os.path.join(out, "apple-touch-icon.png"))
big.resize((64, 64), Image.LANCZOS).save(os.path.join(out, "favicon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64)])
big.save(os.path.join(ROOT, "desktop/icon.ico"), format="ICO", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("icons done")
