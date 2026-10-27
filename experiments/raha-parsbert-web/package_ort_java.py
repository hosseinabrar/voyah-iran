"""Isolate official JNI/ORT from Sherpa by renaming ELF dynamic library names.
Same-length edits preserve ELF layout. No instructions or Java classes change.
Android device validation remains required; never use order-dependent pickFirst.
"""
from pathlib import Path
import hashlib,sys,zipfile,struct

UPSTREAM_SHA256='84737dd1fdb715f0ee695c0a9864f6949b66ecb4c46ca733bc9211bf0c2a7b5f'
OLD=b'libonnxruntime.so'
NEW=b'librahaortcore.so'

def sections(data):
    if data[:4]!=b'\x7fELF' or data[5]!=1:
        raise ValueError('Expected little-endian ELF')
    wide=data[4]==2
    offset=struct.unpack_from('<Q' if wide else '<I',data,40 if wide else 32)[0]
    size,count=struct.unpack_from('<HH',data,58 if wide else 46)
    return wide,[struct.unpack_from('<IIQQQQIIQQ' if wide else '<IIIIIIIIII',data,offset+i*size) for i in range(count)]

def dynamic_names(data):
    wide,entries=sections(data)
    names=[]
    for entry in entries:
        if entry[1]!=6:continue
        table=entries[entry[6]]
        strings=data[table[4]:table[4]+table[5]]
        for tag,value in struct.iter_unpack('<QQ' if wide else '<II',data[entry[4]:entry[4]+entry[5]]):
            if tag in (1,14):names.append((tag,strings[value:strings.index(b'\0',value)]))
    return names

def isolate(data,required_tag):
    assert len(OLD)==len(NEW)
    if (required_tag,OLD) not in dynamic_names(data):
        raise ValueError('Expected upstream dependency/SONAME missing')
    output=bytearray(data)
    _,entries=sections(data)
    # Linked .dynstr also contains GNU symbol-version dependency filenames.
    for index in {entry[6] for entry in entries if entry[1]==6}:
        entry=entries[index];start,length=entry[4],entry[5]
        output[start:start+length]=data[start:start+length].replace(OLD+b'\0',NEW+b'\0')
    result=bytes(output)
    if (required_tag,NEW) not in dynamic_names(result) or any(name==OLD for _,name in dynamic_names(result)):
        raise ValueError('Runtime isolation failed')
    return result

def main():
    source=Path(sys.argv[1]);target=Path(sys.argv[2])
    if hashlib.sha256(source.read_bytes()).hexdigest()!=UPSTREAM_SHA256:
        raise ValueError('Unexpected upstream AAR checksum')
    target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(source) as src,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data=src.read(item.filename)
            if item.filename.startswith('jni/'):
                if item.filename.endswith('/libonnxruntime.so'):
                    data=isolate(data,14)
                    item.filename=item.filename.replace(OLD.decode(),NEW.decode())
                elif item.filename.endswith('/libonnxruntime4j_jni.so'):
                    data=isolate(data,1)
            dst.writestr(item,data)
    print('Upstream SHA256:',hashlib.sha256(source.read_bytes()).hexdigest())

if __name__=='__main__':main()
