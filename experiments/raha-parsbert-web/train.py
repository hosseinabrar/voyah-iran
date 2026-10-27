"""Fine-tune last two ParsBERT layers + classifier; fixed seed and offline report."""
import os
os.environ.setdefault('HF_HUB_DISABLE_XET', '1')
import json
import random
import time
from pathlib import Path
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from data import MODEL, LABELS, EVAL, training_data, normalize

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'artifacts' / 'intent'
REVISION = '3880fac085e1a338e9564907cba0adeb9e14bc72'

def main():
    torch.manual_seed(42)
    random.seed(42)
    torch.set_num_threads(4)
    device = 'mps' if torch.backends.mps.is_available() else 'cpu'
    print('Loading official ParsBERT; device:', device, flush=True)
    tokenizer = AutoTokenizer.from_pretrained(MODEL, revision=REVISION)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL, revision=REVISION, num_labels=len(LABELS),
        id2label=dict(enumerate(LABELS)), label2id={v:i for i,v in enumerate(LABELS)},
        use_safetensors=False).to(device)
    for name, p in model.named_parameters():
        p.requires_grad = any(x in name for x in ['encoder.layer.10.', 'encoder.layer.11.', 'pooler.', 'classifier.'])
    rows = training_data()
    train_text = {r['text'] for r in rows}
    assert not train_text.intersection(normalize(t) for t, _ in EVAL)
    enc = tokenizer([r['text'] for r in rows], padding='max_length', truncation=True, max_length=48, return_tensors='pt')
    labels = torch.tensor([LABELS.index(r['label']) for r in rows])
    optimizer = torch.optim.AdamW([
        {'params':[p for n,p in model.named_parameters() if p.requires_grad and 'classifier' not in n], 'lr':3e-5},
        {'params':[p for n,p in model.named_parameters() if 'classifier' in n], 'lr':2e-4}
    ], weight_decay=.01)
    started = time.monotonic()
    for epoch in range(12):
        model.train()
        order = torch.randperm(len(rows))
        losses = []
        for ids in order.split(24):
            optimizer.zero_grad(set_to_none=True)
            batch = {k:v[ids].to(device) for k,v in enc.items()}
            loss = model(**batch, labels=labels[ids].to(device)).loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            losses.append(loss.item())
        print(f'Epoch {epoch+1}/12 loss={sum(losses)/len(losses):.4f} elapsed={time.monotonic()-started:.0f}s', flush=True)
    model.eval()
    results=[]
    with torch.inference_mode():
        for text, expected in EVAL:
            batch = tokenizer(normalize(text), return_tensors='pt', truncation=True, max_length=48).to(device)
            probs = model(**batch).logits.softmax(-1)[0].cpu()
            predicted=LABELS[int(probs.argmax())]
            results.append(dict(text=text, expected=expected, predicted=predicted, score=round(float(probs.max()),4), correct=predicted==expected))
    OUT.mkdir(parents=True, exist_ok=True)
    model.cpu().save_pretrained(OUT, safe_serialization=True)
    tokenizer.save_pretrained(OUT)
    report=dict(base_model=MODEL, revision=REVISION, seed=42, epochs=12, train_count=len(rows), trainable='last 2 encoder layers + pooler + classifier', device=device, seconds=round(time.monotonic()-started), accuracy=sum(r['correct'] for r in results)/len(results), evaluation='Small synthetic development probe, NOT real-world accuracy or calibrated confidence', results=results)
    (OUT/'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)

if __name__ == '__main__':
    main()
