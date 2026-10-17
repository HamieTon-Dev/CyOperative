"""
Generates the 0.40.0 companion set (owner: "500 more pets ranging from glitched to encrypted
rarity, their eggs, and their design").

Each pet is built from parts in the style of the owner's pets: a glossy round robot body, a dark
visor face with glowing eyes, side pods, legs or thrusters, and a topper (antenna, ears, horns,
halo, propeller, flame, crystals), drawn at 4x and scaled down to 64 px. Each tier has its own
palette and effects:
  GLITCHED (gold + magenta/cyan tears), FORGED (molten iron), VOLTAIC (lightning),
  CELESTIAL (platinum, halos, wings), ABYSSAL (hellfire), ENCRYPTED (black + cyan circuitry).
Every pet gets a matching 48 px egg.

Outputs (resources/pets): pets_4.webp, pets_5.webp, eggs_4.webp, eggs_5.webp, new_pets.txt
(one line per new pet: tier name|pet name|egg name). Sheets for review go in docs/pets_new/.

Run: python3 tools/pets/gen_new_pets.py   (deterministic: same output every time)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "resources", "pets")
DOCS = os.path.join(ROOT, "docs", "pets_new")

S = 4                 # supersampling
PET = 64
EGG = 48
COLS = 16
PER_PAGE = 256

TIERS = [  # name, count
    ("GLITCHED", 90), ("FORGED", 85), ("VOLTAIC", 85), ("CELESTIAL", 85), ("ABYSSAL", 85), ("ENCRYPTED", 70),
]
TIER_COLOR = {
    "GLITCHED": (255, 61, 242), "FORGED": (255, 138, 31), "VOLTAIC": (255, 240, 77),
    "CELESTIAL": (221, 232, 255), "ABYSSAL": (255, 43, 58), "ENCRYPTED": (0, 240, 255),
}

# Body / accent / eye palettes per tier (several looks per tier).
PALETTES = {
    "GLITCHED": [
        dict(body=(236, 186, 52), trim=(120, 72, 20), accent=(255, 61, 242), eye=(61, 242, 255)),
        dict(body=(210, 60, 200), trim=(70, 20, 80), accent=(255, 214, 60), eye=(61, 242, 255)),
        dict(body=(40, 34, 60), trim=(18, 14, 30), accent=(255, 61, 242), eye=(255, 214, 60)),
        dict(body=(80, 220, 240), trim=(20, 70, 110), accent=(255, 61, 242), eye=(255, 230, 90)),
        dict(body=(245, 240, 250), trim=(110, 90, 130), accent=(255, 61, 242), eye=(61, 242, 255)),
    ],
    "FORGED": [
        dict(body=(74, 64, 60), trim=(30, 24, 22), accent=(255, 138, 31), eye=(255, 190, 70)),
        dict(body=(200, 92, 30), trim=(80, 34, 12), accent=(255, 214, 120), eye=(255, 240, 160)),
        dict(body=(120, 60, 40), trim=(50, 24, 16), accent=(255, 120, 20), eye=(255, 170, 40)),
        dict(body=(150, 140, 130), trim=(60, 54, 50), accent=(255, 138, 31), eye=(255, 110, 20)),
    ],
    "VOLTAIC": [
        dict(body=(250, 220, 50), trim=(110, 90, 10), accent=(30, 30, 40), eye=(120, 230, 255)),
        dict(body=(36, 36, 48), trim=(14, 14, 20), accent=(255, 240, 77), eye=(255, 250, 160)),
        dict(body=(240, 240, 230), trim=(100, 100, 90), accent=(255, 220, 40), eye=(90, 200, 255)),
        dict(body=(60, 110, 240), trim=(20, 40, 110), accent=(255, 240, 77), eye=(255, 250, 180)),
    ],
    "CELESTIAL": [
        dict(body=(232, 238, 250), trim=(120, 130, 160), accent=(255, 214, 90), eye=(110, 190, 255)),
        dict(body=(190, 214, 255), trim=(70, 90, 150), accent=(255, 240, 200), eye=(255, 230, 120)),
        dict(body=(250, 236, 200), trim=(150, 120, 70), accent=(150, 210, 255), eye=(120, 200, 255)),
        dict(body=(200, 190, 240), trim=(90, 80, 140), accent=(255, 230, 140), eye=(255, 255, 230)),
    ],
    "ABYSSAL": [
        dict(body=(40, 18, 22), trim=(14, 6, 8), accent=(255, 43, 58), eye=(255, 90, 40)),
        dict(body=(150, 20, 30), trim=(60, 8, 12), accent=(255, 150, 40), eye=(255, 220, 90)),
        dict(body=(70, 40, 50), trim=(26, 12, 18), accent=(255, 70, 30), eye=(255, 40, 60)),
        dict(body=(20, 20, 26), trim=(6, 6, 10), accent=(255, 60, 20), eye=(255, 200, 60)),
    ],
    "ENCRYPTED": [
        dict(body=(20, 26, 34), trim=(6, 10, 14), accent=(0, 240, 255), eye=(0, 255, 200)),
        dict(body=(30, 36, 50), trim=(10, 12, 20), accent=(0, 255, 160), eye=(0, 240, 255)),
        dict(body=(230, 240, 248), trim=(80, 100, 120), accent=(0, 220, 255), eye=(0, 255, 210)),
        dict(body=(16, 40, 50), trim=(4, 14, 20), accent=(120, 255, 250), eye=(0, 255, 160)),
    ],
}

TOPPERS = {
    "GLITCHED": ["antenna", "ears", "crown", "propeller", "shards", "none", "halo_glitch"],
    "FORGED": ["horns", "chimney", "crest", "antenna", "ears", "plates"],
    "VOLTAIC": ["coil", "antenna", "bolt_ears", "propeller", "rods", "ears"],
    "CELESTIAL": ["halo", "halo", "ears", "crown", "star", "antenna"],
    "ABYSSAL": ["flame", "horns", "flame", "horns_big", "crest", "ears"],
    "ENCRYPTED": ["antenna", "dish", "ears", "shards", "crest", "halo_code"],
}
LEGS = ["spider", "spider", "stubby", "hover", "claws", "tripod"]
EYES = ["two", "two", "big", "one", "three", "slit", "visor"]
BODIES = ["round", "round", "wide", "tall", "egg", "capsule", "headed", "headed"]

# --- Names --------------------------------------------------------------------------------------
TIER_WORDS = {
    "GLITCHED": ["Glitch", "Bugged", "Corrupt", "Desync", "Null", "Overflow", "Scramble", "Fractal", "Rogue", "Lag"],
    "FORGED": ["Forge", "Molten", "Ember", "Slag", "Anvil", "Cinder", "Smelt", "Kiln", "Ingot", "Crucible"],
    "VOLTAIC": ["Volt", "Surge", "Tesla", "Storm", "Arc", "Static", "Thunder", "Spark", "Ohm", "Galvan"],
    "CELESTIAL": ["Astral", "Seraph", "Halo", "Starlit", "Nova", "Lumen", "Aether", "Orbit", "Comet", "Zenith"],
    "ABYSSAL": ["Abyss", "Hellfire", "Brimstone", "Doom", "Pyre", "Infernal", "Wrath", "Dread", "Ashen", "Cinderhex"],
    "ENCRYPTED": ["Cipher", "Root", "Hash", "Kernel", "Zero-Day", "Quantum", "Sigma", "Blackbox", "Crypt", "Vector"],
}
SPECIES = [
    "Bytebeast", "Pinglet", "Codehound", "Sentinel", "Scuttler", "Wisp", "Drone", "Mite", "Golem", "Imp",
    "Strider", "Crawler", "Puck", "Orbling", "Prowler", "Seeker", "Warden", "Hopper", "Gremlin", "Spider",
    "Beetle", "Fox", "Pup", "Owlet", "Kit", "Scarab", "Moth", "Wyrm", "Hound", "Sprite",
]
EGG_WORDS = {
    "GLITCHED": ["Glitch", "Bugged", "Desync", "Null"], "FORGED": ["Forge", "Molten", "Slag", "Anvil"],
    "VOLTAIC": ["Volt", "Storm", "Surge", "Arc"], "CELESTIAL": ["Astral", "Halo", "Star", "Nova"],
    "ABYSSAL": ["Abyss", "Hellfire", "Doom", "Pyre"], "ENCRYPTED": ["Cipher", "Root", "Hash", "Crypt"],
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def clamp8(x):
    return np.clip(x, 0, 255).astype(np.uint8)


class Canvas:
    """RGBA float canvas with alpha compositing helpers (supersampled)."""

    def __init__(self, size):
        self.n = size
        self.rgb = np.zeros((size, size, 3), np.float32)
        self.a = np.zeros((size, size), np.float32)
        yy, xx = np.mgrid[0:size, 0:size]
        self.x = xx.astype(np.float32) + 0.5
        self.y = yy.astype(np.float32) + 0.5

    def put(self, mask, color, alpha=1.0):
        """Composite a solid colour (or HxWx3 array) through mask (0..1)."""
        m = np.clip(mask, 0, 1) * alpha
        col = np.asarray(color, np.float32)
        if col.ndim == 1:
            col = col[None, None, :]
        self.rgb = self.rgb * (1 - m[..., None]) + col * m[..., None]
        self.a = self.a + m * (1 - self.a)

    def glow(self, mask, color, radius, strength=0.8):
        img = Image.fromarray(clamp8(mask * 255), "L").filter(ImageFilter.GaussianBlur(radius))
        g = np.asarray(img, np.float32) / 255.0 * strength
        col = np.asarray(color, np.float32)[None, None, :]
        # additive-ish glow that also extends alpha
        self.rgb = self.rgb * (1 - g[..., None] * 0.6) + col * g[..., None] * 0.6 + col * g[..., None] * 0.4 * (1 - self.a[..., None])
        self.a = np.maximum(self.a, g * 0.85)

    def image(self):
        return Image.fromarray(np.dstack([clamp8(self.rgb), clamp8(self.a * 255)]), "RGBA")


def ellipse_mask(c, cx, cy, rx, ry, soft=1.2):
    d = ((c.x - cx) / rx) ** 2 + ((c.y - cy) / ry) ** 2
    r = np.sqrt(d)
    return np.clip((1 - r) * min(rx, ry) / soft, 0, 1)


def shaded_ellipse(c, cx, cy, rx, ry, color, spec=0.9, rim=None, amb=0.5):
    """A glossy sphere-ish ellipse lit from the top left."""
    m = ellipse_mask(c, cx, cy, rx, ry)
    nx = (c.x - cx) / rx
    ny = (c.y - cy) / ry
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    L = np.array([-0.5, -0.65, 0.58]); L = L / np.linalg.norm(L)
    diff = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    base = np.asarray(color, np.float32)
    shade = amb + (1 - amb) * diff
    col = base[None, None, :] * shade[..., None]
    # specular highlight
    H = L + np.array([0, 0, 1.0]); H = H / np.linalg.norm(H)
    sp = np.clip(nx * H[0] + ny * H[1] + nz * H[2], 0, 1) ** 28 * spec
    col = col + 255 * sp[..., None]
    # rim light / dark edge
    edge = np.clip((np.sqrt(nx * nx + ny * ny) - 0.82) / 0.18, 0, 1)
    rimc = np.asarray(rim if rim else (0, 0, 0), np.float32)
    col = col * (1 - edge[..., None] * 0.45) + rimc[None, None, :] * edge[..., None] * 0.45
    c.put(m, col)
    return m


def poly_mask(n, pts):
    img = Image.new("L", (n, n), 0)
    ImageDraw.Draw(img).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    return np.asarray(img, np.float32) / 255.0


def line_mask(n, pts, width):
    img = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(img)
    d.line([(float(x), float(y)) for x, y in pts], fill=255, width=int(width), joint="curve")
    r = width / 2
    for x, y in pts:
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return np.asarray(img, np.float32) / 255.0


def tube(c, pts, width, color, light=True):
    """A shaded limb: dark core outline, colour, highlight stripe."""
    c.put(line_mask(c.n, pts, width), mix(color, (0, 0, 0), 0.45))
    c.put(line_mask(c.n, pts, width * 0.62), color)
    if light:
        hl = [(x - width * 0.12, y - width * 0.12) for x, y in pts]
        c.put(line_mask(c.n, hl, max(2, width * 0.18)), mix(color, (255, 255, 255), 0.55), 0.8)


def outline(img, color=(10, 10, 16), px=4):
    a = img.split()[3]
    grown = a.filter(ImageFilter.MaxFilter(px * 2 + 1))
    base = Image.new("RGBA", img.size, color + (0,))
    base.putalpha(grown.point(lambda v: 255 if v > 40 else 0))
    base.alpha_composite(img)
    return base


# --- Pet ----------------------------------------------------------------------------------------

def draw_pet(tier, rnd):
    N = PET * S
    c = Canvas(N)
    pal = dict(rnd.choice(PALETTES[tier]))
    # small per-pet hue jitter so no two share exact colours
    j = rnd.uniform(-18, 18)
    pal["body"] = tuple(int(np.clip(v + j * (1 if i == rnd.randrange(3) else 0.3), 0, 255)) for i, v in enumerate(pal["body"]))
    body, trim, accent, eye = pal["body"], pal["trim"], pal["accent"], pal["eye"]
    shape = rnd.choice(BODIES)
    legs = rnd.choice(LEGS)
    eyes = rnd.choice(EYES)
    topper = rnd.choice(TOPPERS[tier])

    cx = N * 0.5
    cy = N * 0.52
    rx, ry = N * 0.29, N * 0.27
    if shape == "wide": rx, ry = N * 0.33, N * 0.24
    if shape == "tall": rx, ry = N * 0.25, N * 0.30
    if shape == "egg": rx, ry = N * 0.27, N * 0.29
    if shape == "capsule": rx, ry = N * 0.31, N * 0.25
    rx *= rnd.uniform(0.92, 1.05); ry *= rnd.uniform(0.92, 1.05)
    if legs == "hover": cy -= N * 0.03
    bottom = cy + ry

    # Back-layer FX
    if tier == "ABYSSAL" and topper.startswith("flame"):
        pass
    if tier == "CELESTIAL" and rnd.random() < 0.55:  # wings
        for sgn in (-1, 1):
            for k in range(3):
                ang = -0.5 - k * 0.35
                bx = cx + sgn * rx * 0.75
                by = cy - ry * 0.1
                tipx = bx + sgn * math.cos(ang) * N * (0.3 - k * 0.04)
                tipy = by + math.sin(ang) * N * (0.22 - k * 0.03)
                m = poly_mask(N, [(bx, by - N * 0.04), (tipx, tipy), (bx + sgn * N * 0.1, by + N * 0.06)])
                c.put(m, mix((250, 250, 255), accent, 0.15 + k * 0.1))
    if tier == "GLITCHED" and rnd.random() < 0.4:  # side fins
        for sgn in (-1, 1):
            m = poly_mask(N, [(cx + sgn * rx * 0.8, cy - ry * 0.3), (cx + sgn * (rx + N * 0.14), cy - ry * 0.6), (cx + sgn * rx * 0.9, cy + ry * 0.2)])
            c.put(m, accent)

    # Legs (behind body)
    leg_col = mix(trim, body, 0.35)
    if legs in ("spider", "claws", "tripod"):
        n = 2 if legs != "tripod" else 1
        for sgn in (-1, 1):
            for k in range(n):
                hx = cx + sgn * rx * (0.35 + 0.35 * k)
                hy = cy + ry * 0.55
                kx = hx + sgn * N * (0.12 + 0.05 * k)
                ky = hy - N * (0.07 - 0.03 * k)
                fx = kx + sgn * N * (0.05 + 0.02 * k)
                fy = bottom + N * (0.10 - 0.015 * k)
                tube(c, [(hx, hy), (kx, ky)], N * 0.075, leg_col)
                tube(c, [(kx, ky), (fx, fy - N * 0.02)], N * 0.06, leg_col)
                shaded_ellipse(c, kx, ky, N * 0.035, N * 0.035, mix(leg_col, accent, 0.3), spec=0.6)
                tip = poly_mask(N, [(fx - N * 0.028, fy - N * 0.035), (fx + sgn * N * 0.01, fy + N * 0.02), (fx + N * 0.028, fy - N * 0.035)])
                c.put(tip, accent if legs == "claws" else mix(leg_col, (255, 255, 255), 0.25))
        if legs == "tripod":
            tube(c, [(cx, bottom - N * 0.02), (cx, bottom + N * 0.10)], N * 0.075, leg_col)
            shaded_ellipse(c, cx, bottom + N * 0.11, N * 0.05, N * 0.025, accent, spec=0.5)
    elif legs == "stubby":
        for sgn in (-1, 1):
            shaded_ellipse(c, cx + sgn * rx * 0.5, bottom + N * 0.02, N * 0.08, N * 0.055, trim, spec=0.4)
    elif legs == "hover":
        thr = ellipse_mask(c, cx, bottom + N * 0.09, N * 0.10, N * 0.035)
        c.glow(thr, accent, 6 * S / 4, 1.0)
        shaded_ellipse(c, cx, bottom + N * 0.02, N * 0.11, N * 0.05, trim, spec=0.5)

    # Tail (behind)
    if rnd.random() < 0.3:
        sgn = rnd.choice([-1, 1])
        pts = [(cx + sgn * rx * 0.6, cy + ry * 0.3), (cx + sgn * (rx + N * 0.08), cy + ry * 0.1), (cx + sgn * (rx + N * 0.12), cy - ry * 0.4)]
        tube(c, pts, N * 0.05, mix(body, trim, 0.3))
        tip = ellipse_mask(c, pts[-1][0], pts[-1][1], N * 0.035, N * 0.035)
        c.glow(tip, accent, 3, 0.8); c.put(tip, accent)

    # Side pods (like the owner's pets)
    pod = rnd.random() < 0.65
    if pod:
        pr = N * rnd.uniform(0.07, 0.1)
        for sgn in (-1, 1):
            px = cx + sgn * rx * 0.98
            shaded_ellipse(c, px, cy + N * 0.02, pr, pr * 1.1, trim, spec=0.5)
            c.put(ellipse_mask(c, px, cy + N * 0.02, pr * 0.55, pr * 0.6), accent)

    # Body (a "headed" build has a smaller torso and a head on top)
    if shape == "headed":
        tx, ty = cx, cy + ry * 0.35
        shaded_ellipse(c, tx, ty, rx * 0.8, ry * 0.62, mix(body, trim, 0.25), spec=0.7)
        c.put(line_mask(N, [(tx - rx * 0.5, ty + ry * 0.1), (tx + rx * 0.5, ty + ry * 0.1)], S * 2) * ellipse_mask(c, tx, ty, rx * 0.8, ry * 0.62), accent, 0.8)
        cy -= ry * 0.3
        rx *= 0.86; ry *= 0.8
    body_mask = shaded_ellipse(c, cx, cy, rx, ry, body, spec=1.0, rim=accent if tier in ("ENCRYPTED", "VOLTAIC") else None)
    # plating seam
    seam = line_mask(N, [(cx - rx * 0.95, cy - ry * 0.05), (cx - rx * 0.4, cy + ry * 0.12), (cx + rx * 0.4, cy + ry * 0.12), (cx + rx * 0.95, cy - ry * 0.05)], S * 1.5)
    c.put(seam * body_mask, mix(body, (0, 0, 0), 0.5), 0.7)

    # Tier surface details
    if tier == "FORGED" or tier == "ABYSSAL":
        for _ in range(rnd.randint(2, 4)):
            x0 = cx + rnd.uniform(-rx * 0.8, rx * 0.8); y0 = cy + rnd.uniform(0, ry * 0.8)
            pts = [(x0, y0)]
            for _k in range(3):
                pts.append((pts[-1][0] + rnd.uniform(-N * 0.05, N * 0.05), pts[-1][1] + rnd.uniform(-N * 0.05, N * 0.02)))
            crack = line_mask(N, pts, S * 1.6) * body_mask
            c.glow(crack, accent, 3, 0.9)
            c.put(crack, mix(accent, (255, 255, 200), 0.4))
    if tier == "ENCRYPTED":
        for _ in range(rnd.randint(3, 6)):
            x0 = cx + rnd.uniform(-rx * 0.8, rx * 0.8); y0 = cy + rnd.uniform(-ry * 0.2, ry * 0.85)
            dx = rnd.choice([-1, 1]) * N * rnd.uniform(0.05, 0.12)
            pts = [(x0, y0), (x0 + dx, y0), (x0 + dx, y0 + N * rnd.uniform(-0.05, 0.05))]
            tr = line_mask(N, pts, S * 1.2) * body_mask
            c.glow(tr, accent, 2, 0.6)
            c.put(tr, accent)
            c.put(ellipse_mask(c, pts[-1][0], pts[-1][1], S * 1.6, S * 1.6) * body_mask, mix(accent, (255, 255, 255), 0.5))
    if tier == "VOLTAIC":
        stripe = poly_mask(N, [(cx - rx * 0.2, cy - ry), (cx + rx * 0.05, cy - ry), (cx - rx * 0.1, cy), (cx + rx * 0.15, cy), (cx - rx * 0.25, cy + ry), (cx - rx * 0.1, cy + ry * 0.05), (cx - rx * 0.35, cy + ry * 0.05)])
        if rnd.random() < 0.6:
            c.put(stripe * body_mask * 0.0 + stripe * 0, accent)  # (kept off-face; arcs added below)
    if tier == "CELESTIAL":
        trimband = line_mask(N, [(cx - rx * 0.9, cy + ry * 0.35), (cx + rx * 0.9, cy + ry * 0.35)], S * 2.2) * body_mask
        c.put(trimband, (255, 214, 90), 0.9)

    # Face visor
    vw = rx * rnd.uniform(0.66, 0.82)
    vh = ry * rnd.uniform(0.48, 0.6)
    vy = cy - ry * 0.05
    if eyes == "visor":
        vh *= 0.75
    visor = shaded_ellipse(c, cx, vy, vw, vh, (14, 16, 26), spec=0.6, amb=0.8)
    # eyes
    def eye_at(ex, ey, er, ery=None):
        ery = ery or er
        m = ellipse_mask(c, ex, ey, er, ery)
        c.glow(m, eye, 3 * S / 4, 0.9)
        c.put(m, eye)
        c.put(ellipse_mask(c, ex, ey + ery * 0.15, er * 0.55, ery * 0.55), mix(eye, (0, 0, 0), 0.35))
        c.put(ellipse_mask(c, ex - er * 0.35, ey - ery * 0.35, er * 0.28, ery * 0.28), (255, 255, 255))
    if eyes == "two":
        er = vh * rnd.uniform(0.5, 0.62)
        for sgn in (-1, 1): eye_at(cx + sgn * vw * 0.42, vy, er * 0.8, er)
    elif eyes == "big":
        eye_at(cx, vy, vh * 0.7, vh * 0.7)
        c.put(ellipse_mask(c, cx, vy, vh * 0.25, vh * 0.25), (10, 10, 20))
    elif eyes == "one":
        er = vh * 0.5
        eye_at(cx, vy, er * 1.2, er)
    elif eyes == "three":
        er = vh * 0.36
        for k, sx in enumerate((-0.5, 0, 0.5)): eye_at(cx + sx * vw, vy + (0 if k == 1 else vh * 0.1) - (vh * 0.12 if k == 1 else 0), er, er)
    elif eyes == "slit":
        for sgn in (-1, 1):
            m = poly_mask(N, [(cx + sgn * vw * 0.15, vy - vh * 0.05), (cx + sgn * vw * 0.75, vy - vh * 0.35), (cx + sgn * vw * 0.7, vy + vh * 0.15)])
            c.glow(m, eye, 3, 0.9); c.put(m, eye)
    elif eyes == "visor":
        m = poly_mask(N, [(cx - vw * 0.75, vy - vh * 0.2), (cx + vw * 0.75, vy - vh * 0.2), (cx + vw * 0.65, vy + vh * 0.3), (cx - vw * 0.65, vy + vh * 0.3)])
        c.glow(m, eye, 3, 0.9); c.put(m, eye)
        c.put(line_mask(N, [(cx - vw * 0.6, vy - vh * 0.08), (cx + vw * 0.6, vy - vh * 0.08)], S), (255, 255, 255), 0.7)
    if rnd.random() < 0.35:  # little mouth / grille
        c.put(line_mask(N, [(cx - vw * 0.18, vy + vh * 0.65), (cx + vw * 0.18, vy + vh * 0.65)], S * 1.3), mix(eye, (0, 0, 0), 0.3), 0.8)

    # Topper
    top = cy - ry
    if topper == "antenna" or topper == "rods":
        n = 1 if topper == "antenna" else 2
        for k in range(n):
            ox = 0 if n == 1 else (-1 if k == 0 else 1) * rx * 0.35
            tube(c, [(cx + ox, top + N * 0.02), (cx + ox * 1.2, top - N * 0.12)], N * 0.03, trim)
            m = ellipse_mask(c, cx + ox * 1.2, top - N * 0.13, N * 0.03, N * 0.03)
            c.glow(m, accent, 3, 1.0); c.put(m, accent)
    elif topper in ("ears", "bolt_ears"):
        for sgn in (-1, 1):
            bx = cx + sgn * rx * 0.45
            pts = [(bx - sgn * N * 0.05, top + N * 0.06), (bx + sgn * N * 0.06, top - N * 0.16), (bx + sgn * N * 0.08, top + N * 0.06)]
            if topper == "bolt_ears":
                pts = [(bx - sgn * N * 0.04, top + N * 0.06), (bx + sgn * N * 0.02, top - N * 0.06), (bx - sgn * N * 0.01, top - N * 0.06), (bx + sgn * N * 0.07, top - N * 0.18), (bx + sgn * N * 0.05, top - N * 0.04), (bx + sgn * N * 0.08, top - N * 0.04), (bx + sgn * N * 0.07, top + N * 0.06)]
            m = poly_mask(N, pts)
            c.put(m, mix(body, (0, 0, 0), 0.15))
            inner = poly_mask(N, [((pts[0][0] * 2 + pts[1][0]) / 3, (pts[0][1] + pts[1][1] * 2) / 3 + N * 0.03), pts[1], ((pts[-1][0] * 2 + pts[1][0]) / 3, (pts[-1][1] * 2 + pts[1][1]) / 3)]) * m
            c.put(inner, accent, 0.9)
    elif topper in ("horns", "horns_big"):
        big = 1.5 if topper == "horns_big" else 1.0
        for sgn in (-1, 1):
            bx = cx + sgn * rx * 0.5
            pts = [(bx - sgn * N * 0.04, top + N * 0.05), (bx + sgn * N * 0.10 * big, top - N * 0.14 * big), (bx + sgn * N * 0.05, top + N * 0.06)]
            m = poly_mask(N, pts)
            c.put(m, (40, 30, 34) if tier == "ABYSSAL" else mix(trim, (255, 255, 255), 0.2))
            c.put(line_mask(N, [(bx, top + N * 0.03), (bx + sgn * N * 0.08 * big, top - N * 0.11 * big)], S * 1.2) * m, accent, 0.7)
    elif topper == "crest" or topper == "plates":
        for k in range(3 if topper == "crest" else 2):
            ox = (k - (1 if topper == "crest" else 0.5)) * N * 0.07
            h = N * (0.12 - abs(ox) / N * 0.5)
            m = poly_mask(N, [(cx + ox - N * 0.035, top + N * 0.04), (cx + ox, top - h), (cx + ox + N * 0.035, top + N * 0.04)])
            c.put(m, accent if topper == "crest" else trim)
    elif topper == "crown":
        pts = [(cx - rx * 0.45, top + N * 0.03)]
        for k in range(5):
            x = cx - rx * 0.45 + (k + 0.5) * rx * 0.18
            pts += [(x, top - N * (0.1 if k % 2 == 0 else 0.05)), (x + rx * 0.09, top - N * 0.01)]
        pts += [(cx + rx * 0.45, top + N * 0.03)]
        m = poly_mask(N, pts)
        c.put(m, (255, 214, 60) if tier != "GLITCHED" else accent)
        c.put(m * (c.y < top - N * 0.06), (255, 250, 210), 0.6)
    elif topper == "propeller":
        tube(c, [(cx, top + N * 0.02), (cx, top - N * 0.07)], N * 0.03, trim)
        blade = ellipse_mask(c, cx, top - N * 0.08, N * 0.24, N * 0.025)
        c.put(blade, mix(accent, (255, 255, 255), 0.2), 0.9)
    elif topper in ("halo", "halo_glitch", "halo_code"):
        hy = top - N * 0.08
        ring = np.clip(ellipse_mask(c, cx, hy, N * 0.22, N * 0.06) - ellipse_mask(c, cx, hy, N * 0.17, N * 0.035), 0, 1)
        col = (255, 214, 90) if topper == "halo" else accent
        c.glow(ring, col, 4, 0.9)
        c.put(ring, mix(col, (255, 255, 255), 0.3))
    elif topper == "star":
        sx, sy = cx, top - N * 0.1
        pts = []
        for k in range(10):
            r = N * (0.09 if k % 2 == 0 else 0.04)
            a = -math.pi / 2 + k * math.pi / 5
            pts.append((sx + math.cos(a) * r, sy + math.sin(a) * r))
        m = poly_mask(N, pts)
        c.glow(m, (255, 230, 120), 4, 0.9); c.put(m, (255, 240, 160))
    elif topper == "flame":
        for k in range(3):
            ox = (k - 1) * N * 0.07
            h = N * (0.22 if k == 1 else 0.15) * rnd.uniform(0.85, 1.15)
            pts = [(cx + ox - N * 0.06, top + N * 0.06), (cx + ox - N * 0.02, top - h * 0.5), (cx + ox + rnd.uniform(-0.02, 0.02) * N, top - h), (cx + ox + N * 0.03, top - h * 0.45), (cx + ox + N * 0.06, top + N * 0.06)]
            m = poly_mask(N, pts)
            c.glow(m, accent, 5, 0.8)
            c.put(m, accent)
            inner = poly_mask(N, [(cx + ox - N * 0.03, top + N * 0.05), (cx + ox, top - h * 0.55), (cx + ox + N * 0.03, top + N * 0.05)])
            c.put(inner, (255, 220, 120))
    elif topper == "chimney":
        for sgn in (-1, 1):
            x = cx + sgn * rx * 0.4
            tube(c, [(x, top + N * 0.04), (x, top - N * 0.08)], N * 0.06, trim)
            m = ellipse_mask(c, x, top - N * 0.09, N * 0.03, N * 0.015)
            c.glow(m, accent, 4, 1.0); c.put(m, accent)
    elif topper == "coil":
        tube(c, [(cx, top + N * 0.02), (cx, top - N * 0.12)], N * 0.035, trim)
        for k in range(3):
            y = top - N * (0.02 + k * 0.04)
            c.put(ellipse_mask(c, cx, y, N * 0.07, N * 0.015), (200, 120, 50))
        m = ellipse_mask(c, cx, top - N * 0.14, N * 0.035, N * 0.035)
        c.glow(m, accent, 4, 1.0); c.put(m, mix(accent, (255, 255, 255), 0.4))
    elif topper == "dish":
        tube(c, [(cx, top + N * 0.02), (cx + N * 0.05, top - N * 0.07)], N * 0.03, trim)
        m = poly_mask(N, [(cx - N * 0.02, top - N * 0.14), (cx + N * 0.14, top - N * 0.04), (cx + N * 0.06, top - N * 0.12)])
        c.put(m, mix(body, (255, 255, 255), 0.3))
        c.put(ellipse_mask(c, cx + N * 0.05, top - N * 0.09, N * 0.015, N * 0.015), accent)
    elif topper == "shards":
        for k in range(3):
            ox = (k - 1) * N * 0.08
            h = N * rnd.uniform(0.08, 0.15)
            m = poly_mask(N, [(cx + ox - N * 0.03, top + N * 0.04), (cx + ox + rnd.uniform(-0.02, 0.02) * N, top - h), (cx + ox + N * 0.03, top + N * 0.04)])
            c.glow(m, accent, 3, 0.7); c.put(m, mix(accent, (255, 255, 255), 0.25))

    # Front FX
    if tier == "VOLTAIC":
        for _ in range(2):
            sgn = rnd.choice([-1, 1])
            x = cx + sgn * (rx + N * 0.05)
            y = cy - ry * 0.6
            pts = [(x, y)]
            for _k in range(4):
                pts.append((pts[-1][0] + sgn * rnd.uniform(-N * 0.04, N * 0.04), pts[-1][1] + N * 0.06))
            m = line_mask(N, pts, S * 1.3)
            c.glow(m, accent, 3, 1.0); c.put(m, (255, 255, 230))
    if tier == "CELESTIAL" or tier == "ENCRYPTED":
        for _ in range(rnd.randint(2, 4)):
            x = rnd.uniform(N * 0.1, N * 0.9); y = rnd.uniform(N * 0.1, N * 0.9)
            if ellipse_mask(c, cx, cy, rx * 1.1, ry * 1.1)[int(y), int(x)] > 0: continue
            r = N * 0.03
            if tier == "CELESTIAL":
                m = np.maximum(line_mask(N, [(x - r, y), (x + r, y)], S), line_mask(N, [(x, y - r), (x, y + r)], S))
                c.glow(m, (255, 255, 220), 2, 0.8); c.put(m, (255, 255, 240))
            else:
                m = poly_mask(N, [(x - S * 2, y - S * 3), (x + S * 2, y - S * 3), (x + S * 2, y + S * 3), (x - S * 2, y + S * 3)])
                c.put(m, accent, 0.85)
    if tier == "ABYSSAL" or tier == "FORGED":
        for _ in range(rnd.randint(2, 5)):
            x = cx + rnd.uniform(-rx * 1.3, rx * 1.3); y = cy - ry * rnd.uniform(0.3, 1.6)
            m = ellipse_mask(c, x, y, S * 1.6, S * 1.6)
            c.glow(m, accent, 2, 0.9); c.put(m, (255, 200, 120))

    img = c.image()
    img = outline(img, mix(trim, (0, 0, 0), 0.7), px=S)
    # Glitch: tear the finished sprite into offset slices with chroma split.
    if tier == "GLITCHED":
        arr = np.array(img)
        for _ in range(rnd.randint(2, 4)):
            y0 = rnd.randint(int(N * 0.15), int(N * 0.85)); h = rnd.randint(S * 2, S * 5)
            dx = rnd.choice([-1, 1]) * rnd.randint(S * 2, S * 4)
            arr[y0:y0 + h] = np.roll(arr[y0:y0 + h], dx, axis=1)
        img = Image.fromarray(arr, "RGBA")
        r, g, b, a = img.split()
        sh = S * 1
        mag = Image.merge("RGBA", (r, Image.new("L", img.size, 0), b, a.point(lambda v: v * 0.45))).transform(img.size, Image.AFFINE, (1, 0, sh, 0, 1, 0))
        cyan = Image.merge("RGBA", (Image.new("L", img.size, 0), g, b, a.point(lambda v: v * 0.45))).transform(img.size, Image.AFFINE, (1, 0, -sh, 0, 1, 0))
        base = Image.new("RGBA", img.size, (0, 0, 0, 0))
        base.alpha_composite(mag); base.alpha_composite(cyan); base.alpha_composite(img)
        img = base
    return img.resize((PET, PET), Image.LANCZOS), pal


# --- Egg ----------------------------------------------------------------------------------------

def draw_egg(tier, pal, rnd):
    N = EGG * S
    c = Canvas(N)
    body, trim, accent = pal["body"], pal["trim"], pal["accent"]
    cx, cy = N * 0.5, N * 0.55
    rx, ry = N * 0.30, N * 0.38
    # egg: narrower top
    m = ellipse_mask(c, cx, cy, rx, ry)
    taper = np.clip(1 - np.clip((cy - c.y) / ry, 0, 1) * 0.28, 0, 1)
    m = ellipse_mask(c, cx, cy, rx * 1.0, ry) * (np.abs(c.x - cx) <= rx * taper)
    nx = (c.x - cx) / (rx * np.maximum(taper, 0.3)); ny = (c.y - cy) / ry
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    L = np.array([-0.5, -0.65, 0.58]); L /= np.linalg.norm(L)
    diff = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    col = np.asarray(body, np.float32)[None, None, :] * (0.4 + 0.6 * diff)[..., None]
    H = L + np.array([0, 0, 1.0]); H /= np.linalg.norm(H)
    col = col + 255 * (np.clip(nx * H[0] + ny * H[1] + nz * H[2], 0, 1) ** 30)[..., None]
    c.put(m, col)
    # pattern
    pat = rnd.choice(["bands", "spots", "cracks", "plates"])
    if tier == "ENCRYPTED": pat = "circuit"
    if tier == "GLITCHED" and rnd.random() < 0.5: pat = "glitch"
    if pat == "bands":
        for k in range(2):
            y = cy - ry * 0.2 + k * ry * 0.45
            c.put(line_mask(N, [(cx - rx, y), (cx + rx, y + N * 0.03)], S * 2.2) * m, accent, 0.9)
    elif pat == "spots":
        for _ in range(6):
            x = cx + rnd.uniform(-rx * 0.7, rx * 0.7); y = cy + rnd.uniform(-ry * 0.6, ry * 0.7)
            c.put(ellipse_mask(c, x, y, N * 0.04, N * 0.035) * m, accent, 0.9)
    elif pat == "cracks":
        for _ in range(3):
            x0 = cx + rnd.uniform(-rx * 0.6, rx * 0.6); y0 = cy + rnd.uniform(-ry * 0.5, ry * 0.5)
            pts = [(x0, y0)]
            for _k in range(3): pts.append((pts[-1][0] + rnd.uniform(-N * 0.06, N * 0.06), pts[-1][1] + rnd.uniform(-N * 0.06, N * 0.06)))
            cm = line_mask(N, pts, S * 1.5) * m
            c.glow(cm, accent, 3, 0.9); c.put(cm, mix(accent, (255, 255, 220), 0.4))
    elif pat == "plates":
        for k in range(3):
            y = cy - ry * 0.5 + k * ry * 0.45
            c.put(line_mask(N, [(cx - rx, y), (cx + rx, y)], S * 1.2) * m, trim, 0.9)
            c.put(ellipse_mask(c, cx + rx * 0.5 * (k - 1), y + ry * 0.2, S * 2, S * 2) * m, accent)
    elif pat == "circuit":
        for _ in range(6):
            x0 = cx + rnd.uniform(-rx * 0.7, rx * 0.7); y0 = cy + rnd.uniform(-ry * 0.6, ry * 0.7)
            dx = rnd.choice([-1, 1]) * N * 0.08
            pts = [(x0, y0), (x0 + dx, y0), (x0 + dx, y0 + N * 0.05)]
            tr = line_mask(N, pts, S * 1.2) * m
            c.glow(tr, accent, 2, 0.7); c.put(tr, accent)
    elif pat == "glitch":
        for _ in range(3):
            y = cy + rnd.uniform(-ry * 0.6, ry * 0.6)
            c.put((np.abs(c.y - y) < S * 1.6) * m, rnd.choice([accent, (61, 242, 255), (255, 214, 60)]), 0.85)
    # tier touch
    if tier == "CELESTIAL":
        ring = np.clip(ellipse_mask(c, cx, cy - ry * 1.02, N * 0.18, N * 0.05) - ellipse_mask(c, cx, cy - ry * 1.02, N * 0.13, N * 0.028), 0, 1)
        c.glow(ring, (255, 214, 90), 3, 0.9); c.put(ring, (255, 236, 150))
    if tier in ("ABYSSAL", "FORGED"):
        c.glow(m * (c.y > cy + ry * 0.4), accent, 6, 0.7)
    if tier == "VOLTAIC":
        pts = [(cx - rx * 0.1, cy - ry * 0.7), (cx + rx * 0.2, cy - ry * 0.1), (cx - rx * 0.15, cy), (cx + rx * 0.15, cy + ry * 0.6)]
        b = line_mask(N, pts, S * 1.8) * m
        c.glow(b, accent, 3, 1.0); c.put(b, (255, 255, 220))
    img = outline(c.image(), mix(trim, (0, 0, 0), 0.7), px=S)
    if tier == "GLITCHED":
        arr = np.array(img)
        y0 = rnd.randint(int(N * 0.3), int(N * 0.7)); h = S * 3
        arr[y0:y0 + h] = np.roll(arr[y0:y0 + h], rnd.choice([-1, 1]) * S * 3, axis=1)
        img = Image.fromarray(arr, "RGBA")
    return img.resize((EGG, EGG), Image.LANCZOS)


def main():
    os.makedirs(DOCS, exist_ok=True)
    pets, eggs, meta = [], [], []
    seen = set()
    idx = 0
    for tier, count in TIERS:
        for k in range(count):
            rnd = random.Random(f"pet-{tier}-{k}")
            pet, pal = draw_pet(tier, rnd)
            egg = draw_egg(tier, pal, random.Random(f"egg-{tier}-{k}"))
            words = TIER_WORDS[tier]
            # unique names
            # Walk every word x species pair (from a per-pet start) until one is unused.
            for t in range(len(words) * len(SPECIES)):
                name = f"{words[(k + t) % len(words)]} {SPECIES[(k * 7 + idx + t // len(words)) % len(SPECIES)]}"
                if name not in seen: break
            seen.add(name)
            egg_name = f"{EGG_WORDS[tier][k % 4]} {['Core', 'Shell', 'Prism', 'Vault', 'Rift', 'Nexus'][(k // 4) % 6]} Egg"
            pets.append(pet); eggs.append(egg); meta.append(f"{tier}|{name}|{egg_name}")
            idx += 1
    print("generated", len(pets))

    def pages(imgs, px, prefix):
        for p in range((len(imgs) + PER_PAGE - 1) // PER_PAGE):
            page = Image.new("RGBA", (COLS * px, COLS * px), (0, 0, 0, 0))
            for i, im in enumerate(imgs[p * PER_PAGE:(p + 1) * PER_PAGE]):
                page.alpha_composite(im, ((i % COLS) * px, (i // COLS) * px))
            page.save(os.path.join(RES, f"{prefix}_{4 + p}.webp"), "WEBP", lossless=True, method=6)
    pages(pets, PET, "pets")
    pages(eggs, EGG, "eggs")
    with open(os.path.join(RES, "new_pets.txt"), "w") as f:
        f.write("\n".join(meta) + "\n")

    # Review sheets: one per tier, pets at 2x with their egg and name.
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 11)
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
    except OSError:
        font = big = ImageFont.load_default()
    start = 0
    for tier, count in TIERS:
        cols = 10
        cw, ch = 150, 172
        rows = (count + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cw + 20, rows * ch + 70), (14, 18, 28, 255))
        d = ImageDraw.Draw(sheet)
        d.text((14, 16), f"{tier} PETS  ·  {count}  (#{start + 1}–{start + count} of the 500 new)", fill=TIER_COLOR[tier] + (255,), font=big)
        for i in range(count):
            j = start + i
            x = 10 + (i % cols) * cw; y = 60 + (i // cols) * ch
            d.rounded_rectangle([x + 3, y + 3, x + cw - 3, y + ch - 3], 8, fill=(22, 28, 42, 255), outline=TIER_COLOR[tier] + (120,))
            sheet.alpha_composite(pets[j].resize((128, 128), Image.NEAREST), (x + 11, y + 6))
            sheet.alpha_composite(eggs[j].resize((40, 40), Image.LANCZOS), (x + cw - 46, y + 96))
            name = meta[j].split("|")[1]
            d.text((x + 9, y + ch - 30), name, fill=(230, 236, 245, 255), font=font)
            d.text((x + 9, y + ch - 16), f"#{j + 1}", fill=(140, 150, 170, 255), font=font)
        sheet.save(os.path.join(DOCS, f"pets_{tier.lower()}.png"))
        start += count
    # one overview of all 500 at 1x
    ov = Image.new("RGBA", (25 * 68 + 8, 20 * 68 + 8), (14, 18, 28, 255))
    for i, p in enumerate(pets):
        ov.alpha_composite(p, (4 + (i % 25) * 68, 4 + (i // 25) * 68))
    ov.save(os.path.join(DOCS, "all_500_pets.png"))
    eo = Image.new("RGBA", (25 * 52 + 8, 20 * 52 + 8), (14, 18, 28, 255))
    for i, e in enumerate(eggs):
        eo.alpha_composite(e, (4 + (i % 25) * 52, 4 + (i // 25) * 52))
    eo.save(os.path.join(DOCS, "all_500_eggs.png"))


if __name__ == "__main__":
    sys.exit(main())
