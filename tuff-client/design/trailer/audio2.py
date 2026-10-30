"""Glade teaser mix: the song (muffled before the drop), voice-over with the
music ducked under it, and effects on every cut. Times match trailer2.html.
usage: python3 audio2.py <song.mp3> <voice dir with v1..v6.wav>  -> trailer2.wav"""
import subprocess
import sys
import wave
import numpy as np
import soundfile as sf

SR, DUR, DROP, BEAT = 48000, 13.0, 5.0, 0.4646
END = DROP + 12 * BEAT
SONG_AT = 15.264 - DROP          # the song's first drop kick lands on DROP
N = int(SR * DUR)
rng = np.random.default_rng(5)

raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{SONG_AT:.3f}", "-t", str(DUR), "-i", sys.argv[1],
                      "-ac", "2", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
song = np.frombuffer(raw, np.float32).reshape(-1, 2).astype(np.float64)
song = np.pad(song, ((0, max(0, N - len(song))), (0, 0)))[:N]

# muffled until the drop: a low-pass that opens up over the last 1.2 s, then full
t = np.arange(N) / SR
cut = np.where(t < DROP - 1.2, 500.0, np.where(t < DROP, 500 * (16000 / 500) ** ((t - (DROP - 1.2)) / 1.2), 20000.0))
a = np.exp(-2 * np.pi * cut / SR)
filt = np.empty_like(song)
acc = np.zeros(2)
for i in range(N):
    acc = (1 - a[i]) * song[i] + a[i] * acc
    filt[i] = acc
song = np.where((t < DROP)[:, None], filt * 1.25, song)
song *= np.where(t < DROP, 0.55, 1.0)[:, None]


def env(n, at=0.004, d=0.3):
    k = np.arange(n) / SR
    return np.minimum(1, k / at) * np.exp(-k / d)


def lowpass(x, c):
    k = np.exp(-2 * np.pi * c / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - k) * v + k * acc
        y[i] = acc
    return y


def sweep(dur, f0, f1, rise=True):
    n = int(dur * SR)
    noise = rng.standard_normal(n)
    k = np.exp(-2 * np.pi * np.geomspace(f0, f1, n) / SR)
    y = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc = (1 - k[i]) * noise[i] + k[i] * acc
        y[i] = acc
    shape = np.linspace(0, 1, n) ** 2.5 if rise else np.sin(np.linspace(0, np.pi, n)) ** 2
    return y / (np.abs(y).max() + 1e-9) * shape


def boom(dur=1.4):
    k = np.arange(int(dur * SR)) / SR
    f = 30 + 90 * np.exp(-k * 7)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(k), 0.002, 0.55) + 0.4 * lowpass(rng.standard_normal(len(k)), 300) * env(len(k), 0.002, 0.2)


def glitch(dur=0.12):
    n = int(dur * SR)
    sq = np.sign(np.sin(2 * np.pi * rng.uniform(300, 1400) * np.arange(n) / SR))
    crushed = np.repeat(rng.standard_normal(n // 40 + 1), 40)[:n]
    return (0.5 * sq + 0.6 * crushed) * env(n, 0.001, dur / 2)


def tick():
    n = int(0.03 * SR)
    x = rng.standard_normal(n)
    return (x - lowpass(x, 2500)) * env(n, 0.0005, 0.006)


sfx = np.zeros(N)


def add(sig, at, gain):
    i = int(at * SR)
    sig = sig[: max(0, N - i)]
    sfx[i:i + len(sig)] += sig * gain


for at in (0.25, 0.55, 0.85, 1.05, 1.3, 1.55):          # pop-ups
    add(glitch(0.09), at, 0.18)
for at in (1.72, 2.26, 2.8):                              # BLOATED. LAGGY. PAYWALLED.
    add(boom(0.6), at, 0.5)
    add(glitch(0.16), at, 0.3)
add(glitch(0.1), 3.36, 0.35)                              # screen collapses
for k in range(20):                                       # typing
    add(tick(), 3.5 + k * 0.045 + rng.uniform(0, 0.01), 0.25)
add(sweep(0.75, 200, 12000), DROP - 0.75, 0.55)          # riser into the drop
add(boom(1.6), DROP, 0.9)
add(sweep(0.5, 6000, 200, rise=False), DROP, 0.25)
for k in range(10):                                       # whoosh on each feature
    add(sweep(0.18, 800, 7000, rise=False), DROP + (2 + k) * BEAT - 0.04, 0.12)
add(boom(1.6), END, 0.7)
add(sweep(0.5, 300, 9000), END - 0.5, 0.3)

# voice: placed in time, a touch of room, music ducked under it
voice = np.zeros(N)
CUES = {"v1": 0.12, "v2": 1.74, "v3": 3.5, "v4": 5.08, "v5": 6.02, "v6": 10.8}
for name, at in CUES.items():
    v, vsr = sf.read(f"{sys.argv[2]}/{name}.wav")
    v = np.interp(np.arange(int(len(v) * SR / vsr)) * vsr / SR, np.arange(len(v)), v)
    v /= np.abs(v).max() + 1e-9
    room = v.copy()
    for d, g in ((0.023, 0.18), (0.041, 0.12), (0.067, 0.08)):
        room[int(d * SR):] += v[: len(v) - int(d * SR)] * g
    i = int(at * SR)
    voice[i:i + len(room)] += room[: N - i]
level = np.convolve(np.abs(voice) > 0.02, np.ones(int(0.25 * SR)) / int(0.25 * SR), "same")
duck = 1 - 0.55 * np.clip(level * 3, 0, 1)

mix = song * duck[:, None] + (sfx + voice * 0.95)[:, None]
fade = np.clip((DUR - t) / 0.6, 0, 1) * np.clip(t / 0.05, 0, 1)
mix *= fade[:, None]
mix = np.tanh(mix / np.abs(mix).max() * 1.6) * 0.92
with wave.open("trailer2.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype("<i2").tobytes())
print("trailer2.wav written")
