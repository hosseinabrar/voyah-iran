"""Build-only lossless delta. The immutable base remains the same LFS object."""
import hashlib
import struct

MAGIC=b'RAHADLT1'
BLOCK=65536

def encode(base, model):
    blocks=[(offset,model[offset:offset+BLOCK]) for offset in range(0,len(model),BLOCK)
            if model[offset:offset+BLOCK] != base[offset:offset+BLOCK]]
    return (MAGIC+hashlib.sha256(base).digest()+hashlib.sha256(model).digest()
            +struct.pack('>II',len(model),len(blocks))
            +b''.join(struct.pack('>II',offset,len(data))+data for offset,data in blocks))

def decode(base, delta):
    if delta[:8]!=MAGIC or hashlib.sha256(base).digest()!=delta[8:40]:
        raise ValueError('Invalid delta or base')
    length,count=struct.unpack_from('>II',delta,72)
    if not 0<length<=512*1024*1024:raise ValueError('Invalid model length')
    result=bytearray(base[:length]);result.extend(b'\0'*(length-len(result)))
    cursor=80;end=0
    for _ in range(count):
        offset,size=struct.unpack_from('>II',delta,cursor);cursor+=8
        if offset<end or size==0 or size>BLOCK or offset+size>length or cursor+size>len(delta):
            raise ValueError('Invalid delta block')
        result[offset:offset+size]=delta[cursor:cursor+size];cursor+=size;end=offset+size
    if cursor!=len(delta) or hashlib.sha256(result).digest()!=delta[40:72]:
        raise ValueError('Model hash mismatch')
    return bytes(result)
