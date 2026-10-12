#!/usr/bin/env bash
# Richtet die Python-Umgebung für die Phonem-Aussprachebewertung ein (einmalig, ~2 GB).
# Der Hikari-Server startet danach phoneme_server.py bei Bedarf selbst.
set -euo pipefail
ENV_DIR="${HIKARI_PHONEME_ENV:-$HOME/.hikari/phoneme-env}"
mkdir -p "$(dirname "$ENV_DIR")"
uv venv -p 3.12 "$ENV_DIR"
# shellcheck disable=SC1091
source "$ENV_DIR/bin/activate"
uv pip install torch numpy soundfile transformers phonemizer
python - <<'PY'
from transformers import AutoProcessor, AutoModelForCTC
n = "facebook/wav2vec2-xlsr-53-espeak-cv-ft"
AutoProcessor.from_pretrained(n); AutoModelForCTC.from_pretrained(n)
print("Modell geladen:", n)
PY
echo "fertig: $ENV_DIR"
