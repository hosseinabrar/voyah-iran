"""Append-only slot expansion preserves existing learned output indices."""
import json
import torch
from torch import nn

def expand_temperature(model):
    targets={
        ('SET_CABIN_TEMPERATURE|degreesC,zone','degreesC'):[i/2 for i in range(32,65)],
        ('ADJUST_CABIN_TEMPERATURE|delta,zone','delta'):[i/2 for i in range(-32,33) if i],
    }
    for (frame,key),numbers in targets.items():
        values=model.schema['slots'][key]
        allowed=[]
        for number in numbers:
            existing=next((v for v in values if json.loads(v)==number),None)
            if existing is None:
                existing=json.dumps(number);values.append(existing)
            allowed.append(existing)
        head=model.slot_heads['slot_'+key]
        if head.out_features<len(values):
            expanded=nn.Linear(head.in_features,len(values))
            with torch.no_grad():
                expanded.weight[:head.out_features].copy_(head.weight)
                expanded.bias[:head.out_features].copy_(head.bias)
                # New classes must not displace learned predictions before training.
                expanded.weight[head.out_features:].zero_()
                expanded.bias[head.out_features:].fill_(-8)
            model.slot_heads['slot_'+key]=expanded
        model.schema['allowed'].setdefault(frame,{})[key]=allowed
