"""0.49.1 pets (owner: "add 100 more glitched legendary … 100 more glitched encrypted … 100 new
Encrypted pets … new and unused sprites").

300 pets with their own eggs, appended after the 1,050 from gen_new_pets.py + gen_gx_pets.py:
  new-pet 1050..1149  GLITCHED (gold / magenta, torn, with a magenta-cyan GLITCH flicker)
  new-pet 1150..1249  GLITCHED_ENCRYPTED (cyan <-> red, like gen_gx_pets.py)
  new-pet 1250..1349  ENCRYPTED (animated, in the 0.41.0 colourways and a few new ones)
Every one uses at least one part no earlier pet has (cube / twin / pear bodies, wheels or
tentacles, x / ring / pixel eyes, fin / mohawk / satellite / tech-horn toppers), so none of them
repeats an older design.

Run AFTER gen_new_pets.py and gen_gx_pets.py: python3 tools/pets/gen_more_pets.py
"""
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_new_pets as g  # noqa: E402
import gen_gx_pets as gx  # noqa: E402

FIRST = 1050
NEW_BODIES = ["cube", "twin", "pear"]
NEW_LEGS = ["wheels", "tentacles"]
NEW_EYES = ["x", "ring", "pixel"]
NEW_TOPS = ["fin", "mohawk", "sat", "horns_tech"]
GLITCH_WORDS = ["Byteshift", "Stutter", "Misfire", "Hexrot", "Tearline", "Skipframe", "Pixelrot", "Jitter", "Smear", "Bitflip"]
ENC_WORDS = ["Keystone", "Ciphon", "Hashling", "Nonce", "Checksum", "Payload", "Firmware", "Bootkey", "Signet", "Entropy"]
GX_WORDS = ["Splitframe", "Redline", "Crossfade", "Bluescreen", "Wraparound", "Deadpixel", "Overrun", "Mirrorkey", "Phaseslip", "Ghostbit"]
NEW_COLORWAYS = [
    ("Teal", (20, 170, 160), (6, 60, 60), (120, 255, 230), (220, 255, 250)),
    ("Crimson", (170, 20, 50), (60, 6, 16), (255, 110, 140), (255, 220, 230)),
    ("Lime", (150, 230, 40), (50, 80, 10), (220, 255, 120), (245, 255, 220)),
    ("Indigo", (70, 50, 200), (24, 16, 80), (160, 140, 255), (225, 220, 255)),
    ("Rose", (240, 120, 160), (100, 30, 60), (255, 200, 220), (255, 240, 245)),
    ("Steel", (120, 136, 156), (40, 46, 56), (0, 240, 255), (220, 245, 255)),
]
GLITCH_PALS = [
    dict(body=(250, 200, 70), trim=(120, 76, 16), accent=(255, 61, 242), eye=(61, 242, 255)),
    dict(body=(120, 40, 140), trim=(40, 12, 50), accent=(255, 214, 60), eye=(255, 61, 242)),
    dict(body=(30, 30, 44), trim=(10, 10, 16), accent=(61, 242, 255), eye=(255, 61, 242)),
    dict(body=(255, 120, 220), trim=(110, 30, 90), accent=(255, 230, 90), eye=(61, 242, 255)),
    dict(body=(200, 210, 230), trim=(80, 90, 110), accent=(255, 61, 242), eye=(255, 214, 60)),
]


def parts(k, rnd):
    """The pet's build, with at least one never-before-used part."""
    new = k % 4
    body = rnd.choice(NEW_BODIES) if new == 0 or rnd.random() < 0.35 else rnd.choice(g.BODIES)
    legs = rnd.choice(NEW_LEGS) if new == 1 or rnd.random() < 0.3 else rnd.choice(g.LEGS)
    eyes = rnd.choice(NEW_EYES) if new == 2 or rnd.random() < 0.35 else rnd.choice(g.EYES)
    top = rnd.choice(NEW_TOPS) if new == 3 or rnd.random() < 0.35 else None
    return body, legs, eyes, top


def draw(tier, k, seed, pal):
    rnd = random.Random(seed)
    body, legs, eyes, top = parts(k, random.Random(seed + "-parts"))
    saved = (g.BODIES, g.LEGS, g.EYES, dict(g.TOPPERS))
    g.BODIES, g.LEGS, g.EYES = [body], [legs], [eyes]
    if top:
        g.TOPPERS[tier] = [top]
    try:
        return g.draw_pet(tier, rnd, pal_override=pal)
    finally:
        g.BODIES, g.LEGS, g.EYES = saved[0], saved[1], saved[2]
        g.TOPPERS.clear(); g.TOPPERS.update(saved[3])


def unique_name(words, k, seen):
    for t in range(len(words) * len(g.SPECIES)):
        name = f"{words[(k + t) % len(words)]} {g.SPECIES[(k * 17 + 3 + t // len(words)) % len(g.SPECIES)]}"
        if name not in seen:
            return name
    return f"{words[k % len(words)]} {k}"


