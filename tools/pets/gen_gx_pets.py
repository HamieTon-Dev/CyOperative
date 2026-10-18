"""GLITCHED ENCRYPTED pets (owner, 0.49.0: "make the 50 glitched encrypted pets and eggs").

50 animated pets plus their eggs. Each is drawn with the ENCRYPTED body set (tools/pets/gen_new_pets.py),
then split between cyan and red, torn into glitch slices, with a red / cyan colour fringe,
like the GLITCHED ENCRYPTED gear. Their moving parts use the "GX" colour (cyan <-> red, like
the gear) and a GLITCH part, which tears the sprite now and then in the game (PetArt).

They follow the 1,000 pets from gen_new_pets.py: new pet k = 1000..1049 → art NEW_BASE + k, so
page 7 (cells 232..255) and page 8. This script adds them to pets_7 / eggs_7, writes pets_8 /
eggs_8, and appends their lines to new_pets.txt. Run it AFTER gen_new_pets.py, which rewrites
new_pets.txt and pages 4-7.

Run: python3 tools/pets/gen_gx_pets.py
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_new_pets as g  # noqa: E402

COUNT = 50
FIRST = 1000            # new-pet index of the first one
CYAN = np.array((0, 240, 255), float)
RED = np.array((255, 43, 58), float)
WORDS = ["Split-Key", "Desync", "Redshift", "Bitrot", "Null-Hash", "Shardlock", "Paradox", "Dual-Phase", "Overclock", "Fracture"]
PALS = [  # body, trim, accent, eye: half red-bodied with cyan trim, half the other way round
    dict(body=(40, 44, 70), trim=(10, 10, 20), accent=(0, 240, 255), eye=(255, 60, 80)),
    dict(body=(170, 24, 48), trim=(50, 6, 16), accent=(0, 240, 255), eye=(200, 255, 255)),
    dict(body=(0, 150, 190), trim=(0, 40, 60), accent=(255, 43, 58), eye=(255, 220, 225)),
    dict(body=(26, 26, 36), trim=(6, 6, 10), accent=(255, 43, 58), eye=(0, 240, 255)),
]
KINDS = ["ORBS", "ORBS", "RING", "GEARS", "SHARDS", "HELIX", "SCAN", "PULSE", "PROP"]


def glitch(img, rnd, px):
    """The GX look on a sprite: cyan / red split shading, torn slices, chromatic fringe, scanlines."""
    a = np.asarray(img.convert("RGBA")).astype(float)
    rgb, al = a[..., :3], a[..., 3] / 255.0
    n = a.shape[0]
    lum = (rgb @ np.array([0.3, 0.55, 0.15])) / 255.0
    # split: torn horizontal bands of red over the cyan-tinted piece (keeps the drawing's light)
    m = np.zeros((n, n))
    y = 0
    red = rnd.random() < 0.5
    while y < n:
        h = rnd.randint(max(2, n // 12), max(3, n // 4))
        m[y:y + h] = 1.0 if red else 0.0
        red = not red
        y += h
    tint = CYAN * (1 - m[..., None]) + RED * m[..., None]
    col = rgb * 0.55 + tint * lum[..., None] * 0.75
    # torn slices
    al2 = al.copy()
    for _ in range(rnd.randint(2, 4)):
        y0 = rnd.randint(2, n - 4); h = rnd.randint(1, max(2, n // 20)); dx = rnd.choice([-1, 1]) * rnd.randint(1, max(2, n // 24))
        col[y0:y0 + h] = np.roll(col[y0:y0 + h], dx, axis=1)
        al2[y0:y0 + h] = np.roll(al2[y0:y0 + h], dx, axis=1)
    # chromatic fringe under the piece
    s = max(1, px)
    out = np.zeros((n, n, 4))
    for ghost, c in ((np.roll(al2, -s, axis=1), (255, 40, 70)), (np.roll(al2, s, axis=1), (0, 235, 255))):
        ga = ghost * 0.55 * (1 - out[..., 3])
        out[..., :3] += np.array(c) * ga[..., None]
        out[..., 3] += ga
    o = al2[..., None]
    out[..., :3] = col * o + out[..., :3] * (1 - o)
    out[..., 3] = np.clip(al2 + out[..., 3] * (1 - al2), 0, 1)
    out[::3, :, :3] *= 0.85
    out[..., :3] = np.clip(out[..., :3], 0, 255)
    out[..., 3] *= 255
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def spec(rnd):
    first = rnd.choice(KINDS)
    kinds = [first]
    if rnd.random() < 0.6:
        kinds.append(rnd.choice([k for k in KINDS if k != first and not (first in ("ORBS", "HELIX") and k in ("ORBS", "HELIX"))]))
    parts = []
    for k in kinds:
        n = {"ORBS": rnd.randint(2, 4), "GEARS": 2, "SHARDS": 3, "HELIX": 6}.get(k, 1)
        parts.append(f"{k}:{n}:GX:{round(rnd.uniform(0.8, 1.6), 2) * rnd.choice([1, -1])}")
    parts.append("GLITCH:1:GX:1.0")
    if rnd.random() < 0.6:
        parts.append("FLOAT:1:FFFFFF:1.0")
    return ";".join(parts)


def main():
    pets, eggs, meta = [], [], []
    seen = set()
    for k in range(COUNT):
        rnd = random.Random(f"gx-pet-{k}")
        pal = dict(PALS[k % len(PALS)])
        pet, pal = g.draw_pet("ENCRYPTED", rnd, pal_override=pal)
        egg = g.draw_egg("ENCRYPTED", pal, random.Random(f"gx-egg-{k}"))
        pets.append(glitch(pet, random.Random(f"gx-tear-{k}"), 1))
        eggs.append(glitch(egg, random.Random(f"gx-egg-tear-{k}"), 1))
        for t in range(len(WORDS) * len(g.SPECIES)):
            name = f"{WORDS[(k + t) % len(WORDS)]} {g.SPECIES[(k * 13 + t // len(WORDS)) % len(g.SPECIES)]}"
            if name not in seen:
                break
        seen.add(name)
        egg_name = f"{WORDS[k % len(WORDS)]} {['Core', 'Shell', 'Prism', 'Vault', 'Rift', 'Nexus'][k % 6]} Egg"
        meta.append(f"GLITCHED_ENCRYPTED|{name}|{egg_name}|{spec(rnd)}")

    # Place them: global index NEW_BASE (1024) + FIRST + k → page (index // 256), cell (index % 256).
    for imgs, px, prefix in ((pets, g.PET, "pets"), (eggs, g.EGG, "eggs")):
        pages = {}
        for k, im in enumerate(imgs):
            idx = 1024 + FIRST + k
            p, c = idx // g.PER_PAGE, idx % g.PER_PAGE
            if p not in pages:
                path = os.path.join(g.RES, f"{prefix}_{p}.webp")
                pages[p] = Image.open(path).convert("RGBA") if os.path.exists(path) else Image.new("RGBA", (g.COLS * px, g.COLS * px), (0, 0, 0, 0))
                # clear the cells this script owns, so re-running is safe
                if p == 7:
                    for cc in range(232, 256):
                        pages[p].paste((0, 0, 0, 0), ((cc % g.COLS) * px, (cc // g.COLS) * px, (cc % g.COLS + 1) * px, (cc // g.COLS + 1) * px))
            pages[p].paste(im, ((c % g.COLS) * px, (c // g.COLS) * px))
        for p, im in pages.items():
            im.save(os.path.join(g.RES, f"{prefix}_{p}.webp"), "WEBP", lossless=True, method=6)

    path = os.path.join(g.RES, "new_pets.txt")
    lines = [l for l in open(path).read().splitlines() if l.strip()]
    # keep anything after its 50 (gen_more_pets.py's 300, 0.49.1)
    lines = lines[:FIRST] + meta + lines[FIRST + COUNT:]
    open(path, "w").write("\n".join(lines) + "\n")
    print("pets", len(lines))

    # Review sheet + GIF (the python twin draws GX parts in the shifting colour; GLITCH tears are in-game only).
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 11)
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
    except OSError:
        font = big = ImageFont.load_default()
    cols, cw, ch = 10, 150, 172
    sheet = Image.new("RGBA", (cols * cw + 20, 5 * ch + 70), (14, 18, 28, 255))
    d = ImageDraw.Draw(sheet)
    d.text((14, 16), "GLITCHED ENCRYPTED PETS  ·  50", fill=(255, 46, 77, 255), font=big)
    for i in range(COUNT):
        x = 10 + (i % cols) * cw; y = 60 + (i // cols) * ch
        d.rounded_rectangle([x + 3, y + 3, x + cw - 3, y + ch - 3], 8, fill=(22, 28, 42, 255), outline=(0, 240, 255, 140) if i % 2 else (255, 43, 58, 140))
        sheet.alpha_composite(g.render_anim(pets[i], meta[i].split("|")[3], 0.7 + i * 0.37), (x + 11, y + 6))
        sheet.alpha_composite(eggs[i].resize((40, 40), Image.LANCZOS), (x + cw - 46, y + 96))
        nm = meta[i].split("|")[1]
        d.text((x + 9, y + ch - 30), nm if len(nm) <= 22 else nm[:21] + "…", fill=(230, 236, 245, 255), font=font)
        d.text((x + 9, y + ch - 16), meta[i].split("|")[2][:24], fill=(140, 150, 170, 255), font=font)
    sheet.save(os.path.join(g.DOCS, "glitched_encrypted_pets.png"))
    frames = []
    for f in range(36):
        t = f / 36 * 2.86
        fr = Image.new("RGBA", (5 * 132 + 4, 2 * 132 + 4), (14, 18, 28, 255))
        for i in range(10):
            fr.alpha_composite(g.render_anim(pets[i * 5], meta[i * 5].split("|")[3], t), (2 + (i % 5) * 132, 2 + (i // 5) * 132))
        frames.append(fr.convert("RGB"))
    frames[0].save(os.path.join(g.DOCS, "glitched_encrypted_pets.gif"), save_all=True, append_images=frames[1:], duration=70, loop=0)


if __name__ == "__main__":
    sys.exit(main())
