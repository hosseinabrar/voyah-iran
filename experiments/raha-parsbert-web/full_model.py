"""ParsBERT with a semantic-frame head and typed slot heads, experiment only."""
import json
import torch
from torch import nn
from transformers import AutoModel, AutoConfig
from safetensors.torch import load_file, save_file

def encode_value(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))

def frame(row):
    return row['action']+'|'+','.join(sorted(row['parameters']))

def allowed_values(rows):
    allowed={}
    for row in rows:
        target=allowed.setdefault(frame(row),{})
        for k,v in row['parameters'].items():target.setdefault(k,set()).add(encode_value(v))
    return {f:{k:sorted(v) for k,v in fields.items()} for f,fields in allowed.items()}

class FullModel(nn.Module):
    def __init__(self, config, schema, pretrained=None):
        super().__init__()
        self.schema=schema
        self.bert=pretrained if pretrained is not None else AutoModel.from_config(config)
        self.dropout=nn.Dropout(.1)
        self.frame_head=nn.Linear(config.hidden_size,len(schema['frames']))
        self.slot_heads=nn.ModuleDict({'slot_'+k:nn.Linear(config.hidden_size,len(v)) for k,v in schema['slots'].items()})

    def forward(self, **inputs):
        out=self.bert(**inputs).last_hidden_state
        return self.heads(out,inputs['attention_mask'])

    def heads(self,out,attention_mask):
        mask=attention_mask.unsqueeze(-1)
        pooled=(out*mask).sum(1)/mask.sum(1).clamp(min=1)
        hidden=self.dropout(pooled)
        return self.frame_head(hidden),{k[5:]:h(hidden) for k,h in self.slot_heads.items()}

    def frozen_prefix(self,inputs):
        mask=self.bert.get_extended_attention_mask(inputs['attention_mask'],inputs['input_ids'].shape)
        hidden=self.bert.embeddings(input_ids=inputs['input_ids'],token_type_ids=inputs.get('token_type_ids'))
        for layer in self.bert.encoder.layer[:8]:hidden=layer(hidden,attention_mask=mask)[0]
        return hidden

    def from_prefix(self,hidden,attention_mask):
        mask=self.bert.get_extended_attention_mask(attention_mask,attention_mask.shape)
        for layer in self.bert.encoder.layer[8:]:hidden=layer(hidden,attention_mask=mask)[0]
        return self.heads(hidden,attention_mask)

    def save(self,path):
        path.mkdir(parents=True,exist_ok=True)
        self.bert.config.save_pretrained(path)
        (path/'schema.json').write_text(json.dumps(self.schema,ensure_ascii=False,indent=2))
        save_file({k:v.detach().cpu().contiguous() for k,v in self.state_dict().items()},str(path/'model.safetensors'))

    @classmethod
    def load(cls,path):
        model=cls(AutoConfig.from_pretrained(path,local_files_only=True),json.loads((path/'schema.json').read_text()))
        model.load_state_dict(load_file(str(path/'model.safetensors')))
        return model.eval()

    def decode(self,frame_logits,slot_logits):
        scores=frame_logits.softmax(-1)
        slot_probs={k:v.softmax(-1) for k,v in slot_logits.items()}
        results=[]
        for i in range(len(scores)):
            ranked=scores[i].topk(min(3,len(self.schema['frames'])))
            candidates=[]
            for score,idx in zip(ranked.values,ranked.indices):
                frame_name=self.schema['frames'][int(idx)]
                action,keys=frame_name.split('|')
                params={}; confidences={}
                for key in filter(None,keys.split(',')):
                    probs=slot_probs[key][i]
                    permitted=self.schema.get('allowed',{}).get(frame_name,{}).get(key)
                    if permitted:
                        indices=[self.schema['slots'][key].index(v) for v in permitted]
                        value_idx=indices[int(probs[indices].argmax())]
                    else:value_idx=int(probs.argmax())
                    params[key]=json.loads(self.schema['slots'][key][value_idx])
                    confidences[key]=round(float(probs[value_idx]),4)
                candidates.append(dict(action=action,parameters=params,score=round(float(score),4),slot_scores=confidences))
            results.append(candidates)
        return results
