"""Aggregate saved evaluation without hiding incorrect or rejected parameters."""
import json
from collections import defaultdict
from practice_train import OUT

def metrics(report):
    actions=defaultdict(lambda:[0,0]);fields=defaultdict(lambda:[0,0])
    for row in report['results']:
        actions[row['action']][1]+=1
        actions[row['action']][0]+=int(row['exact_correct'])
        for key,value in row['parameters'].items():
            fields[key][1]+=1
            fields[key][0]+=int(row['action_correct'] and row['predicted']['parameters'].get(key)==value)
    return {'actions':{k:dict(correct=v[0],total=v[1],accuracy=v[0]/v[1]) for k,v in actions.items()},
            'parameters':{k:dict(correct=v[0],total=v[1],accuracy=v[0]/v[1]) for k,v in fields.items()}}

def main():
    before=json.loads((OUT/'baseline-final.json').read_text())
    after=json.loads((OUT/'refined/report.json').read_text())
    result={'baseline':metrics(before),'candidate':metrics(after)}
    (OUT/'parameter-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
    print(json.dumps(result['candidate']['parameters'],ensure_ascii=False,indent=2))

if __name__=='__main__':main()
