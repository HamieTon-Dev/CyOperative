"""
The egg-hatch sound (owner, 0.45.2: "add a sound effect when the egg hatches"): a shell crack,
a soft pop, then a rising shimmer of bell tones with a sparkle tail. 44.1 kHz mono 16-bit like
the other recorded effects. Writes app/src/main/resources/sfx/pet_hatch.wav.
"""
import os
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "resources", "sfx", "pet_hatch.wav")


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-t * decay)


def place(buf, sig, at):
    i = int(at * SR)
    buf[i:i + len(sig)] += sig[: max(0, len(buf) - i)]


def main():
    rng = np.random.default_rng(42)
    total = int(1.7 * SR)
    out = np.zeros(total)
    # Shell crack: two bright noise snaps.
    for at, amp in ((0.0, 0.55), (0.045, 0.4)):
        n = int(0.05 * SR)
        noise = rng.normal(0, 1, n)
        noise = np.diff(np.concatenate([[0], noise]))  # tilt toward the highs
        place(out, noise * env(n, 0.0005, 70) * amp, at)
    # Pop: a short low thump sweeping down.
    n = int(0.18 * SR)
    t = np.arange(n) / SR
    f = 230 * np.exp(-t * 9) + 70
    place(out, np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 18) * 0.7, 0.03)
    # Shimmer: a rising arpeggio of soft bells (C6 E6 G6 C7 E7).
    for k, freq in enumerate((1046.5, 1318.5, 1568.0, 2093.0, 2637.0)):
        n = int(0.9 * SR)
        t = np.arange(n) / SR
        bell = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2.01 * t) + 0.12 * np.sin(2 * np.pi * freq * 3.02 * t)
        place(out, bell * env(n, 0.004, 5.5) * 0.22, 0.09 + k * 0.07)
    # Sparkle tail: little high pings fading out.
    for i in range(18):
        at = 0.35 + i * 0.06 + rng.uniform(0, 0.03)
        freq = rng.uniform(2800, 6200)
        n = int(0.12 * SR)
        t = np.arange(n) / SR
        place(out, np.sin(2 * np.pi * freq * t) * env(n, 0.002, 30) * 0.08 * (1 - i / 20), at)
    # Gentle fade and normalise to about -3 dBFS.
    out *= np.clip((len(out) - np.arange(len(out))) / (0.25 * SR), 0, 1)
    out = out / np.max(np.abs(out)) * 0.7
    pcm = (out * 32767).astype(np.int16)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print("ok", os.path.getsize(OUT))


if __name__ == "__main__":
    main()
