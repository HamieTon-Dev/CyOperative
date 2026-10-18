"""GLITCHED ENCRYPTED gear art (owner, 0.47.0).

Every slot gets 50 new pieces; the PRIMARY sheet also gets 50 class weapons for each of the five
classes (Operative, Technician, Juggernaut, Phantom, Packet Mage), so 300 rifles in all.
Each piece is one of the slot's existing pieces (any tier) re-cut in the new look:
a cyan / red split shading, torn glitch slices, a red-cyan chromatic fringe and scanlines.

Writes app/src/main/resources/gear/<slot>_gx.webp (16 x 96 px cells, in order) and
docs/gear_gx/<slot>.png review sheets.

Run: python3 tools/gear/glitched_encrypted.py
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GEAR = os.path.join(ROOT, "app/src/main/resources/gear")
DOCS = os.path.join(ROOT, "docs/gear_gx")
CELL, COLS = 96, 16
# Cells per art tier in each atlas (GearArt.counts).
COUNTS = {
    "primary": [21, 19, 15, 17, 28, 5, 5, 5, 5], "secondary": [20, 20, 19, 21, 20, 5, 5, 5, 5],
    "helmet": [44, 44, 44, 42, 46, 5, 5, 5, 5], "chest": [23, 17, 17, 17, 26, 5, 5, 5, 5],
    "gloves": [20, 19, 17, 21, 23, 5, 5, 5, 5], "boots": [44, 44, 36, 38, 48, 5, 5, 5, 5],
    "implant": [20, 20, 19, 20, 21, 5, 5, 5, 5], "core": [20, 20, 20, 20, 20, 5, 5, 5, 5],
}
PER_SLOT = 50
CLASSES = ["operative", "technician", "juggernaut", "phantom", "packet_mage"]  # GunStyle classed order

CYAN = np.array([[3, 8, 20], [0, 70, 120], [0, 200, 235], [120, 250, 255], [235, 255, 255]], float)
RED = np.array([[18, 3, 8], [110, 8, 28], [235, 35, 60], [255, 120, 135], [255, 230, 232]], float)


def ramp(pal, l):
    x = np.clip(l, 0, 1) * (len(pal) - 1)
    i = np.clip(x.astype(int), 0, len(pal) - 2)
    f = (x - i)[..., None]
    return pal[i] * (1 - f) + pal[i + 1] * f


def cells(sheet):
    im = np.asarray(Image.open(os.path.join(GEAR, sheet + ".webp")).convert("RGBA")).astype(float)
    out = []
    for c in range(sum(COUNTS[sheet])):
        x, y = (c % COLS) * CELL, (c // COLS) * CELL
        cell = im[y:y + CELL, x:x + CELL]
        if cell[..., 3].sum() > 255 * 200:
            out.append(cell)
    return out


def split_mask(rng, style):
    """Where the piece turns red (1) instead of cyan (0)."""
    yy, xx = np.mgrid[0:CELL, 0:CELL] / CELL
    if style == 0:    # torn horizontal bands
        m = np.zeros((CELL, CELL))
        y = 0
        red = rng.random() < 0.5
        while y < CELL:
            h = int(rng.integers(5, 22))
            m[y:y + h] = 1.0 if red else 0.0
            red = not red
            y += h
    elif style == 1:  # diagonal split with a jagged edge
        edge = 0.5 + 0.08 * np.sin(yy * rng.uniform(15, 30)) * (rng.random() < 0.7)
        m = ((xx + yy * rng.uniform(0.4, 1.4)) / 2 > edge * rng.uniform(0.8, 1.1)).astype(float)
    elif style == 2:  # vertical halves, a stepped seam
        steps = (np.floor(yy * 8) % 2) * rng.uniform(0.04, 0.12)
        m = (xx > 0.5 + steps - 0.06).astype(float)
    else:             # glitch blocks over a gradient
        m = np.clip(yy * 1.2 - 0.1, 0, 1)
        for _ in range(int(rng.integers(5, 10))):
            x0, y0 = rng.integers(0, CELL - 8, 2)
            w, h = rng.integers(8, 34), rng.integers(3, 12)
            m[y0:y0 + h, x0:x0 + w] = 1 - m[y0:y0 + h, x0:x0 + w].mean().round()
    if rng.random() < 0.5:
        m = 1 - m
    return m


def glitch(src, seed, cls=None):
    rng = np.random.default_rng(seed)
    rgb, a = src[..., :3], src[..., 3] / 255.0
    lum = (rgb @ np.array([0.3, 0.55, 0.15])) / 255.0
    lum = np.clip((lum - 0.06) * rng.uniform(1.25, 1.55), 0, 1) ** 0.85
    m = split_mask(rng, int(rng.integers(0, 4)))[..., None]
    if cls == "juggernaut":
        m = np.clip(m * 0.6 + 0.4, 0, 1)        # heavier red
    elif cls == "technician":
        m = m * 0.6                              # mostly cyan
    col = ramp(CYAN, lum) * (1 - m) + ramp(RED, lum) * m
    # Torn slices: a few rows shoved sideways.
    for _ in range(int(rng.integers(3, 6))):
        y0 = int(rng.integers(4, CELL - 8)); h = int(rng.integers(2, 7)); dx = int(rng.integers(-6, 7))
        col[y0:y0 + h] = np.roll(col[y0:y0 + h], dx, axis=1)
        a = a.copy(); a[y0:y0 + h] = np.roll(a[y0:y0 + h], dx, axis=1)
    # Chromatic fringe: a red ghost left, a cyan ghost right, under the piece.
    s = int(rng.integers(2, 4))
    ar, ac = np.roll(a, -s, axis=1), np.roll(a, s, axis=1)
    out = np.zeros((CELL, CELL, 4))
    for ghost, c in ((ar, (255, 40, 70)), (ac, (0, 235, 255))):
        ga = ghost * 0.55 * (1 - out[..., 3:4][..., 0])
        out[..., :3] += np.array(c) * ga[..., None]
        out[..., 3] += ga
    over = a[..., None]
    out[..., :3] = col * over + out[..., :3] * (1 - over)
    out[..., 3] = np.clip(a + out[..., 3] * (1 - a), 0, 1)
    # Scanlines.
    out[::3, :, :3] *= 0.82
    # Class marks on class weapons: a small glyph in the corner.
    if cls is not None:
        mark(out, CLASSES.index(cls))
    # Stray glitch pixels near the outline.
    for _ in range(int(rng.integers(3, 8))):
        x0, y0 = rng.integers(2, CELL - 10, 2)
        if a[y0, x0] < 0.1 and a[max(0, y0 - 4):y0 + 4, max(0, x0 - 6):x0 + 6].max() > 0.5:
            c = (0, 240, 255) if rng.random() < 0.5 else (255, 43, 58)
            w, h = int(rng.integers(3, 9)), int(rng.integers(1, 3))
            out[y0:y0 + h, x0:x0 + w, :3] = c
            out[y0:y0 + h, x0:x0 + w, 3] = 0.9
    out[..., :3] = np.clip(out[..., :3], 0, 255)
    out[..., 3] = np.clip(out[..., 3] * 255, 0, 255)
    return out.astype(np.uint8)


def mark(out, k):
    """Class glyph, bottom-left: Operative ◆, Technician ⚡ bars, Juggernaut ■, Phantom ◐, Packet Mage ⬡."""
    g = np.zeros((11, 11))
    yy, xx = np.mgrid[0:11, 0:11] - 5
    if k == 0: g = (abs(xx) + abs(yy) <= 5).astype(float) - (abs(xx) + abs(yy) <= 2)
    elif k == 1: g[:, 2:4] = 1; g[:, 7:9] = 1; g[4:7, :] = 1
    elif k == 2: g[:] = 1; g[3:8, 3:8] = 0
    elif k == 3: g = ((xx ** 2 + yy ** 2 <= 25) & ((xx <= 0) | (xx ** 2 + yy ** 2 >= 9))).astype(float)
    else: g = ((abs(xx) * 0.87 + abs(yy) * 0.5 <= 5) & (abs(yy) <= 5)).astype(float) - ((abs(xx) * 0.87 + abs(yy) * 0.5 <= 2.6) & (abs(yy) <= 2.6))
    y0, x0 = CELL - 15, 3
    c = np.array((255, 255, 255))
    reg = out[y0:y0 + 11, x0:x0 + 11]
    reg[..., :3] = reg[..., :3] * (1 - g[..., None]) + c * g[..., None]
    reg[..., 3] = np.maximum(reg[..., 3], g)


def sheet(name, tiles):
    rows = (len(tiles) + COLS - 1) // COLS
    im = np.zeros((rows * CELL, COLS * CELL, 4), np.uint8)
    for i, t in enumerate(tiles):
        im[(i // COLS) * CELL:(i // COLS + 1) * CELL, (i % COLS) * CELL:(i % COLS + 1) * CELL] = t
    Image.fromarray(im).save(os.path.join(GEAR, name + "_gx.webp"), lossless=False, quality=92, method=6)
    prev = Image.new("RGBA", (COLS * CELL, rows * CELL), (8, 12, 22, 255))
    prev.alpha_composite(Image.fromarray(im))
    prev.convert("RGB").resize((COLS * 48, rows * 48), Image.LANCZOS).save(os.path.join(DOCS, name + ".png"))


def main():
    os.makedirs(DOCS, exist_ok=True)
    for slot in COUNTS:
        src = cells(slot)
        rng = np.random.default_rng(hash(slot) % 2**32 if False else sum(map(ord, slot)) * 7919)
        order = rng.permutation(len(src))
        tiles = [glitch(src[order[i % len(src)]], 1000 * sum(map(ord, slot)) + i) for i in range(PER_SLOT)]
        if slot == "primary":
            for k, cls in enumerate(CLASSES):
                o = np.random.default_rng(31 + k).permutation(len(src))
                tiles += [glitch(src[o[i % len(src)]], 77_000 + k * 1000 + i, cls) for i in range(PER_SLOT)]
        sheet(slot, tiles)
        print(slot, len(tiles))


if __name__ == "__main__":
    main()
