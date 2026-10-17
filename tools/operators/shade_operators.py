"""
Operator redesign, take 2 (owner: "more closely trace the original operator design then add in
the nice shading and color"). Starts from the real in-game figures (rendered large by the
OperatorRawRenders test into docs/operators_concept/raw) and shades them in the pets' style:

  * every flat panel is filled with a richer tone of its own colour and gets a rounded,
    lit volume (inflated from its outline) with a soft specular highlight;
  * the dark visor plates stay near-black but glossy;
  * the neon trim lines and the >_< faces stay bright and gain a glow;
  * a dark outline around the whole figure, like the pets.

Run after:  ./gradlew :app:testDebugUnitTest --tests '*OperatorRawRenders' -PrenderPreviews
Then:       python3 tools/operators/shade_operators.py   ->  docs/operators_concept/v2_*.png
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW = os.path.join(ROOT, "docs", "operators_concept", "raw")
OUT = os.path.join(ROOT, "docs", "operators_concept")

LIGHT = np.array([-0.55, -0.7, 0.55]); LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0]); HALF /= np.linalg.norm(HALF)


def shade(path, flat_cape=None):
    im = np.asarray(Image.open(path).convert("RGBA")).astype(np.float32) / 255.0
    rgb, a = im[..., :3], im[..., 3]
    H, W = a.shape
    solid = a > 0.55                     # the figure (auras / ground glow are fainter)
    faint = (a > 0.02) & ~solid          # kept as a soft glow layer
    mx = rgb.max(-1); mn = rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    line = solid & (mx > 0.55) & ((sat > 0.35) | (mx > 0.85))     # neon trim / faces / lights
    fill = solid & ~line

    labels, n = ndimage.label(fill, structure=np.ones((3, 3)))
    gain = np.zeros(n + 1, np.float32)   # brightness lift per panel (pixels keep their relative shading)
    kind = np.zeros(n + 1, np.int8)      # 0 body panel, 1 visor plate
    idx = np.arange(1, n + 1)
    med = np.stack([ndimage.median(rgb[..., c], labels, idx) for c in range(3)], -1) if n else np.zeros((0, 3))
    total = max(int(solid.sum()), 1)
    objs = ndimage.find_objects(labels)
    for i, col in enumerate(med, start=1):
        v = max(col.max(), 1e-3)
        sl = objs[i - 1]
        reg = labels[sl] == i
        holes = ndimage.binary_fill_holes(reg) & ~reg
        enclosed = (holes & line[sl]).sum() / max(reg.sum(), 1)
        if v < 0.2 and enclosed > 0.03 and reg.sum() < 0.08 * total:
            kind[i] = 1; gain[i] = 0.09 / v          # visor plate round a face: stays near-black
        elif v < 0.12:
            gain[i] = 0.22 / v                        # the dark armour: a deep tinted tone
        else:
            gain[i] = 0.48 / v                        # coloured panels: a muted mid-tone (owner: "a little too colorful")
    px = rgb * gain[labels][..., None]
    grey = px.mean(-1, keepdims=True)
    base_px = np.clip(grey + (px - grey) * 0.85, 0, 1)   # slightly less saturated than the panel colour
    # rounded volumes: a soft bevel at every panel edge plus a broad dome over each panel
    fm = fill.astype(np.float32)
    height = (0.55 * ndimage.gaussian_filter(fm, 2.5) + 0.45 * ndimage.gaussian_filter(fm, 9)) * fm
    hs = ndimage.gaussian_filter(height, 1.0) * 26.0
    gy, gx = np.gradient(hs)
    nrm = np.dstack([-gx, -gy, np.ones_like(hs)])
    nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True)
    diff = np.clip(nrm @ LIGHT, 0, 1)
    spec = np.clip(nrm @ HALF, 0, 1) ** 36
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    ys, xs = np.nonzero(solid)
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    fall = 1.0 - 0.3 * np.clip(((yy - y0) / max(y1 - y0, 1) * 0.7 + (xx - x0) / max(x1 - x0, 1) * 0.3), 0, 1)
    ao = np.clip(ndimage.gaussian_filter(fm, 2.0) * 1.6, 0, 1) * 0.5 + 0.5
    base = base_px
    visor = (kind[labels] == 1) & fill
    lit = base * (0.42 + 0.75 * diff)[..., None] * fall[..., None] * ao[..., None]
    lit = lit + (spec * np.where(visor, 0.45, 0.5))[..., None]
    out = np.where(fill[..., None], lit, 0)
    # The Phantom's cape and hood stay as they were, unshaded (owner, after the v2 sheets):
    # its two biggest panels.
    if flat_cape is None:
        flat_cape = os.path.basename(path).startswith("phantom")
    if flat_cape and n:
        sizes = ndimage.sum(fill, labels, idx)
        flat = np.isin(labels, np.argsort(-sizes)[:2] + 1)
        out = np.where(flat[..., None], rgb, out)

    lines = np.where(line[..., None], np.clip(rgb * 1.12 + 0.05, 0, 1), 0)
    out = np.where(line[..., None], lines, out)
    glow = np.asarray(Image.fromarray((lines * 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(5)), np.float32) / 255.0

    img = Image.fromarray(np.dstack([np.clip(out, 0, 1) * 255, solid * 255.0]).astype(np.uint8), "RGBA")
    sil = Image.fromarray((solid * 255).astype(np.uint8), "L").filter(ImageFilter.MaxFilter(5))
    edge = Image.new("RGBA", img.size, (6, 8, 14, 255)); edge.putalpha(sil)
    aura = Image.fromarray(np.dstack([rgb * 255, np.where(faint, a, 0) * 255 * 0.8]).astype(np.uint8), "RGBA").filter(ImageFilter.GaussianBlur(2))
    glow_img = Image.fromarray(np.dstack([glow * 255, np.clip(glow.max(-1) * 1.6, 0, 1) * 255 * 0.85]).astype(np.uint8), "RGBA")
    res = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for layer in (aura, glow_img, edge, img):
        res.alpha_composite(layer)
    top = glow_img.copy(); top.putalpha(top.split()[3].point(lambda v: int(v * 0.5)))
    res.alpha_composite(top)
    return res


def main():
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 32)
    except OSError:
        font = small = big = ImageFont.load_default()
    BG = (10, 13, 22, 255)
    classes = [("agent", "FIELD AGENT"), ("technician", "TECHNICIAN"), ("juggernaut", "JUGGERNAUT"), ("phantom", "PHANTOM"), ("mage", "PACKET MAGE")]
    skins = [("default", "DEFAULT", (35, 197, 94)), ("titanium", "TITANIUM", (230, 236, 242)), ("purple", "PURPLE", (162, 89, 255)), ("gold", "GOLD", (255, 200, 50)),
             ("red_hat", "RED", (255, 45, 85)), ("ids", "CYAN", (0, 229, 255))]
    cell = 260

    # 1. Before / after for every class (default skin).
    s = Image.new("RGBA", (260 + 2 * cell, 100 + len(classes) * cell), BG)
    d = ImageDraw.Draw(s)
    d.text((24, 20), "OPERATORS · ORIGINAL vs SHADED", fill=(0, 240, 255, 255), font=big)
    d.text((260 + 80, 66), "ORIGINAL", fill=(150, 160, 180, 255), font=small)
    d.text((260 + cell + 90, 66), "SHADED", fill=(0, 240, 255, 255), font=small)
    for r, (c, name) in enumerate(classes):
        y = 90 + r * cell
        d.text((24, y + cell // 2 - 12), name, fill=(230, 236, 245, 255), font=font)
        raw = Image.open(os.path.join(RAW, f"{c}_default.png")).convert("RGBA").resize((cell, cell), Image.LANCZOS)
        s.alpha_composite(raw, (260, y))
        s.alpha_composite(shade(os.path.join(RAW, f"{c}_default.png")).resize((cell, cell), Image.LANCZOS), (260 + cell, y))
    s.save(os.path.join(OUT, "v2_before_after.png"))

    # 2. Classes x skins, shaded.
    s = Image.new("RGBA", (260 + len(skins) * 210, 100 + len(classes) * 220), BG)
    d = ImageDraw.Draw(s)
    d.text((24, 20), "SHADED OPERATORS · CLASSES × SKINS", fill=(0, 240, 255, 255), font=big)
    for k, (_, label, col) in enumerate(skins):
        d.text((260 + k * 210 + 60, 66), label, fill=col + (255,), font=small)
    for r, (c, name) in enumerate(classes):
        y = 90 + r * 220
        d.text((24, y + 100), name, fill=(230, 236, 245, 255), font=font)
        for k, (sk, _, _) in enumerate(skins):
            s.alpha_composite(shade(os.path.join(RAW, f"{c}_{sk}.png")).resize((210, 210), Image.LANCZOS), (260 + k * 210, y))
    s.save(os.path.join(OUT, "v2_classes_skins.png"))

    # 3. Character creator options (Field Agent).
    s = Image.new("RGBA", (260 + 3 * 230, 100 + 3 * 230), BG)
    d = ImageDraw.Draw(s)
    d.text((24, 20), "SHADED · CREATOR OPTIONS (Field Agent)", fill=(0, 240, 255, 255), font=big)
    for r, part in enumerate(("head", "face", "legs")):
        y = 90 + r * 230
        d.text((24, y + 100), part.upper(), fill=(230, 236, 245, 255), font=font)
        for k in range(3):
            s.alpha_composite(shade(os.path.join(RAW, f"agent_{part}{k}.png")).resize((230, 230), Image.LANCZOS), (260 + k * 230, y))
    s.save(os.path.join(OUT, "v2_creator_options.png"))
    print("ok")


if __name__ == "__main__":
    main()
