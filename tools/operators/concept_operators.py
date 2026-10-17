"""
Operator redesign concept (owner, 0.43.x: "a new layout of operator design that more closely
matches the design of the pets"). Draws the five classes in the pets' style: glossy shaded
armour, a dark visor with the glowing >_< face, round side pods, chunky chibi proportions, and
a class prop. Every class is shown in three skins, with the creator's HEAD / FACE / LEGS options
and an in-game scale check next to a pet. For review only; nothing in the game uses it yet.

Run: python3 tools/operators/concept_operators.py  -> docs/operators_concept/*.png
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "pets"))
import gen_new_pets as g  # noqa: E402  (shading toolkit shared with the pets)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs", "operators_concept")
S = 4
PX = 200          # final figure box

SKINS = {
    "agent": [("DEFAULT", (40, 90, 230), (0, 230, 140)), ("TITANIUM", (200, 206, 216), (240, 245, 255)), ("PURPLE", (130, 70, 230), (210, 160, 255))],
    "technician": [("DEFAULT", (30, 110, 240), (0, 220, 255)), ("TITANIUM", (200, 206, 216), (240, 245, 255)), ("PURPLE", (130, 70, 230), (210, 160, 255))],
    "juggernaut": [("DEFAULT", (40, 140, 90), (0, 255, 150)), ("TITANIUM", (190, 196, 206), (240, 245, 255)), ("PURPLE", (110, 60, 200), (210, 160, 255))],
    "phantom": [("DEFAULT", (24, 60, 50), (0, 255, 170)), ("COBALT", (30, 70, 200), (120, 200, 255)), ("PURPLE", (80, 50, 140), (210, 160, 255))],
    "mage": [("DEFAULT", (20, 70, 60), (0, 255, 170)), ("COBALT", (30, 70, 200), (120, 200, 255)), ("PURPLE", (90, 50, 160), (210, 160, 255))],
}
NAMES = {"agent": "FIELD AGENT", "technician": "TECHNICIAN", "juggernaut": "JUGGERNAUT", "phantom": "PHANTOM", "mage": "PACKET MAGE"}
DARK = (16, 18, 28)


def face(c, cx, cy, w, h, glow, kind=0):
    """The operators' signature visor: dark glossy plate + glowing emote."""
    N = c.n
    g.shaded_ellipse(c, cx, cy, w, h, (14, 16, 26), spec=0.7, amb=0.85)
    lw = max(S * 2.2, w * 0.09)
    if kind == 0:   # >_<
        a = g.line_mask(N, [(cx - w * 0.62, cy - h * 0.32), (cx - w * 0.3, cy - h * 0.02), (cx - w * 0.62, cy + h * 0.28)], lw)
        b = g.line_mask(N, [(cx + w * 0.62, cy - h * 0.32), (cx + w * 0.3, cy - h * 0.02), (cx + w * 0.62, cy + h * 0.28)], lw)
        m = g.line_mask(N, [(cx - w * 0.14, cy + h * 0.32), (cx + w * 0.14, cy + h * 0.32)], lw)
        mask = np.maximum(np.maximum(a, b), m)
    elif kind == 1:  # ▮▮ eyes
        mask = np.maximum(g.poly_mask(N, [(cx - w * 0.5, cy - h * 0.35), (cx - w * 0.2, cy - h * 0.35), (cx - w * 0.2, cy + h * 0.35), (cx - w * 0.5, cy + h * 0.35)]),
                          g.poly_mask(N, [(cx + w * 0.2, cy - h * 0.35), (cx + w * 0.5, cy - h * 0.35), (cx + w * 0.5, cy + h * 0.35), (cx + w * 0.2, cy + h * 0.35)]))
    else:            # ^ ^ visor smile
        mask = np.maximum(g.line_mask(N, [(cx - w * 0.6, cy + h * 0.1), (cx - w * 0.35, cy - h * 0.25), (cx - w * 0.1, cy + h * 0.1)], lw),
                          g.line_mask(N, [(cx + w * 0.1, cy + h * 0.1), (cx + w * 0.35, cy - h * 0.25), (cx + w * 0.6, cy + h * 0.1)], lw))
    c.glow(mask, glow, 5, 1.0)
    c.put(mask, g.mix(glow, (255, 255, 255), 0.35))
    c.put(g.ellipse_mask(c, cx - w * 0.45, cy - h * 0.55, w * 0.25, h * 0.12), (255, 255, 255), 0.18)


