"""Export the active model AND the user's latest trained heads. Never trains.

Intermediate graphs stay in experiments/artifacts; only verified deployment
assets should be copied into Android. Run with this experiment's virtualenv.
"""
import hashlib
import json
import shutil
import os
from pathlib import Path
import torch
from transformers import AutoTokenizer
from full_model import FullModel
from feedback_training import FeedbackTrainer
from temperature_schema import expand_temperature

ROOT = Path(__file__).resolve().parent
OUT = Path(os.environ.get('RAHA_EXPORT_DIR', str(ROOT / 'artifacts/android')))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def load_active():
    source = (ROOT / json.loads((ROOT/'active-model.json').read_text())['path']).resolve()
    if not source.is_relative_to((ROOT/'artifacts').resolve()):
        raise ValueError('Checkpoint outside artifacts')
    model = FullModel.load(source)
    schema = model.schema
    schema['allowed']['SET_AUDIO_VOLUME|channel,level']['level'] = [str(i) for i in range(31)]
    schema['allowed']['SET_AMBIENT_LIGHT_MODE|enabled,mode']['mode'] = ['"normal"','"music"','"driveMode"']
    expand_temperature(model)
    schema['allowed']['SET_FAN_SPEED|level,zone']['level'] = [str(i) for i in range(8)]+['"min"','"max"']
    schema['allowed']['ADJUST_FAN_SPEED|delta,zone']['delta'] = [next(v for v in schema['slots']['delta'] if json.loads(v)==i) for i in range(-7,8) if i]
    for feature in ['HEATING','VENTILATION']:
        schema['allowed'][f'ADJUST_SEAT_{feature}|delta,seat']['delta'] = [str(i) for i in [-3,-2,-1,1,2,3]]
    tokenizer = AutoTokenizer.from_pretrained(source, local_files_only=True)
    checkpoint = OUT/'feedback-snapshot.safetensors'
    before = sha(checkpoint)
    trainer = FeedbackTrainer(ROOT,model,tokenizer,checkpoint_name='android/feedback-snapshot')
    if sha(checkpoint)!=before:
        raise RuntimeError('Feedback changed during export; retry after training finishes')
    return model.eval(),tokenizer,trainer,source,before

class Export(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model
    def forward(self,input_ids,attention_mask,token_type_ids):
        frames,slots = self.model(input_ids=input_ids,attention_mask=attention_mask,token_type_ids=token_type_ids)
        return (frames, *[slots[k] for k in self.model.schema['slots']])

def main():
    torch.set_num_threads(4)
    OUT.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(ROOT/'artifacts/feedback-head-practice-v3.safetensors',OUT/'feedback-snapshot.safetensors')
    model,tokenizer,trainer,source,feedback_sha = load_active()
    # Eager attention has a stable portable ONNX graph (no device-specific SDPA).
    model.bert.set_attn_implementation('eager')
    batch = tokenizer('دمای کابین رو سه درجه زیاد کن',return_tensors='pt')
    names = ['frames']+['slot_'+k for k in model.schema['slots']]
    with torch.inference_mode():
        torch.onnx.export(Export(model),tuple(batch[k] for k in ['input_ids','attention_mask','token_type_ids']),str(OUT/'model.fp32.onnx'),
            input_names=['input_ids','attention_mask','token_type_ids'],output_names=names,
            dynamic_axes={k:{1:'sequence'} for k in ['input_ids','attention_mask','token_type_ids']},
            opset_version=17,dynamo=False)
    (OUT/'schema.json').write_text(json.dumps(model.schema,ensure_ascii=False))
    tokenizer.save_pretrained(OUT/'tokenizer')
    (OUT/'contact-rules.json').write_text(json.dumps(trainer.contact_rules,ensure_ascii=False))
    (OUT/'provenance.json').write_text(json.dumps(dict(source=str(source.relative_to(ROOT)),
        base_sha256=sha(source/'model.safetensors'),feedback_sha256=feedback_sha,
        feedback_count=len(trainer.rows),outputs=names,max_tokens=48),indent=2))
    print(json.dumps(dict(output=str(OUT),feedback_count=len(trainer.rows)),ensure_ascii=False),flush=True)

if __name__=='__main__':main()
