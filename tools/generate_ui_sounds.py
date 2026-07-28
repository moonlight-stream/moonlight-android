#!/usr/bin/env python3
"""Generate Iris's original UI sonification assets.

The tones are intentionally short, quiet, and nonmusical. Keeping generation
in-tree makes the asset provenance and synthesis parameters reproducible.
"""

import math
import pathlib
import struct
import wave

SAMPLE_RATE = 44100
OUTPUT_DIR = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def envelope(position, length):
    attack = max(1, int(length * 0.08))
    release = max(1, int(length * 0.45))
    if position < attack:
        return position / attack
    if position > length - release:
        return max(0.0, (length - position) / release)
    return 1.0


def write_tone(name, duration_ms, start_hz, end_hz, volume, overtone=0.15):
    frame_count = int(SAMPLE_RATE * duration_ms / 1000)
    frames = bytearray()
    phase = 0.0
    overtone_phase = 0.0
    for index in range(frame_count):
        progress = index / max(1, frame_count - 1)
        frequency = start_hz + (end_hz - start_hz) * progress
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        overtone_phase += 2.0 * math.pi * frequency * 2.01 / SAMPLE_RATE
        sample = math.sin(phase) + overtone * math.sin(overtone_phase)
        sample *= envelope(index, frame_count) * volume
        frames.extend(struct.pack("<h", int(max(-1.0, min(1.0, sample)) * 32767)))

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT_DIR / name), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(frames)


def main():
    write_tone("ui_focus.wav", 55, 1120, 1340, 0.16, 0.08)
    write_tone("ui_confirm.wav", 100, 520, 880, 0.19, 0.16)
    write_tone("ui_back.wav", 85, 620, 390, 0.16, 0.10)
    write_tone("ui_error.wav", 160, 190, 145, 0.20, 0.22)


if __name__ == "__main__":
    main()