def main():
    path = os.path.join(g.RES, "new_pets.txt")
    old = [l for l in open(path).read().splitlines() if l.strip()][:FIRST]
    seen = {l.split("|")[1] for l in old}
    pets, eggs, meta = [], [], []

    for k in range(100):  # GLITCHED
        pal = dict(GLITCH_PALS[k % len(GLITCH_PALS)])
        pet, pal = draw("GLITCHED", k, f"gl2-{k}", pal)
        egg = g.draw_egg("GLITCHED", pal, random.Random(f"gl2-egg-{k}"))
        name = unique_name(GLITCH_WORDS, k, seen); seen.add(name)
        anim = "GLITCH:1:FF3DF2:1.0" + (";FLOAT:1:FFFFFF:1.0" if k % 3 == 0 else "")
        pets.append(pet); eggs.append(egg)
        meta.append(f"GLITCHED|{name}|{GLITCH_WORDS[k % 10]} {['Core', 'Shell', 'Prism', 'Vault', 'Rift', 'Nexus'][k % 6]} Egg|{anim}")

    for k in range(100):  # GLITCHED ENCRYPTED
        pal = dict(gx.PALS[k % len(gx.PALS)])
        pet, pal = draw("ENCRYPTED", k, f"gx2-{k}", pal)
        egg = g.draw_egg("ENCRYPTED", pal, random.Random(f"gx2-egg-{k}"))
        pets.append(gx.glitch(pet, random.Random(f"gx2-tear-{k}"), 1))
        eggs.append(gx.glitch(egg, random.Random(f"gx2-egg-tear-{k}"), 1))
        name = unique_name(GX_WORDS, k, seen); seen.add(name)
        meta.append(f"GLITCHED_ENCRYPTED|{name}|{GX_WORDS[k % 10]} {['Core', 'Shell', 'Prism', 'Vault', 'Rift', 'Nexus'][k % 6]} Egg|{gx.spec(random.Random(f'gx2-spec-{k}'))}")

    ways = g.COLORWAYS + NEW_COLORWAYS
    for k in range(100):  # ENCRYPTED, animated
        cw = ways[k % len(ways)]
        accent = cw[3]
        pal = dict(body=cw[1], trim=cw[2], accent=(255, 255, 255) if accent == "RAINBOW" else accent, eye=cw[4])
        pet, pal = draw("ENCRYPTED", k, f"enc2-{k}", pal)
        egg = g.draw_egg("ENCRYPTED", pal, random.Random(f"enc2-egg-{k}"))
        pets.append(pet); eggs.append(egg)
        name = unique_name([f"{cw[0]} {w}" for w in ENC_WORDS], k, seen); seen.add(name)
        meta.append(f"ENCRYPTED|{name}|{cw[0]} {ENC_WORDS[k % 10]} Egg|{g.anim_spec(random.Random(f'enc2-spec-{k}'), accent, cw[4])}")

    # Place: global art = 1024 + FIRST + i → page // 256, cell % 256 (page 8 from cell 26, then 9).
    for imgs, px, prefix in ((pets, g.PET, "pets"), (eggs, g.EGG, "eggs")):
        pages = {}
        for i, im in enumerate(imgs):
            idx = 1024 + FIRST + i
            p, c = idx // g.PER_PAGE, idx % g.PER_PAGE
            if p not in pages:
                f = os.path.join(g.RES, f"{prefix}_{p}.webp")
                pages[p] = Image.open(f).convert("RGBA") if os.path.exists(f) else Image.new("RGBA", (g.COLS * px, g.COLS * px), (0, 0, 0, 0))
            pages[p].paste(im, ((c % g.COLS) * px, (c // g.COLS) * px))
        for p, im in pages.items():
            im.save(os.path.join(g.RES, f"{prefix}_{p}.webp"), "WEBP", lossless=True, method=6)
    open(path, "w").write("\n".join(old + meta) + "\n")
    print("pets", len(old) + len(meta))

    # Review sheets (one per tier) and a GIF of a few of each.
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 11)
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 28)
    except OSError:
        font = big = ImageFont.load_default()
    titles = [("GLITCHED LEGENDARY", (255, 61, 242), "glitched_legendary_new_100"),
              ("GLITCHED ENCRYPTED", (255, 46, 77), "glitched_encrypted_new_100"),
              ("ENCRYPTED", (0, 240, 255), "encrypted_new_100")]
    for b, (title, col, fname) in enumerate(titles):
        cols, cw_, ch = 10, 150, 172
        sheet = Image.new("RGBA", (cols * cw_ + 20, 10 * ch + 70), (14, 18, 28, 255))
        d = ImageDraw.Draw(sheet)
        d.text((14, 16), f"{title} PETS  ·  100 NEW", fill=col + (255,), font=big)
        for i in range(100):
            j = b * 100 + i
            x = 10 + (i % cols) * cw_; y = 60 + (i // cols) * ch
            d.rounded_rectangle([x + 3, y + 3, x + cw_ - 3, y + ch - 3], 8, fill=(22, 28, 42, 255), outline=col + (130,))
            sheet.alpha_composite(g.render_anim(pets[j], meta[j].split("|")[3], 0.7 + i * 0.37), (x + 11, y + 6))
            sheet.alpha_composite(eggs[j].resize((40, 40), Image.LANCZOS), (x + cw_ - 46, y + 96))
            nm = meta[j].split("|")[1]
            d.text((x + 9, y + ch - 30), nm if len(nm) <= 22 else nm[:21] + "…", fill=(230, 236, 245, 255), font=font)
            d.text((x + 9, y + ch - 16), meta[j].split("|")[2][:24], fill=(140, 150, 170, 255), font=font)
        sheet.save(os.path.join(g.DOCS, f"{fname}.png"))
    frames = []
    pick = [b * 100 + i * 9 for b in range(3) for i in range(6)]
    for f in range(36):
        t = f / 36 * 2.86
        fr = Image.new("RGBA", (6 * 132 + 4, 3 * 132 + 4), (14, 18, 28, 255))
        for n, j in enumerate(pick):
            fr.alpha_composite(g.render_anim(pets[j], meta[j].split("|")[3], t), (2 + (n % 6) * 132, 2 + (n // 6) * 132))
        frames.append(fr.convert("RGB"))
    frames[0].save(os.path.join(g.DOCS, "new_300_pets.gif"), save_all=True, append_images=frames[1:], duration=70, loop=0)


if __name__ == "__main__":
    sys.exit(main())
