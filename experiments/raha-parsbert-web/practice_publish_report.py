"""Publish transparent summary; keep structural and colloquial audits separate."""
import json
from practice_train import OUT,summary

def main():
    path=OUT/'final/report.json'
    structural=json.loads(path.read_text())
    if 'structural_holdout' in structural:raise RuntimeError('Report already published')
    audits=json.loads((OUT/'final-colloquial-audit.json').read_text())
    before,after=audits['baseline'],audits['candidate']
    if after['exact_accuracy']<=before['exact_accuracy']:raise RuntimeError('Candidate did not improve')
    data=json.loads((OUT/'dataset.json').read_text())
    corrections=json.loads((OUT/'contrast-corrections.json').read_text())
    dev=json.loads((OUT/'dev-epoch-3.json').read_text())
    trained=data['train']+corrections+[r for r in dev['results'] if not r['exact_correct']]
    unique={r['text'] for r in trained}
    assert not unique.intersection(r['text'] for r in after['results'])
    (OUT/'final/structural-report.json').write_text(json.dumps(structural,ensure_ascii=False,indent=2))
    report={**structural,**after,'baseline':summary(before),'structural_holdout':summary(structural),
            'train_count':len(unique),'epochs':13,'training_rounds':[6,4,3],
            'evaluation':'36 fresh hand-written colloquial text cases, never trained. Small synthetic audit; not speech or real-world accuracy.',
            'known_limitation':'One out-of-domain home-TV mute request was incorrectly accepted as vehicle audio mute in the final text audit.'}
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:report[k] for k in ['train_count','epochs','action_accuracy','exact_accuracy','baseline']},ensure_ascii=False))

if __name__=='__main__':main()
