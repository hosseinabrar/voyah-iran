"""One-time lossless migration; refuses to overwrite an existing base."""
from pathlib import Path
from model_delta import encode,decode

def main():
    target=Path(__file__).resolve().parents[2]/'voyah-iran/raha-runtime/src/main/assets/raha-intent'
    old=target/'model.onnx';base=target/'model.base.onnx'
    if base.exists():raise RuntimeError('Base already exists; do not rotate it on training')
    model=old.read_bytes();delta=encode(model,model)
    assert decode(model,delta)==model
    (target/'model.delta').write_bytes(delta)
    old.rename(base)

if __name__=='__main__':main()
