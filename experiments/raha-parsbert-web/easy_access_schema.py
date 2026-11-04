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
    # OEM sunroof ventilation is a separate command, not a percentage.
    frame = 'VENT_SUNROOF|'
    if frame not in model.schema['frames']:
        model.schema['frames'].append(frame)
    model.schema['allowed'][frame] = {}
    model.schema['titles']['VENT_SUNROOF'] = 'حالت تهویه سانروف'
    frame = 'ADJUST_SUSPENSION_HEIGHT|delta'
    if frame not in model.schema['frames']: model.schema['frames'].append(frame)
    model.schema['allowed'][frame] = {'delta': ['-1', '1']}
    model.schema['titles']['ADJUST_SUSPENSION_HEIGHT'] = 'تغییر پله‌ای ارتفاع خودرو'
    frame = 'ADJUST_FORWARD_COLLISION_SENSITIVITY|delta'
    if frame not in model.schema['frames']: model.schema['frames'].append(frame)
    model.schema['allowed'][frame] = {'delta': ['-1', '1']}
    model.schema['titles']['ADJUST_FORWARD_COLLISION_SENSITIVITY'] = 'تغییر حساسیت هشدار برخورد جلو'
    frame = 'SET_WIRELESS_CHARGER|enabled'
    if frame not in model.schema['frames']: model.schema['frames'].append(frame)
    model.schema['allowed'][frame] = {'enabled': ['false','true']}
    model.schema['titles']['SET_WIRELESS_CHARGER'] = 'شارژر وایرلس گوشی'
    frame = 'SET_PASSENGER_DISPLAY_POWER|enabled'
    if frame not in model.schema['frames']: model.schema['frames'].append(frame)
    model.schema['allowed'][frame] = {'enabled':['false','true']}
    model.schema['titles']['SET_PASSENGER_DISPLAY_POWER'] = 'روشن و خاموش کردن نمایشگر شاگرد'
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

    # Preserve old window output indices and restrict non-ventilation frames
    # to their original values before extending the shared slot head.
    key = 'window'
    values = model.schema['slots'].get(key)
    if values is not None:
        for existing in model.schema['frames']:
            if key in existing.split('|')[1].split(','):
                model.schema['allowed'].setdefault(existing, {}).setdefault(key, list(values))
        for value in ['"sunroof"', '"allWithSunroof"']:
            if value not in values: values.append(value)
        old = model.slot_heads['slot_window']
        if old.out_features < len(values):
            expanded = nn.Linear(old.in_features, len(values))
            with torch.no_grad():
                expanded.weight[:old.out_features].copy_(old.weight)
                expanded.bias[:old.out_features].copy_(old.bias)
                expanded.weight[old.out_features:].zero_()
                expanded.bias[old.out_features:].fill_(-8)
            model.slot_heads['slot_window'] = expanded
        allowed = model.schema['allowed']['VENT_WINDOW|window']['window']
        for value in ['"sunroof"', '"allWithSunroof"']:
            if value not in allowed: allowed.append(value)
        model.schema['titles']['VENT_WINDOW'] = 'حالت تهویه'

    # OEM rain sensor sensitivity has four selectable levels.
    if 'SET_WIPER_SENSITIVITY|level' in model.schema['frames']:
        model.schema['allowed'].setdefault('SET_WIPER_SENSITIVITY|level', {})['level'] = ['"max"', '"min"', '1', '2', '3', '4']

    # Match the installed OEM reserve slider, retaining existing learned indices.
    import json
    for frame, key in [('SET_BATTERY_RESERVE_TARGET|percent','percent'), ('SET_ENERGY_STRATEGY|reservePercent,strategy','reservePercent')]:
        if frame not in model.schema['frames']: continue
        values = model.schema['slots'][key]
        allowed = []
        for number in range(25,81,5):
            existing = next((v for v in values if json.loads(v) == number), None)
            if existing is None: existing = str(number); values.append(existing)
            allowed.append(existing)
        head = model.slot_heads['slot_'+key]
        if head.out_features < len(values):
            expanded = nn.Linear(head.in_features,len(values))
            with torch.no_grad():
                expanded.weight[:head.out_features].copy_(head.weight)
                expanded.bias[:head.out_features].copy_(head.bias)
                expanded.weight[head.out_features:].zero_()
                expanded.bias[head.out_features:].fill_(-8)
            model.slot_heads['slot_'+key] = expanded
        model.schema['allowed'][frame][key] = allowed
    for action in ['SET_LANE_SUPPORT_MODE','SET_DRIVER_MONITORING']:
        frame = action+'|sensitivity'
        if frame in model.schema['frames']:
            model.schema['allowed'][frame]['sensitivity'] = ['"min"','"max"']
