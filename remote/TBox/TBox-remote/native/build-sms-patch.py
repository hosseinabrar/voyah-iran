"""Offline only. Build against the explicitly identified 2.0.7 hrild input."""
from pathlib import Path
import argparse,hashlib,struct,subprocess
from elftools.elf.elffile import ELFFile
p=argparse.ArgumentParser();p.add_argument('original');p.add_argument('output');a=p.parse_args()
source=bytearray(Path(a.original).read_bytes())
assert hashlib.sha256(source).hexdigest()=='25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721'
out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True)
asm='''.text
.global wrapper
wrapper:
sub sp, sp, #48
stp x29,x30,[sp]
mov x29,sp
stp x0,x1,[sp,#16]
str x2,[sp,#32]
bl signal_singleton
ldr x1,[sp,#24]
ldr x2,[sp,#32]
bl signal_init
bl sms_singleton
ldr x1,[sp,#24]
ldr x2,[sp,#32]
bl sms_init
ldp x0,x1,[sp,#16]
ldr x2,[sp,#32]
bl voice_init
ldp x29,x30,[sp]
add sp,sp,#48
ret
'''
asmfile=out.with_suffix('.S');obj=out.with_suffix('.o');asmfile.write_text(asm)
subprocess.run(['clang','-target','aarch64-linux-gnu','-c',str(asmfile),'-o',str(obj)],check=True)
start=0x6df80
with obj.open('rb') as f:
 elf=ELFFile(f);code=bytearray(elf.get_section_by_name('.text').data());rel=elf.get_section_by_name('.rela.text');syms=elf.get_section(rel['sh_link'])
 targets={'signal_singleton':0x288e4,'signal_init':0x4121c,'sms_singleton':0x4341c,'sms_init':0x42ae8,'voice_init':0x44184}
 for r in rel.iter_relocations():
  name=syms.get_symbol(r['r_info_sym']).name;off=r['r_offset'];assert r['r_info_type']==283
  struct.pack_into('<I',code,off,0x94000000|(((targets[name]-start-off)//4)&0x3ffffff))
assert start+len(code)<0x6e658 and not any(source[start:start+len(code)])
source[start:start+len(code)]=code
assert source[0x1ec0c:0x1ec10]==bytes.fromhex('5e950094')
struct.pack_into('<I',source,0x1ec0c,0x94000000|((start-0x1ec0c)//4))
phoff=struct.unpack_from('<Q',source,32)[0];size,count=struct.unpack_from('<HH',source,54)
for i in range(count):
 o=phoff+i*size;typ,flags,off,va,pa,fs,ms,align=struct.unpack_from('<IIQQQQQQ',source,o)
 if typ==1 and off==0:
  assert fs==0x6df7f and fs==ms
  struct.pack_into('<QQ',source,o+32,start+len(code),start+len(code))
out.write_bytes(source);print('Built signal + SMS initialization patch:',hashlib.sha256(source).hexdigest())
