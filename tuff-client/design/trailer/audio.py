"""Glade trailer sound: a light 120 bpm beat plus effects on every cut.
Writes trailer.wav (48 kHz stereo, 13 s). Cue times match trailer.html."""
import wave
import numpy as np

SR, DUR = 48000, 13.0
N = int(SR * DUR)
rng = np.random.default_rng(3)
mix = np.zeros((N, 2))


def add(sig, at, gain=1.0, pan=0.0):
    i = int(at * SR)
    sig = sig[: max(0, N - i)] * gain
    if sig.ndim == 1:
        sig = np.stack([sig * (1 - max(0, pan)), sig * (1 + min(0, pan))], 1)
    mix[i:i + len(sig)] += sig


def env(n, a=0.005, d=0.3):
    t = np.arange(n) / SR
    return np.minimum(1, t / a) * np.exp(-t / d)


def tone(f, dur, d=0.4, a=0.005, kind="sine"):
    t = np.arange(int(dur * SR)) / SR
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(f, t.shape)) / SR
    w = np.sin(ph) if kind == "sine" else 2 * (ph / (2 * np.pi) % 1) - 1
    return w * env(len(t), a, d)


def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def sweep_noise(dur, f0, f1, rise=True):
    n = int(dur * SR)
    noise = rng.standard_normal(n)
    cut = np.geomspace(f0, f1, n)
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.empty(n)
    acc = 0.0
    for i in range(n):
        acc = (1 - a[i]) * noise[i] + a[i] * acc
        y[i] = acc
    shape = np.linspace(0, 1, n) ** 2 if rise else np.sin(np.linspace(0, np.pi, n))
    return y / (np.abs(y).max() + 1e-9) * shape


def kick():
    t = np.arange(int(0.45 * SR)) / SR
    f = 45 + 110 * np.exp(-t * 28)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(t), 0.002, 0.16)


def hat():
    n = int(0.06 * SR)
    x = rng.standard_normal(n)
    x = x - lowpass(x, 7000)
    return x * env(n, 0.001, 0.02)


def clap():
    n = int(0.25 * SR)
    x = rng.standard_normal(n)
    x = lowpass(x - lowpass(x, 900), 6000)
    e = env(n, 0.002, 0.08) + 0.6 * np.roll(env(n, 0.002, 0.05), int(0.012 * SR))
    return x * e


def boom():
    t = np.arange(int(1.6 * SR)) / SR
    f = 32 + 70 * np.exp(-t * 6)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(t), 0.003, 0.7)
    return sub + 0.5 * lowpass(rng.standard_normal(len(t)), 400) * env(len(t), 0.002, 0.25)


def chime(notes, step=0.07):
    out = np.zeros(int((len(notes) * step + 1.6) * SR))
    for i, f in enumerate(notes):
        s = tone(f, 1.5, 0.6) + 0.3 * tone(f * 2, 1.5, 0.3)
        i0 = int(i * step * SR)
        out[i0:i0 + len(s)] += s
    return out


def pad(freqs, dur):
    t = np.arange(int(dur * SR)) / SR
    s = sum(np.sin(2 * np.pi * f * t + i) + 0.4 * np.sin(2 * np.pi * f * 1.003 * t) for i, f in enumerate(freqs))
    fade = np.minimum(1, np.minimum(t / 0.4, (dur - t) / 0.4))
    return s * fade / len(freqs)


BEAT = 0.5
# chords: A minor, F, C, G (two beats each... looped), quieter when "coming soon" builds
CHORDS = [[220, 261.6, 329.6], [174.6, 220, 261.6], [130.8, 196, 261.6], [196, 246.9, 293.7]]
for k, at in enumerate(np.arange(0.0, 12.0, 2.0)):
    add(pad(CHORDS[k % 4], 2.05), at, 0.10)

for b in range(26):
    at = b * BEAT
    building = 6.6 <= at < 7.3
    if at >= 2.3 and not building and at < 12.6:
        add(kick(), at, 0.85)
        add(hat(), at + BEAT / 2, 0.22, pan=0.3)
        if b % 2 == 1:
            add(clap(), at, 0.35, pan=-0.15)
    elif at < 2.3 and b % 2 == 0 and at > 0.5:
        add(hat(), at + BEAT / 2, 0.12)

# effects, in time with trailer.html
add(sweep_noise(0.7, 300, 6000, rise=False), 0.05, 0.35)            # opening whoosh
add(chime([523.3, 659.3, 784, 1046.5]), 0.8, 0.22)                   # wordmark sparkle
add(boom(), 2.3, 0.7)                                                # cut to features
add(sweep_noise(0.4, 600, 5000, rise=False), 2.25, 0.25)
for i in range(6):                                                   # each card lands
    add(tone(1320 + i * 90, 0.15, 0.05), 2.62 + i * 0.6, 0.18, pan=(i % 3 - 1) * 0.4)
    add(hat(), 2.62 + i * 0.6, 0.08)
add(sweep_noise(0.75, 200, 9000), 6.55, 0.5)                         # riser into coming soon
add(boom(), 7.3, 1.0)
add(sweep_noise(0.5, 3000, 300, rise=False), 7.3, 0.3)
for i in range(3):                                                   # each "coming soon" item
    add(boom()[: int(0.5 * SR)], 8.05 + i * 0.6, 0.55)
    add(clap(), 8.05 + i * 0.6, 0.4)
add(sweep_noise(0.6, 300, 7000, rise=False), 10.35, 0.35)            # whoosh to the end card
add(chime([392, 523.3, 659.3, 784, 1046.5], 0.06), 10.65, 0.28)
add(boom(), 10.6, 0.6)

# master: gentle limiter and a fade at the very end
peak = np.abs(mix).max()
mix = np.tanh(mix / peak * 1.4) * 0.9
fade = np.ones(N)
fade[-int(0.5 * SR):] = np.linspace(1, 0, int(0.5 * SR))
mix *= fade[:, None]
pcm = (mix * 32767).astype("<i2")
with wave.open("trailer.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("trailer.wav written")
