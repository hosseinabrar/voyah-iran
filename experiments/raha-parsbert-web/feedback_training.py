"""Local frame/slot-head adaptation with replay and reusable contact-name patterns."""
import copy
import json
import os
import random
from datetime import datetime, timezone
import torch
from safetensors import safe_open
from safetensors.torch import save_file, load_file
from data import normalize
from full_model import frame,encode_value
from contact_extraction import learn_pattern,extract_contact

class FeedbackTrainer:
    def __init__(self, root, model, tokenizer, dataset_path=None, checkpoint_name='feedback-head'):
        self.model, self.tokenizer = model, tokenizer
        self.path = root/'artifacts'/f'{checkpoint_name}.safetensors'
        self.rows = []
        self.contact_rules = []
        self.replay = None
        self.dataset = dataset_path or root/'dataset.json'
        self.actions = [f.split('|')[0] for f in model.schema['frames']]
        if self.path.exists():
            with safe_open(str(self.path), framework='pt') as f:
                meta = f.metadata()
            if json.loads(meta['frames']) != model.schema['frames']:
                raise RuntimeError('Feedback checkpoint does not match model schema')
            self.rows = json.loads(meta['feedback'])
            self.contact_rules=json.loads(meta.get('contact_rules','[]'))
            state=load_file(str(self.path))
            if meta.get('format')=='2':
                model.frame_head.load_state_dict({k[6:]:v for k,v in state.items() if k.startswith('frame.')})
                loaded={k[6:]:v for k,v in state.items() if k.startswith('slots.')}
                current=model.slot_heads.state_dict()
                for key,value in loaded.items():
                    if value.shape!=current[key].shape:
                        if value.shape[1:]!=current[key].shape[1:] or value.shape[0]>current[key].shape[0]:
                            raise RuntimeError('Incompatible feedback slot shape')
                        merged=current[key].clone();merged[:value.shape[0]]=value;loaded[key]=merged
                model.slot_heads.load_state_dict(loaded)
            else:model.frame_head.load_state_dict(state)

    def features(self, texts):
        chunks=[]
        with torch.no_grad():
            for offset in range(0,len(texts),24):
                inputs=self.tokenizer(texts[offset:offset+24],padding=True,truncation=True,max_length=48,return_tensors='pt')
                hidden=self.model.bert(**inputs).last_hidden_state
                mask=inputs['attention_mask'].unsqueeze(-1)
                chunks.append(((hidden*mask).sum(1)/mask.sum(1)).detach())
        return torch.cat(chunks)

    def train(self, text, action, parameters=None, extra_records=None):
        text=normalize(text)
        if not text or action not in self.actions:
            raise ValueError('متن یا نوع فرمان معتبر نیست')
        if len(self.tokenizer.encode(text))>48:
            raise ValueError('برای آموزش یک فرمان کوتاه وارد کن')
        if parameters is not None:
            parameters=dict(parameters)
            if action=='CALL_CONTACT':
                if set(parameters)!={'contact_query'} or not isinstance(parameters['contact_query'],str):raise ValueError('نام مخاطب را وارد کن')
                learn_pattern(text,parameters['contact_query'])
            else:
                target=frame({'action':action,'parameters':parameters})
                if target not in self.model.schema['frames']:raise ValueError('ترکیب پارامترها برای این فرمان معتبر نیست')
                for k,v in parameters.items():
                    allowed=self.model.schema.get('allowed',{}).get(target,{}).get(k,self.model.schema['slots'][k])
                    # JSON/JavaScript do not preserve integer vs floating-point spelling.
                    choices=[json.loads(value) for value in allowed]
                    matches=[value for value in choices if value==v and isinstance(value,bool)==isinstance(v,bool)]
                    if not matches:raise ValueError('مقدار نامعتبر برای '+k)
                    parameters[k]=matches[0]
                if action.startswith('SET_SEAT_') and parameters.get('enabled') is False and parameters.get('level',0)!=0:
                    raise ValueError('وقتی صندلی خاموش است سطح باید صفر باشد')
        if self.replay is None:
            groups={}
            for row in json.loads(self.dataset.read_text())['train']:
                groups.setdefault(frame(row),[]).append(row)
            rng=random.Random(73)
            samples=[r for group in groups.values() for r in rng.sample(group,min(2,len(group)))]
            self.replay=(self.features([r['text'] for r in samples]),samples)
        record=dict(text=text,action=action,time=datetime.now(timezone.utc).isoformat())
        if parameters is not None:record['parameters']=parameters
        extras=extra_records or []
        replacement={r['text'] for r in extras}|{text}
        rows=[r for r in self.rows if r['text'] not in replacement]+extras+[record]
        # Bound each update's work while retaining the complete feedback log on disk.
        recent=rows[-max(256,len(extras)+1):]
        features=self.features([r['text'] for r in recent])
        head=copy.deepcopy(self.model.frame_head)
        slots=copy.deepcopy(self.model.slot_heads)
        trainable=list(head.parameters())+list(slots.parameters())
        optimizer=torch.optim.AdamW(trainable,lr=.001,weight_decay=.01)
        def loss(x, records):
            logp=head(x).log_softmax(-1)
            terms=[]
            for i,r in enumerate(records):
                if 'parameters' not in r or r['action']=='CALL_CONTACT':terms.append(-torch.logsumexp(logp[i,[j for j,a in enumerate(self.actions) if a==r['action']]],0))
                else:terms.append(-logp[i,self.model.schema['frames'].index(frame(r))])
            slotloss=[]
            for k,values in self.model.schema['slots'].items():
                ids=[i for i,r in enumerate(records) if k in r.get('parameters',{})]
                if ids:
                    truth=torch.tensor([values.index(encode_value(records[i]['parameters'][k])) for i in ids])
                    slotloss.append(torch.nn.functional.cross_entropy(slots['slot_'+k](x[ids]),truth))
            return torch.stack(terms).mean()+(torch.stack(slotloss).mean() if slotloss else 0)
        for step in range(600 if extras else 160):
            optimizer.zero_grad()
            objective=loss(features,recent)+2*loss(features[-1:],[record])+.7*loss(*self.replay)
            objective.backward()
            torch.nn.utils.clip_grad_norm_(trainable,1)
            optimizer.step()
        with torch.no_grad():
            predicted=self.model.decode(head(features[-1:]),{k[5:]:h(features[-1:]) for k,h in slots.items()})[0][0]
        rules=list(self.contact_rules)
        if action=='CALL_CONTACT' and parameters is not None:
            rule=learn_pattern(text,parameters['contact_query'])
            if rule not in rules:rules.append(rule)
        learned=predicted['action']==action
        if parameters is not None:
            actual={'contact_query':extract_contact(text,rules)} if action=='CALL_CONTACT' else predicted['parameters']
            learned=learned and actual==parameters
        # Persist before swapping the active head; interrupted writes preserve the old checkpoint.
        temporary=self.path.with_suffix('.tmp')
        state={**{'frame.'+k:v.detach().contiguous() for k,v in head.state_dict().items()},**{'slots.'+k:v.detach().contiguous() for k,v in slots.state_dict().items()}}
        save_file(state,str(temporary),metadata={'format':'2','frames':json.dumps(self.model.schema['frames']),'feedback':json.dumps(rows,ensure_ascii=False),'contact_rules':json.dumps(rules,ensure_ascii=False)})
        os.replace(temporary,self.path)
        self.model.frame_head.load_state_dict(head.state_dict())
        self.model.slot_heads.load_state_dict(slots.state_dict())
        self.contact_rules=rules
        self.rows=rows
        return dict(count=len(rows),learned=learned,scope='contact_pattern' if action=='CALL_CONTACT' and parameters is not None else 'action_and_parameters' if parameters is not None else 'action_only')
