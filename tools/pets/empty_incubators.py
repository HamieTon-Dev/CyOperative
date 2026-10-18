"""
Empty incubator pictures (owner, 0.45.0: "make this look empty inside the image of the
incubator if the player isn't trying to hatch an egg"). The owner's six incubator states each
have an egg painted in the glass; this paints it out (the chamber's own glass, sampled from either
side of the egg and blended in, plus a faint glass streak) and writes incubator_empty_1..6.webp.
The game then draws the real egg being hatched inside the glass.

Run: python3 tools/pets/empty_incubators.py
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage

RES = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "resources", "pets")

# The egg in every picture (fractions of the picture): centre and radii.
CX, CY, RX, RY = 0.5, 0.555, 0.155, 0.245
FLOOR = 0.735          # below this the glowing pad stays as painted


def empty(im):
    a = np.asarray(im.convert("RGBA")).astype(np.float32)
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy, rx, ry = CX * w, CY * h, RX * w, RY * h
    d = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2
    # The egg plus its glowing halo; the pad below stays as painted.
    hole = (d <= 1.35) & (yy < FLOOR * h)
    out = a.copy()
    # Fill the hole smoothly from the glass all round it (repeated neighbour averaging).
    k = np.array([[0, 1, 0], [1, 0, 1], [0, 1, 0]], np.float32) / 4.0
    for c in range(3):
        ch = out[..., c]
        ch[hole] = ch[~hole].mean()
        for _ in range(900):
            sm = ndimage.convolve(ch, k, mode="nearest")
            ch[hole] = sm[hole]
        out[..., c] = ch
    # A little of the glass's own grain, and a faint vertical streak, so it reads as glass.
    rng = np.random.default_rng(7)
    grain = ndimage.gaussian_filter(rng.normal(0, 1, (h, w)), 0.8) * 5
    out[..., :3] += (grain * hole)[..., None]
    streak = np.exp(-((xx - (cx - rx * 0.55)) / (rx * 0.1)) ** 2) * hole * np.clip((yy - (cy - ry)) / (ry * 2), 0, 1)
    out[..., :3] += streak[..., None] * 22
    # Feather the edge.
    soft = ndimage.gaussian_filter(hole.astype(np.float32), 1.2)
    edge = (np.clip(soft, 0, 1) * (1 - hole))[..., None]
    blur = np.stack([ndimage.gaussian_filter(out[..., c], 1.0) for c in range(4)], -1)
    out = out * (1 - edge * 0.6) + blur * edge * 0.6
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def main():
    for i in range(1, 7):
        im = Image.open(os.path.join(RES, f"incubator_{i}.webp"))
        empty(im).save(os.path.join(RES, f"incubator_empty_{i}.webp"), "WEBP", lossless=True)
    print("ok")


if __name__ == "__main__":
    main()
