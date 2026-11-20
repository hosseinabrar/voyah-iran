#!/usr/bin/env python
# UTF-8
"""Receive-only QL SDK PDU adapter. Never sends, deletes or changes modem settings."""
from __future__ import print_function
import binascii, calendar, ctypes as c, datetime, hashlib, json, math, os, struct, sys, time
SDK_HASH='5896d6f51b0f92246b36887313e5c955c76abf473525c193d3fdf132c40ef519'
GSM=u'@\u00a3$\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\n\u00d8\u00f8\r\u00c5\u00e5\u0394_\u03a6\u0393\u039b\u03a9\u03a0\u03a8\u03a3\u0398\u039e\x1b\u00c6\u00e6\u00df\u00c9 !"#\u00a4%&\'()*+,-./0123456789:;<=>?\u00a1ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00c4\u00d6\u00d1\u00dc\u00a7\u00bfabcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00f1\u00fc\u00e0'
EXT={10:u'\f',20:u'^',40:u'{',41:u'}',47:u'\\',60:u'[',61:u'~',62:u']',64:u'|',101:u'\u20ac'}

def gsm(data,count,offset=0):
    out=[];escape=False
    for i in range(count):
        bit=offset+7*i;pos=bit//8;shift=bit%8
        if bit+7>len(data)*8:raise ValueError('truncated septets')
        value=(data[pos]>>shift)|((data[pos+1]<<(8-shift)) if shift>1 and pos+1<len(data) else 0)
        value &=127
        if escape:
            out.append(EXT.get(value,u'\ufffd'));escape=False
        elif value==27:escape=True
        else:out.append(GSM[value])
    if escape:raise ValueError('truncated escape')
    return u''.join(out)

def decode(raw):
    d=bytearray(raw)
    def take(n):
        if take.pos+n>len(d):raise ValueError('truncated PDU')
        v=d[take.pos:take.pos+n];take.pos+=n;return v
    take.pos=0
    take(take(1)[0]);first=take(1)[0]
    if first&3:raise ValueError('not SMS DELIVER')
    digits,toa=take(2)
    address=take((digits+1)//2)
    if toa&0x70==0x50:sender=gsm(address,(digits*4)//7)
    else:
        chars='0123456789*#abcF'
        sender=''.join(chars[x&15]+chars[x>>4] for x in address)[:digits]
        if toa&0x70==0x10:sender='+'+sender
    pid,dcs=take(2)
    if dcs&0xc0==0:
        if dcs&0x20:raise ValueError('compressed SMS unsupported')
        alphabet=(dcs>>2)&3
    elif dcs&0xf0 in (0xc0,0xd0):alphabet=0
    elif dcs&0xf0==0xe0:alphabet=2
    elif dcs&0xf0==0xf0:alphabet=1 if dcs&4 else 0
    else:raise ValueError('unsupported DCS')
    t=take(7)
    def bcd(x):
        if x&15>9 or x>>4>9:raise ValueError('invalid BCD')
        return (x&15)*10+(x>>4)
    vals=[bcd(x) for x in t[:6]]
    zone=bcd(t[6]&0xf7)*15*(-1 if t[6]&8 else 1)
    date=datetime.datetime(2000+vals[0],*vals[1:])-datetime.timedelta(minutes=zone)
    stamp=date.strftime('%Y-%m-%dT%H:%M:%SZ')
    length=take(1)[0];payload=take((length*7+7)//8 if alphabet==0 else length)
    header=0;part=None
    if first&64:
        if not payload:raise ValueError('missing UDH')
        header=payload[0]+1
        if header>len(payload):raise ValueError('truncated UDH')
        i=1
        while i<header:
            if i+2>header:raise ValueError('truncated IE')
            kind,n=payload[i:i+2];i+=2
            if i+n>header:raise ValueError('truncated IE data')
            v=payload[i:i+n];i+=n
            if kind==0 and n==3:part=(v[0],v[1],v[2])
            elif kind==8 and n==4:part=(v[0]*256+v[1],v[2],v[3])
            elif kind in (0x24,0x25):raise ValueError('national shift table unsupported')
    if alphabet==0:
        skip=(header*8+6)//7
        if skip>length:raise ValueError('invalid UDL')
        body=gsm(payload,length-skip,skip*7)
    elif alphabet==2:
        body=bytes(payload[header:]).decode('utf-16-be') if sys.version_info[0]>=3 else str(payload[header:]).decode('utf-16-be')
    else:raise ValueError('binary SMS unsupported')
    if part and not 1<=part[2]<=part[1]<=20:raise ValueError('invalid multipart')
    return sender,body,stamp,part

class Assembly(object):
    def __init__(self):self.parts={}
    def accept(self,raw,now):
        sender,body,stamp,part=decode(raw)
        self.parts={k:v for k,v in self.parts.items() if now-v['at']<300}
        if part:
            ref,total,index=part;key=(sender,ref,total)
            if key not in self.parts:
                if len(self.parts)>=64:raise ValueError('multipart capacity')
                self.parts[key]={'at':now,'items':{}}
            entry=self.parts[key];entry['items'][index]=(body,stamp)
            if len(entry['items'])!=total:return None
            body=u''.join(entry['items'][i][0] for i in range(1,total+1));stamp=entry['items'][1][1]
            del self.parts[key]
        if len(body.encode('utf-8'))>6000:raise ValueError('message too large')
        return {'sender':sender,'body':body,'stamp':stamp}

def emit(value):
    print(json.dumps(value));sys.stdout.flush()

def main():
    path='/usr/lib64/libql_sdk.so'
    with open(path,'rb') as f:
        if hashlib.sha256(f.read()).hexdigest()!=SDK_HASH:return 2
    lib=c.CDLL(path);assembly=Assembly()
    callback=c.CFUNCTYPE(None,c.c_int,c.c_void_p)
    @callback
    def receive(sim,ptr):
        if sim!=1 or not ptr:return
        try:
            header=c.string_at(ptr,8);kind,length=struct.unpack('<ii',header)
            if not 1<=length<=256:return
            result=assembly.accept(c.string_at(ptr+8,length),time.time())
            if result:emit(result)
        except (ValueError,UnicodeError,IndexError,OverflowError):
            # Never log phone numbers or content.
            sys.stderr.write('SMS PDU decode rejected\n')
    lib.ql_ms_sms_init.restype=c.c_int
    lib.ql_ms_sms_set_pdu_recv_cb.argtypes=[c.c_int,callback]
    if lib.ql_ms_sms_init()!=0:return 3
    if lib.ql_ms_sms_set_pdu_recv_cb(1,receive)!=0:return 4
    emit({'ready':True})
    while True:time.sleep(60)
if __name__=='__main__':sys.exit(main())
