"""Independent, exact-case human reviews. Never trains/syncs intent weights."""
import json
import sqlite3
from pathlib import Path
from data import normalize
from command_evidence import check

PATH=Path(__file__).resolve().parent/'artifacts/evidence-feedback.sqlite3'

def key(text,action,parameters):
    return json.dumps([normalize(text),action,parameters],ensure_ascii=False,sort_keys=True)

def connect():
    PATH.parent.mkdir(parents=True,exist_ok=True)
    c=sqlite3.connect(PATH)
    c.execute('CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY, target TEXT, allowed INTEGER, note TEXT)')
    return c

def save(text,action,parameters,allowed,note):
    with connect() as c:
        c.execute('INSERT INTO reviews (target,allowed,note) VALUES (?,?,?)',(key(text,action,parameters),int(allowed),note))

def evaluate(text,action,parameters):
    result=check(text,action,parameters)
    result['rule_error']=result['error']
    result['source']='rules'
    with connect() as c:
        row=c.execute('SELECT allowed,note FROM reviews WHERE target=? ORDER BY id DESC LIMIT 1',(key(text,action,parameters),)).fetchone()
    if row:
        allowed,note=row
        result.update(source='human_review',note=note,error=None if allowed else (note or 'این برداشت با بازخورد شما ناسازگار ثبت شده است'))
    result['allowed']=result['error'] is None
    return result
