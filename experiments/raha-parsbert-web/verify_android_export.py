"""Check portable graph against the exact trained heads before deployment."""
import json,time,sys
import numpy as np
import onnxruntime as ort
import torch
from export_android import OUT,ROOT,load_active
from inference_parity import equivalent

def main():
    torch.set_num_threads(4)
    model,tokenizer,trainer,source,feedback_sha=load_active()
    provenance=json.loads((OUT/'provenance.json').read_text())
    assert provenance['feedback_sha256']==feedback_sha, 'Feedback changed: export again'
    options=ort.SessionOptions();options.intra_op_num_threads=2
    graph=sys.argv[1] if len(sys.argv)>1 else 'model.fp32.onnx'
    if graph=='model.compact.onnx':
        from compact_model import CompactEncoder
        from export_android import sha
        encoder=ROOT/'artifacts/compact-web/encoder.int8.onnx'
        assert sha(encoder)==provenance['encoder_sha256']
        model.bert=CompactEncoder(encoder)
    session=ort.InferenceSession(str(OUT/graph),options,providers=['CPUExecutionProvider'])
    rows=trainer.rows
    # Every saved user correction, plus held-out examples and edge tokenization.
    dataset=json.loads((ROOT/'artifacts/practice/dataset.json').read_text())
    rows=rows+dataset['final']
    texts=list(dict.fromkeys(r['text'] for r in rows))
    texts+=['','سلام','رها','دمای کولر رو شیش درجه کم کن','شیشه پشت سری همراه رو ببند','یه زنگ به علی بزن']
    failures=[];max_error=0.;latencies=[];fixtures=[]
    for i,text in enumerate(texts):
        from data import normalize
        text=normalize(text)
        batch=tokenizer(text,return_tensors='pt',max_length=48,truncation=True)
        with torch.inference_mode():frames,slots=model(**batch)
        start=time.perf_counter()
        outputs=session.run(None,{k:v.numpy() for k,v in batch.items()})
        latencies.append((time.perf_counter()-start)*1000)
        expected=model.decode(frames,slots)[0]
        actual=model.decode(torch.from_numpy(outputs[0]),{k:torch.from_numpy(outputs[j+1]) for j,k in enumerate(model.schema['slots'])})[0]
        max_error=max(max_error,float(np.max(np.abs(outputs[0]-frames.numpy()))))
        if not equivalent(expected,actual):
            failures.append(dict(text=text,expected=expected,actual=actual))
            print('Parity mismatch: '+json.dumps(failures[-1],ensure_ascii=False),flush=True)
        fixtures.append(dict(text=text,ids=batch['input_ids'][0].tolist(),expected=expected[0]))
        if i%100==0:print(f'Checked {i}/{len(texts)}',flush=True)
    from export_android import sha
    report=dict(count=len(texts),failures=failures,max_frame_logit_error=max_error,model_sha256=sha(OUT/graph),
        median_ms=float(np.median(latencies)),p95_ms=float(np.percentile(latencies,95)),
        feedback_sha256=feedback_sha,device='macOS host CPU, not vehicle')
    (OUT/('parity-report.json' if graph in ['model.fp32.onnx','model.compact.onnx'] else 'int8-parity-report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
    if graph in ['model.fp32.onnx','model.compact.onnx']:(OUT/'parity-fixtures.json').write_text(json.dumps(fixtures,ensure_ascii=False))
    print(json.dumps({k:v for k,v in report.items() if k!='failures'},ensure_ascii=False),flush=True)
    assert not failures, f'{len(failures)} graph parity failures'

if __name__=='__main__':main()
