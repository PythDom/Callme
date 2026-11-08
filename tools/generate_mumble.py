#!/usr/bin/env python3
"""Generate the "someone talking on the phone" mumble clip used after a call is answered.

The clip is pure formant synthesis (no recorded voice, no copyright concerns):
a glottal pulse train with natural-sounding intonation is shaped by three
time-varying vowel formants, broken into syllables and phrases, with short
consonant-like noise bursts, then band-limited to telephone bandwidth
(300-3400 Hz) so it sounds like an unintelligible voice in the earpiece.

Usage:
    python3 tools/generate_mumble.py out.wav [--seed 7] [--seconds 32]
Then encode, e.g.:
    ffmpeg -y -i out.wav -c:a libvorbis -q:a 3 app/src/main/res/raw/mumble.ogg
"""
import argparse
import wave

import numpy as np
from scipy import signal

SR = 16000

# Rough (F1, F2, F3) formant targets for a male voice, in Hz.
VOWELS = [
    (730, 1090, 2440),  # a
    (530, 1840, 2480),  # e
    (390, 1990, 2550),  # i-ish
    (570, 840, 2410),   # o
    (440, 1020, 2240),  # u-ish
    (660, 1720, 2410),  # ae
    (490, 1350, 1690),  # schwa / er
]
BANDWIDTHS = (90.0, 110.0, 170.0)


def build_plan(rng, seconds):
    """Return per-sample arrays: voiced amplitude, noise amplitude, f0, F1..F3."""
    n = int(seconds * SR)
    amp = np.zeros(n)
    noise_amp = np.zeros(n)
    f0 = np.full(n, 110.0)
    formants = np.zeros((3, n))
    formants[:] = np.array(VOWELS[6])[:, None]

    t = int(0.6 * SR)  # start with a short silence so the loop point is clean
    end = n - int(0.8 * SR)
    prev_vowel = np.array(VOWELS[6], dtype=float)
    while t < end:
        syllables = rng.integers(4, 13)
        base = rng.uniform(105, 135)
        for k in range(syllables):
            # Consonant: either a short fricative burst or a short stop gap.
            c_len = int(rng.uniform(0.025, 0.08) * SR)
            if t + c_len >= end:
                break
            if rng.random() < 0.55:
                noise_amp[t:t + c_len] = rng.uniform(0.08, 0.22) * np.hanning(c_len)
            t += c_len

            v_len = int(rng.uniform(0.09, 0.24) * SR)
            if t + v_len >= end:
                break
            stress = 1.0 if rng.random() < 0.3 else rng.uniform(0.55, 0.85)
            env = np.ones(v_len)
            ramp = min(int(0.025 * SR), v_len // 3)
            env[:ramp] = np.linspace(0, 1, ramp) ** 1.5
            env[-ramp:] = np.linspace(1, 0, ramp) ** 1.5
            amp[t:t + v_len] = stress * env

            # Intonation: phrase declination + accent bump + slight wobble.
            progress = k / max(1, syllables - 1)
            pitch = base * (1.08 - 0.22 * progress) * (1 + 0.12 * (stress - 0.7))
            if k == syllables - 1 and rng.random() < 0.25:
                contour = np.linspace(pitch, pitch * 1.25, v_len)  # question-like rise
            else:
                contour = np.linspace(pitch * 1.03, pitch * 0.97, v_len)
            f0[t:t + v_len] = contour

            target = np.array(VOWELS[rng.integers(len(VOWELS))], dtype=float)
            target *= rng.uniform(0.94, 1.06, 3)
            glide = np.linspace(0, 1, v_len) ** 0.5
            formants[:, t:t + v_len] = prev_vowel[:, None] + (target - prev_vowel)[:, None] * glide
            prev_vowel = target
            t += v_len
        # Pause between phrases; sometimes a long one (the "listener" is talking).
        t += int((rng.uniform(0.9, 1.8) if rng.random() < 0.2 else rng.uniform(0.2, 0.6)) * SR)

    # Smooth f0 and formants so transitions aren't stepwise.
    smooth = np.hanning(int(0.03 * SR))
    smooth /= smooth.sum()
    f0 = np.convolve(f0, smooth, mode="same")
    for i in range(3):
        formants[i] = np.convolve(formants[i], smooth, mode="same")
    return amp, noise_amp, f0, formants


def resonator(x, freqs, bw):
    """Time-varying 2-pole resonator (Klatt style)."""
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    r = np.exp(-np.pi * bw / SR)
    c = -r * r
    for i in range(len(x)):
        b = 2 * r * np.cos(2 * np.pi * freqs[i] / SR)
        a = 1 - b - c
        yi = a * x[i] + b * y1 + c * y2
        y[i] = yi
        y2, y1 = y1, yi
    return y


def synthesize(seed, seconds):
    rng = np.random.default_rng(seed)
    amp, noise_amp, f0, formants = build_plan(rng, seconds)
    n = len(amp)

    jitter = 1 + 0.01 * rng.standard_normal(n)
    phase = np.cumsum(f0 * jitter / SR)
    pulses = np.diff(np.floor(phase), prepend=0.0)
    # Glottal shaping: smooth each pulse into a decaying wave.
    glottal = signal.lfilter([1.0], [1.0, -1.85, 0.86], pulses)
    glottal = np.diff(glottal, prepend=0.0)  # lip radiation
    source = glottal * amp + 0.02 * rng.standard_normal(n) * amp  # a bit of breathiness

    voiced = source
    out = np.zeros(n)
    for i, bw in enumerate(BANDWIDTHS):
        out += resonator(voiced, formants[i], bw) * (1.0, 0.6, 0.3)[i]

    fric = rng.standard_normal(n) * noise_amp
    b, a = signal.butter(2, [2200, 5500], btype="bandpass", fs=SR)
    fric = signal.lfilter(b, a, fric)
    out = out / (np.max(np.abs(out)) + 1e-9) + 0.6 * fric

    # Telephone band + soft compression + a faint line noise floor.
    b, a = signal.butter(4, [300, 3400], btype="bandpass", fs=SR)
    out = signal.lfilter(b, a, out)
    out = np.tanh(2.2 * out / (np.max(np.abs(out)) + 1e-9))
    out += 0.004 * rng.standard_normal(n)
    out *= 0.7 / np.max(np.abs(out))

    fade = int(0.2 * SR)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--seconds", type=float, default=32)
    args = p.parse_args()
    pcm = (synthesize(args.seed, args.seconds) * 32767).astype(np.int16)
    with wave.open(args.out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    main()