def leg(c, hx, hy, fx, fy, col, glow, w, kind=0):
    N = c.n
    kx = (hx + fx) / 2 + (fx - hx) * 0.1
    ky = (hy + fy) / 2
    g.tube(c, [(hx, hy), (kx, ky)], w, col)
    g.tube(c, [(kx, ky), (fx, fy - w * 0.4)], w * 0.85, col)
    g.shaded_ellipse(c, kx, ky, w * 0.55, w * 0.55, g.mix(col, glow, 0.25), spec=0.6)
    if kind == 2:  # hover thruster
        m = g.ellipse_mask(c, fx, fy + w * 0.5, w * 0.6, w * 0.25)
        c.glow(m, glow, 6, 1.0)
        g.shaded_ellipse(c, fx, fy, w * 0.75, w * 0.45, DARK, spec=0.5)
    else:  # boot
        bw = w * (1.25 if kind == 0 else 1.5)
        g.shaded_ellipse(c, fx, fy, bw, w * 0.55, g.mix(col, (0, 0, 0), 0.35), spec=0.7)
        c.put(g.line_mask(N, [(fx - bw * 0.6, fy + w * 0.1), (fx + bw * 0.6, fy + w * 0.1)], S * 1.6), glow, 0.9)


def side_pod(c, x, y, r, col, glow):
    g.shaded_ellipse(c, x, y, r, r * 1.08, g.mix(col, (0, 0, 0), 0.25), spec=0.6)
    m = g.ellipse_mask(c, x, y, r * 0.5, r * 0.55)
    c.glow(m, glow, 3, 0.8)
    c.put(m, glow)


