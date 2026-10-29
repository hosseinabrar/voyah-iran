"""Append-only command expansion; keep every existing learned output index."""
import torch
from torch import nn

TITLES = {'SET_EASY_ENTRY': 'ورود آسان', 'SET_EASY_EXIT': 'خروج آسان',
          'SET_SCHEDULED_CHARGING': 'شارژ زمان‌بندی‌شده'}

def expand_easy_access(model):
    for action, title in TITLES.items():
        frame = action + '|enabled'
        if frame not in model.schema['frames']:
            model.schema['frames'].append(frame)
        model.schema['allowed'][frame] = {'enabled': ['false', 'true']}
        model.schema['titles'][action] = title
    head = model.frame_head
    count = len(model.schema['frames'])
    if head.out_features < count:
        expanded = nn.Linear(head.in_features, count)
        with torch.no_grad():
            expanded.weight[:head.out_features].copy_(head.weight)
            expanded.bias[:head.out_features].copy_(head.bias)
            expanded.weight[head.out_features:].zero_()
            expanded.bias[head.out_features:].fill_(-20)
        model.frame_head = expanded
