"""Full command-family pilot. Do not overwrite the first experiment checkpoint."""
import os
os.environ.setdefault('HF_HUB_DISABLE_XET','1')
import json
import random
import time
from collections import Counter,defaultdict
from pathlib import Path
import torch
from transformers import AutoModel,AutoTokenizer
from data import MODEL,normalize
from full_model import FullModel,frame,encode_value,allowed_values
from train import REVISION

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'artifacts/full-intent'

def main():
    random.seed(73);torch.manual_seed(73);torch.set_num_threads(4)
    device='mps' if torch.backends.mps.is_available() else 'cpu'
    dataset=json.loads((ROOT/'dataset.json').read_text())
    rows=dataset['train']; evaluation=dataset['evaluation']
    frames=sorted({frame(r) for r in rows})
    values=defaultdict(set)
    for r in rows:
        for k,v in r['parameters'].items():values[k].add(encode_value(v))
    schema={'frames':frames,'slots':{k:sorted(v) for k,v in sorted(values.items())},'titles':dataset['titles'],'allowed':allowed_values(rows)}
    tokenizer=AutoTokenizer.from_pretrained(MODEL,revision=REVISION)
    base=AutoModel.from_pretrained(MODEL,revision=REVISION,use_safetensors=False,attn_implementation='eager')
    model=FullModel(base.config,schema,base).to(device)
    # Four final transformer blocks adapt to the larger domain; early layers frozen.
    for name,p in model.bert.named_parameters():p.requires_grad=any(f'encoder.layer.{n}.' in name for n in range(8,12))
    optimizer=torch.optim.AdamW([
        {'params':[p for p in model.bert.parameters() if p.requires_grad],'lr':3e-5},
        {'params':list(model.frame_head.parameters())+list(model.slot_heads.parameters()),'lr':3e-4}
    ],weight_decay=.01)
    enc=tokenizer([r['text'] for r in rows],padding='max_length',truncation=True,max_length=48,return_tensors='pt')
    targets=torch.tensor([frames.index(frame(r)) for r in rows])
    slots={k:torch.tensor([v.index(encode_value(r['parameters'][k])) if k in r['parameters'] else -100 for r in rows]) for k,v in schema['slots'].items()}
    counts=Counter(int(t) for t in targets)
    weights=torch.tensor([1/counts[int(t)]**.7 for t in targets])
    start=time.monotonic()
    print(f'Caching frozen encoder prefix for {len(rows)} examples / {len(frames)} frames',flush=True)
    model.eval();cached=[]
    with torch.inference_mode():
        for offset in range(0,len(rows),64):
            cached.append(model.frozen_prefix({k:v[offset:offset+64].to(device) for k,v in enc.items()}).cpu().half())
    cached=torch.cat(cached)
    epochs=int(os.environ.get('RAHA_EPOCHS','18'))
    for epoch in range(epochs):
        model.train();total=0;steps=0
        order=torch.multinomial(weights,len(rows),replacement=True)
        for indices in order.split(32):
            optimizer.zero_grad(set_to_none=True)
            pred,slot_pred=model.from_prefix(cached[indices].float().to(device),enc['attention_mask'][indices].to(device))
            loss=torch.nn.functional.cross_entropy(pred,targets[indices].to(device))
            slot_losses=[]
            for k,logits in slot_pred.items():
                truth=slots[k][indices].to(device)
                if (truth!=-100).any():slot_losses.append(torch.nn.functional.cross_entropy(logits,truth,ignore_index=-100))
            if slot_losses:loss=loss+torch.stack(slot_losses).mean()
            loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1);optimizer.step()
            total+=loss.item();steps+=1
        print(f'Epoch {epoch+1}/{epochs} loss={total/steps:.4f} elapsed={time.monotonic()-start:.0f}s',flush=True)
    model.eval();results=[]
    with torch.inference_mode():
        for start_idx in range(0,len(evaluation),32):
            batch=evaluation[start_idx:start_idx+32]
            inp=tokenizer([r['text'] for r in batch],padding=True,truncation=True,max_length=48,return_tensors='pt').to(device)
            logits,slot_logits=model(**inp)
            predictions=model.decode(logits,slot_logits)
            for r,ranked in zip(batch,predictions):
                top=ranked[0]
                results.append({**r,'predicted':top,'action_correct':r['action']==top['action'],'exact_correct':r['action']==top['action'] and r['parameters']==top['parameters']})
    model.save(OUT);tokenizer.save_pretrained(OUT)
    report=dict(base_model=MODEL,revision=REVISION,seed=73,epochs=epochs,train_count=len(rows),action_count=len(dataset['titles'])-1,frame_count=len(frames),device=device,seconds=round(time.monotonic()-start),action_accuracy=sum(r['action_correct'] for r in results)/len(results),exact_accuracy=sum(r['exact_correct'] for r in results)/len(results),evaluation='Synthetic held-out phrasing; not real-world accuracy. All numbers include rejected/uncertain predictions.',results=results)
    (OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__':main()
