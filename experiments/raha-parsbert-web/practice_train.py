"""Supervised rounds on curated semantics; dev mining, untouched final holdout."""
import json
import os
import random
import time
from collections import Counter
from pathlib import Path
import torch
from transformers import AutoTokenizer
from full_model import FullModel,frame,encode_value,allowed_values

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'artifacts/practice'

def evaluate(model,tok,rows,device):
    model.eval();results=[]
    with torch.no_grad():
        for offset in range(0,len(rows),32):
            batch=rows[offset:offset+32]
            inputs=tok([r['text'] for r in batch],padding=True,truncation=True,max_length=48,return_tensors='pt').to(device)
            logits,slots=model(**inputs)
            for row,ranked in zip(batch,model.decode(logits,slots)):
                p=ranked[0]
                results.append({**row,'predicted':p,'action_correct':p['action']==row['action'],'exact_correct':p['action']==row['action'] and p['parameters']==row['parameters']})
    return dict(count=len(rows),action_accuracy=sum(r['action_correct'] for r in results)/len(rows),exact_accuracy=sum(r['exact_correct'] for r in results)/len(rows),results=results)

def summary(report):return {k:v for k,v in report.items() if k!='results'}

def main():
    torch.manual_seed(819);random.seed(819);torch.set_num_threads(4)
    device='mps' if torch.backends.mps.is_available() else 'cpu'
    data=json.loads((OUT/'dataset.json').read_text());rows=data['train']
    source=ROOT/'artifacts/full-intent'
    model=FullModel.load(source).to(device)
    tok=AutoTokenizer.from_pretrained(source,local_files_only=True)
    baseline=evaluate(model,tok,data['dev'],device)
    (OUT/'baseline-dev.json').write_text(json.dumps(baseline,ensure_ascii=False,indent=2))
    print('BASELINE DEV',json.dumps(summary(baseline)),flush=True)
    # Do not inspect final examples during mining/model selection.
    baseline_final=evaluate(model,tok,data['final'],device)
    (OUT/'baseline-final.json').write_text(json.dumps(baseline_final,ensure_ascii=False,indent=2))
    model.schema['allowed']=allowed_values(rows)
    for name,p in model.bert.named_parameters():p.requires_grad=any(f'encoder.layer.{n}.' in name for n in range(8,12))
    optimizer=torch.optim.AdamW([
      {'params':[p for p in model.bert.parameters() if p.requires_grad],'lr':1.5e-5},
      {'params':list(model.frame_head.parameters())+list(model.slot_heads.parameters()),'lr':1.5e-4}
    ],weight_decay=.01)
    # Cache features for train + development only; final is never fed to optimizer.
    allrows=rows+data['dev']
    encoded=tok([r['text'] for r in allrows],padding='max_length',truncation=True,max_length=48,return_tensors='pt')
    model.eval();cache=[];start=time.monotonic()
    print(f'Caching {len(allrows)} examples',flush=True)
    with torch.no_grad():
        for offset in range(0,len(allrows),64):
            cache.append(model.frozen_prefix({k:v[offset:offset+64].to(device) for k,v in encoded.items()}).cpu().half())
            if offset%4096==0:print(f'CACHE {offset}/{len(allrows)}',flush=True)
    cache=torch.cat(cache)
    frames=model.schema['frames']
    targets=torch.tensor([frames.index(frame(r)) for r in allrows])
    slot_targets={k:torch.tensor([v.index(encode_value(r['parameters'][k])) if k in r['parameters'] else -100 for r in allrows]) for k,v in model.schema['slots'].items()}
    # A mixture preserves numeric diversity while balancing rare command families.
    counts=Counter(r['action'] for r in rows)
    weights=torch.tensor([.5/len(rows)+.5/(len(counts)*counts[r['action']]) for r in rows]+[0.]*len(data['dev']))
    best=-1;history=[]
    epochs=int(os.environ.get('RAHA_PRACTICE_EPOCHS','6'))
    for epoch in range(epochs):
        model.train();total=0;steps=0
        order=torch.multinomial(weights,len(rows),replacement=True)
        for ids in order.split(32):
            optimizer.zero_grad(set_to_none=True)
            logits,slots=model.from_prefix(cache[ids].float().to(device),encoded['attention_mask'][ids].to(device))
            loss=torch.nn.functional.cross_entropy(logits,targets[ids].to(device))
            terms=[]
            for k,p in slots.items():
                truth=slot_targets[k][ids].to(device)
                if (truth!=-100).any():terms.append(torch.nn.functional.cross_entropy(p,truth,ignore_index=-100))
            if terms:loss=loss+torch.stack(terms).mean()
            loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1);optimizer.step()
            total+=loss.item();steps+=1
        report=evaluate(model,tok,data['dev'],device)
        item=dict(epoch=epoch+1,loss=total/steps,seconds=round(time.monotonic()-start),**summary(report))
        history.append(item);print('EPOCH',json.dumps(item),flush=True)
        (OUT/f'dev-epoch-{epoch+1}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
        if report['exact_accuracy']>best:
            best=report['exact_accuracy'];model.save(OUT/'best');tok.save_pretrained(OUT/'best')
        # First three rounds test genuinely unseen dev; subsequent rounds practice
        # its errors. Final holdout remains disjoint, including normalized text.
        if epoch==2:
            failures=[i for i,r in enumerate(report['results']) if not r['exact_correct']]
            for i in failures:weights[len(rows)+i]=.2/max(1,len(failures))
            print('MINING',len(failures),'development failures for corrective practice',flush=True)
    model=FullModel.load(OUT/'best').to(device)
    final=evaluate(model,tok,data['final'],device)
    report=dict(version=3,train_count=len(rows),action_count=len(data['titles'])-1,epochs=epochs,device=device,
                baseline=summary(baseline_final),**summary(final),history=history,
                evaluation='Synthetic structural holdout never trained/mined; not real-world or ASR accuracy.',results=final['results'])
    (OUT/'best/report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('FINAL',json.dumps({k:v for k,v in report.items() if k not in ['results','history']}),flush=True)

if __name__=='__main__':main()