def operator(cls, body, glow, head=0, face_kind=0, legs=0):
    N = PX * S
    c = g.Canvas(N)
    cx = N * 0.5
    dark_body = g.mix(body, (0, 0, 0), 0.35)

    # ---- class back layer ------------------------------------------------------------------
    if cls in ("phantom", "mage"):
        # long glossy robe / cloak (behind arms), tattered for the Phantom
        top = N * 0.42
        pts = [(cx - N * 0.16, top), (cx - N * 0.26, N * 0.86), (cx + N * 0.26, N * 0.86), (cx + N * 0.16, top)]
        if cls == "phantom":
            pts = [(cx - N * 0.16, top), (cx - N * 0.3, N * 0.78), (cx - N * 0.2, N * 0.72), (cx - N * 0.14, N * 0.84),
                   (cx - N * 0.04, N * 0.74), (cx + N * 0.06, N * 0.86), (cx + N * 0.14, N * 0.73), (cx + N * 0.3, N * 0.8), (cx + N * 0.16, top)]
        m = g.poly_mask(N, pts)
        shade = (0.55 + 0.45 * np.clip(1 - (c.x - (cx - N * 0.3)) / (N * 0.6), 0, 1))[..., None]
        c.put(m, np.asarray(body, np.float32)[None, None, :] * shade)
        c.put(g.line_mask(N, [(cx, top + N * 0.05), (cx, N * 0.84)], S * 1.6) * m, glow, 0.5)
    if cls == "technician":   # backpack with cables
        g.shaded_ellipse(c, cx + N * 0.13, N * 0.55, N * 0.11, N * 0.13, dark_body, spec=0.5)
        g.tube(c, [(cx + N * 0.2, N * 0.48), (cx + N * 0.27, N * 0.36), (cx + N * 0.24, N * 0.26)], N * 0.018, glow, light=False)
        m = g.ellipse_mask(c, cx + N * 0.24, N * 0.25, N * 0.022, N * 0.022); c.glow(m, glow, 4, 1); c.put(m, glow)
    if cls == "agent":   # short scarf fin
        m = g.poly_mask(N, [(cx - N * 0.08, N * 0.43), (cx - N * 0.36, N * 0.46), (cx - N * 0.26, N * 0.5), (cx - N * 0.06, N * 0.5)])
        c.put(m, glow, 0.9)

    # ---- legs --------------------------------------------------------------------------------
    hip = N * 0.66
    foot = N * 0.86
    lw = N * (0.07 if cls != "juggernaut" else 0.095)
    if cls not in ("phantom", "mage") or legs == 2:
        lk = 2 if (cls == "technician" or legs == 2) else (1 if legs == 1 else 0)
        spread = N * (0.09 if cls != "juggernaut" else 0.13)
        for sgn in (-1, 1):
            leg(c, cx + sgn * spread * 0.7, hip, cx + sgn * spread, foot, dark_body, glow, lw, lk)
    elif cls == "phantom":   # floating: soft glow under the cloak
        m = g.ellipse_mask(c, cx, N * 0.9, N * 0.14, N * 0.03); c.glow(m, glow, 8, 0.8)

    # ---- torso -------------------------------------------------------------------------------
    tw = N * (0.17 if cls != "juggernaut" else 0.24)
    th = N * (0.15 if cls != "juggernaut" else 0.17)
    ty = N * 0.56
    if cls not in ("phantom", "mage"):
        g.shaded_ellipse(c, cx, ty, tw, th, body, spec=0.9)
        belt = g.line_mask(N, [(cx - tw * 0.95, ty + th * 0.55), (cx + tw * 0.95, ty + th * 0.55)], S * 3) * g.ellipse_mask(c, cx, ty, tw, th)
        c.put(belt, DARK, 0.9)
    else:
        g.shaded_ellipse(c, cx, ty - th * 0.2, tw * 0.9, th * 0.8, body, spec=0.6)
    core = g.ellipse_mask(c, cx, ty - th * 0.1, N * 0.028, N * 0.028)
    if cls == "juggernaut":
        core = g.poly_mask(N, [(cx - N * 0.06, ty - th * 0.45), (cx + N * 0.06, ty - th * 0.45), (cx, ty + th * 0.1)])
    if cls == "mage":
        core = g.poly_mask(N, [(cx, ty - th * 0.5), (cx + N * 0.035, ty - th * 0.15), (cx, ty + th * 0.2), (cx - N * 0.035, ty - th * 0.15)])
    c.glow(core, glow, 5, 1.0); c.put(core, g.mix(glow, (255, 255, 255), 0.3))

    # ---- arms (shoulder pods + tubes) ----------------------------------------------------------
    sh_y = ty - th * 0.55
    aw = N * (0.05 if cls != "juggernaut" else 0.075)
    for sgn in (-1, 1):
        sx = cx + sgn * tw * 1.0
        hand = (sx + sgn * N * 0.07, sh_y + N * 0.16)
        if cls == "agent" and sgn == 1: hand = (sx + N * 0.02, sh_y + N * 0.12)
        if cls == "mage" and sgn == 1: hand = (sx + N * 0.08, sh_y + N * 0.08)
        g.tube(c, [(sx, sh_y + N * 0.02), hand], aw, dark_body)
        g.shaded_ellipse(c, hand[0], hand[1], aw * 0.7, aw * 0.7, g.mix(body, (255, 255, 255), 0.1), spec=0.6)
        side_pod(c, sx, sh_y, N * (0.045 if cls != "juggernaut" else 0.07), body, glow)

    # ---- class props (front) ---------------------------------------------------------------
    if cls == "agent":   # rounded blaster rifle across the body
        g.tube(c, [(cx - N * 0.1, N * 0.6), (cx + N * 0.3, N * 0.48)], N * 0.06, (60, 66, 84))
        g.tube(c, [(cx + N * 0.22, N * 0.505), (cx + N * 0.36, N * 0.465)], N * 0.03, (40, 44, 60))
        m = g.line_mask(N, [(cx - N * 0.02, N * 0.575), (cx + N * 0.2, N * 0.51)], S * 2.2); c.glow(m, glow, 4, 1); c.put(m, glow)
        m = g.ellipse_mask(c, cx + N * 0.37, N * 0.462, N * 0.018, N * 0.018); c.glow(m, glow, 5, 1); c.put(m, (255, 255, 255))
    if cls == "technician":   # tool drone hovering by the hand
        dx, dy = cx - N * 0.3, N * 0.48
        g.shaded_ellipse(c, dx, dy, N * 0.055, N * 0.045, g.mix(body, (255, 255, 255), 0.2), spec=1.0)
        e = g.ellipse_mask(c, dx, dy, N * 0.02, N * 0.016); c.glow(e, glow, 3, 1); c.put(e, glow)
        c.put(g.ellipse_mask(c, dx, dy - N * 0.06, N * 0.07, N * 0.008), g.mix(glow, (255, 255, 255), 0.4), 0.8)
        m = g.line_mask(N, [(dx + N * 0.05, dy + N * 0.02), (cx - N * 0.2, N * 0.55)], S * 1.5); c.glow(m, glow, 3, 0.8); c.put(m, glow, 0.8)
    if cls == "juggernaut":   # hex energy shield on the right arm, shoulder cannon on the left
        hx, hy, r = cx + N * 0.3, N * 0.58, N * 0.15
        pts = [(hx + math.cos(a) * r * 0.75, hy + math.sin(a) * r) for a in [math.pi / 2 + k * math.pi / 3 for k in range(6)]]
        m = g.poly_mask(N, pts)
        c.put(m, g.mix(glow, DARK, 0.75), 0.85)
        edge = np.clip(m - g.poly_mask(N, [(hx + (x - hx) * 0.8, hy + (y - hy) * 0.8) for x, y in pts]), 0, 1)
        c.glow(edge, glow, 4, 1); c.put(edge, glow)
        g.shaded_ellipse(c, cx - N * 0.27, N * 0.33, N * 0.085, N * 0.085, (50, 54, 66), spec=0.8)
        m = np.clip(g.ellipse_mask(c, cx - N * 0.27, N * 0.33, N * 0.05, N * 0.05) - g.ellipse_mask(c, cx - N * 0.27, N * 0.33, N * 0.03, N * 0.03), 0, 1)
        c.glow(m, glow, 4, 1); c.put(m, glow)
    if cls == "phantom":   # floating data shards and a blade
        for k, (u, v) in enumerate(((0.16, 0.42), (0.84, 0.4), (0.78, 0.66))):
            x, y = N * u, N * v
            m = g.poly_mask(N, [(x, y - N * 0.035), (x + N * 0.018, y), (x, y + N * 0.035), (x - N * 0.018, y)])
            c.glow(m, glow, 3, 0.8); c.put(m, g.mix(glow, (255, 255, 255), 0.3))
        m = g.poly_mask(N, [(cx + N * 0.2, N * 0.6), (cx + N * 0.34, N * 0.42), (cx + N * 0.22, N * 0.62)])
        c.glow(m, glow, 4, 0.9); c.put(m, g.mix(glow, (255, 255, 255), 0.5))
    if cls == "mage":   # staff with an orbiting packet orb, floating code cards
        sx = cx + N * 0.27
        g.tube(c, [(sx, N * 0.86), (sx, N * 0.2)], N * 0.022, (90, 80, 70))
        m = g.ellipse_mask(c, sx, N * 0.17, N * 0.05, N * 0.05)
        c.glow(m, glow, 8, 1.0); g.shaded_ellipse(c, sx, N * 0.17, N * 0.045, N * 0.045, glow, spec=1.0, amb=0.8)
        ring = np.clip(g.ellipse_mask(c, sx, N * 0.17, N * 0.08, N * 0.022) - g.ellipse_mask(c, sx, N * 0.17, N * 0.065, N * 0.013), 0, 1)
        c.put(ring, g.mix(glow, (255, 255, 255), 0.5), 0.9)
        for k in range(3):
            x, y = cx - N * 0.33, N * (0.4 + k * 0.12)
            m = g.poly_mask(N, [(x - N * 0.035, y - N * 0.045), (x + N * 0.035, y - N * 0.045), (x + N * 0.035, y + N * 0.045), (x - N * 0.035, y + N * 0.045)])
            c.glow(m, glow, 3, 0.6); c.put(m, g.mix(glow, DARK, 0.6), 0.85)
            for j in range(3):
                c.put(g.line_mask(N, [(x - N * 0.02, y - N * 0.02 + j * N * 0.018), (x + N * 0.02, y - N * 0.02 + j * N * 0.018)], S), glow)

    # ---- head --------------------------------------------------------------------------------
    hy = N * 0.3
    hr = N * (0.17 if cls != "juggernaut" else 0.15)
    if cls in ("phantom", "mage"):   # tall pointed hood
        tip = (cx + N * 0.1, N * 0.04) if cls == "mage" else (cx - N * 0.08, N * 0.06)
        m = np.maximum(g.ellipse_mask(c, cx, hy + N * 0.02, hr * 1.05, hr * 1.0), g.poly_mask(N, [(cx - hr * 0.9, hy - hr * 0.2), tip, (cx + hr * 0.9, hy - hr * 0.2)]))
        shade = (0.5 + 0.5 * np.clip(1 - (c.x - (cx - hr)) / (2 * hr), 0, 1))[..., None]
        c.put(m, np.asarray(body, np.float32)[None, None, :] * shade + 30)
        c.put(g.ellipse_mask(c, cx, hy + N * 0.03, hr * 0.78, hr * 0.7), (8, 10, 16))
        face(c, cx, hy + N * 0.04, hr * 0.62, hr * 0.46, glow, face_kind)
    elif cls == "juggernaut":   # boxy monitor head (rounded)
        m = g.poly_mask(N, [(cx - hr, hy - hr * 0.8), (cx + hr, hy - hr * 0.8), (cx + hr * 1.05, hy + hr * 0.8), (cx - hr * 1.05, hy + hr * 0.8)])
        shade = (0.55 + 0.45 * np.clip(1 - (c.y - (hy - hr)) / (2 * hr), 0, 1))[..., None]
        c.put(m, np.asarray(body, np.float32)[None, None, :] * shade + 25)
        face(c, cx, hy, hr * 0.78, hr * 0.5, glow, face_kind)
        g.tube(c, [(cx, hy - hr * 0.8), (cx, hy - hr * 1.15)], N * 0.02, dark_body)
    else:   # glossy sphere helmet like the pets
        g.shaded_ellipse(c, cx, hy, hr, hr * 0.95, body, spec=1.0)
        face(c, cx, hy + hr * 0.1, hr * 0.74, hr * 0.56, glow, face_kind)
        for sgn in (-1, 1):
            side_pod(c, cx + sgn * hr * 0.98, hy + hr * 0.1, hr * 0.28, body, glow)
    # head options: 0 antenna, 1 ears, 2 crest/horns
    if cls in ("agent", "technician", "juggernaut"):
        if head == 0:
            g.tube(c, [(cx - hr * 0.5, hy - hr * 0.7), (cx - hr * 0.7, hy - hr * 1.35)], N * 0.016, dark_body)
            m = g.ellipse_mask(c, cx - hr * 0.7, hy - hr * 1.4, N * 0.02, N * 0.02); c.glow(m, glow, 4, 1); c.put(m, glow)
        elif head == 1:
            for sgn in (-1, 1):
                m = g.poly_mask(N, [(cx + sgn * hr * 0.35, hy - hr * 0.75), (cx + sgn * hr * 0.75, hy - hr * 1.45), (cx + sgn * hr * 0.85, hy - hr * 0.55)])
                c.put(m, g.mix(body, (0, 0, 0), 0.1)); c.put(g.line_mask(N, [(cx + sgn * hr * 0.55, hy - hr * 0.8), (cx + sgn * hr * 0.75, hy - hr * 1.3)], S * 1.6) * m, glow)
        else:
            for k in range(3):
                ox = (k - 1) * hr * 0.32
                m = g.poly_mask(N, [(cx + ox - hr * 0.13, hy - hr * 0.85), (cx + ox, hy - hr * (1.45 - abs(k - 1) * 0.25)), (cx + ox + hr * 0.13, hy - hr * 0.85)])
                c.glow(m, glow, 3, 0.7); c.put(m, glow)
    if cls == "technician":   # hood rim over the sphere
        rim = np.clip(g.ellipse_mask(c, cx, hy + hr * 0.08, hr * 0.9, hr * 0.72) - g.ellipse_mask(c, cx, hy + hr * 0.08, hr * 0.78, hr * 0.62), 0, 1)
        c.put(rim, glow, 0.6)

    img = g.outline(c.image(), (6, 8, 14), px=S)
    return img.resize((PX, PX), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
    except OSError:
        font = small = big = ImageFont.load_default()
    BG = (10, 13, 22, 255)

    # 1. The classes x skins sheet.
    cols = 3
    W = 260 + cols * 270
    H = 110 + len(SKINS) * 260
    sheet = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 22), "OPERATOR REDESIGN · CLASSES × SKINS", fill=(0, 240, 255, 255), font=big)
    for r, (cls, skins) in enumerate(SKINS.items()):
        y = 90 + r * 260
        d.text((24, y + 110), NAMES[cls], fill=(230, 236, 245, 255), font=font)
        for k, (label, body, glow) in enumerate(skins):
            x = 260 + k * 270
            if r == 0: d.text((x + 95, 70), label, fill=glow + (255,), font=small)
            sheet.alpha_composite(operator(cls, body, glow).resize((250, 250), Image.LANCZOS), (x, y))
    sheet.save(os.path.join(OUT, "operators_classes_skins.png"))

    # 2. Creator options (Field Agent): HEAD, FACE, LEGS.
    W2, H2 = 240 + 3 * 230, 110 + 3 * 230
    opt = Image.new("RGBA", (W2, H2), BG)
    d = ImageDraw.Draw(opt)
    d.text((24, 22), "CHARACTER CREATOR OPTIONS (Field Agent)", fill=(0, 240, 255, 255), font=big)
    body, glow = SKINS["agent"][0][1], SKINS["agent"][0][2]
    rows = [("HEAD", [dict(head=0), dict(head=1), dict(head=2)], ["ANTENNA", "EARS", "CREST"]),
            ("FACE", [dict(face_kind=0), dict(face_kind=1), dict(face_kind=2)], [">_<", "▮ ▮", "^ ^"]),
            ("LEGS", [dict(legs=0), dict(legs=1), dict(legs=2)], ["BOOTS", "HEAVY", "HOVER"])]
    for r, (label, variants, names) in enumerate(rows):
        y = 90 + r * 230
        d.text((24, y + 95), label, fill=(230, 236, 245, 255), font=font)
        for k, v in enumerate(variants):
            x = 240 + k * 230
            opt.alpha_composite(operator("agent", body, glow, **v), (x + 15, y + 10))
            d.text((x + 85, y + 205), names[k], fill=(150, 160, 180, 255), font=small)
    opt.save(os.path.join(OUT, "operators_creator_options.png"))

    # 3. In-game scale: each class next to a pet, at arena size.
    W3, H3 = 1100, 300
    sc = Image.new("RGBA", (W3, H3), (18, 24, 36, 255))
    d = ImageDraw.Draw(sc)
    d.text((24, 18), "IN-GAME SCALE  ·  operator (≈ 96 px) with its pet (54 px)", fill=(0, 240, 255, 255), font=font)
    pets = Image.open(os.path.join(ROOT, "app", "src", "main", "resources", "pets", "pets_6.webp")).convert("RGBA")
    for k, cls in enumerate(SKINS):
        body, glow = SKINS[cls][0][1], SKINS[cls][0][2]
        x = 30 + k * 212
        sc.alpha_composite(operator(cls, body, glow).resize((120, 120), Image.LANCZOS), (x, 110))
        pet = pets.crop(((k * 3 % 16) * 64, 0, (k * 3 % 16) * 64 + 64, 64)).resize((60, 60), Image.LANCZOS)
        sc.alpha_composite(pet, (x + 120, 160))
        d.text((x + 10, 245), NAMES[cls], fill=(200, 210, 225, 255), font=small)
    sc.save(os.path.join(OUT, "operators_ingame_scale.png"))
    print("ok")


if __name__ == "__main__":
    main()
