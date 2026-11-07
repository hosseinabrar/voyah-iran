"""Run explicitly against the local trainer."""
from test_ambient_everyday import predict

TARGETS = {'central': ('مرکزی','وسط'), 'passenger': ('راست','شاگرد','همراه','سمت راست'),
           'cluster': ('راننده','سمت چپ')}
CHANGES = {10: ('روشن تر','پر نور تر'), -10: ('تاریک تر','تیره تر','کم نور تر')}

def run():
    targets = [(noun+'ها','all') for noun in ('نمایشگر','مانیتور')]
    targets += [(noun+' '+alias, target) for target, aliases in TARGETS.items()
                for alias in aliases for noun in ('نمایشگر','مانیتور')]
    failures = []
    total = 0
    for subject, target in targets:
        for delta, changes in CHANGES.items():
            for change in changes:
                text = f'{subject} {change} کن'
                r = predict(text)
                total += 1
                if not (r['accepted'] and round(100*r['prediction']['score']) == 100
                        and r['prediction']['label'] == 'SET_DISPLAY_BRIGHTNESS'
                        and r['slots'] == {'display':target, 'deltaPercent':delta}):
                    failures.append(text)
    print(f'{total-len(failures)}/{total}')
    if failures:
        raise SystemExit(failures)

if __name__ == '__main__':
    run()
