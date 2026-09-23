import torch, soundfile as sf, numpy as np, librosa, sys
from transformers import AutoProcessor, AutoModelForCTC
name = "facebook/wav2vec2-xlsr-53-espeak-cv-ft"
proc = AutoProcessor.from_pretrained(name)
model = AutoModelForCTC.from_pretrained(name).eval()
def phon(f):
    a, sr = sf.read(f)
    if a.ndim > 1: a = a.mean(1)
    if sr != 16000:
        a = librosa.resample(a.astype(np.float32), orig_sr=sr, target_sr=16000)
    x = proc(a, sampling_rate=16000, return_tensors="pt").input_values
    with torch.no_grad():
        ids = model(x).logits.argmax(-1)
    return proc.batch_decode(ids)[0]
if __name__ == "__main__":
    for f in sys.argv[1:]:
        print(f, "|", phon(f))
