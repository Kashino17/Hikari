"""Stimmen für die Fitness-/Achtsamkeits-Ansagen (Qwen3-TTS, Stimmklon aus einer Referenz).

- rem:  Rems Stimme (TMS-Terminal-Referenz) — Training.
- samt: entworfene Meditationsstimme (Qwen3-TTS VoiceDesign, 20.11.2026, von Kadir gewählt) —
        Meditation, Yoga, Atem, Affirmationen. Referenz liegt hier in voices/.
Clip-Kennung: Rem wie bisher sha1(Text); andere Stimmen sha1("<stimme>|Text") — so überschreiben
sich gleiche Sätze in zwei Stimmen nie.
"""
import hashlib, os

HERE = os.path.dirname(os.path.abspath(__file__))
VOICES = {
    "rem": (os.path.expanduser("~/.tms-terminal/voice_24k_15s.wav"), os.path.expanduser("~/.tms-terminal/voice_24k_15s.txt")),
    "samt": (os.path.join(HERE, "voices/samt.wav"), os.path.join(HERE, "voices/samt.txt")),
}


def ref(voice="rem"):
    wav, txt = VOICES[voice]
    return wav, open(txt, encoding="utf-8").read().strip()


def clip_id(spoken, voice="rem"):
    key = spoken if voice == "rem" else f"{voice}|{spoken}"
    return hashlib.sha1(key.encode("utf-8")).hexdigest()[:12]
