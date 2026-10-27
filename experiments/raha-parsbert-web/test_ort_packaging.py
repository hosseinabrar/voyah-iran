"""Checks real shipped ELF dependencies for every supported ABI."""
import unittest
import zipfile
import struct
from pathlib import Path
from package_ort_java import dynamic_names, sections, NEW, OLD, isolate

LIBS = Path(__file__).resolve().parents[2] / 'voyah-iran/raha-runtime/libs'

def exports(data, name):
    wide, entries=sections(data)
    for entry in entries:
        if entry[1]!=11:continue # SHT_DYNSYM, not a debug symbol table
        table=entries[entry[6]]
        strings=data[table[4]:table[4]+table[5]]
        for offset in range(entry[4],entry[4]+entry[5],entry[9]):
            symbol=struct.unpack_from('<IBBHQQ' if wide else '<IIIBBH',data,offset)
            index=symbol[0]
            info,visibility,section=symbol[1:4] if wide else symbol[3:6]
            if strings[index:strings.index(b'\0',index)]==name and section!=0 and info>>4 in (1,2) and visibility&3==0:
                return True
    return False

class RuntimePackagingTests(unittest.TestCase):
    def test_isolated_runtime_for_every_abi(self):
        with zipfile.ZipFile(LIBS/'onnxruntime-java-1.19.2-shared.aar') as intent, zipfile.ZipFile(LIBS/'sherpa-onnx-1.13.8.aar') as speech:
            for abi in ('arm64-v8a','armeabi-v7a','x86','x86_64'):
                prefix=f'jni/{abi}/'
                with self.subTest(abi=abi):
                    self.assertNotIn(prefix+OLD.decode(),intent.namelist())
                    runtime=intent.read(prefix+NEW.decode())
                    bridge=intent.read(prefix+'libonnxruntime4j_jni.so')
                    self.assertIn((14,NEW),dynamic_names(runtime))
                    self.assertIn((1,NEW),dynamic_names(bridge))
                    self.assertNotIn((1,OLD),dynamic_names(bridge))
                    self.assertIn((14,OLD),dynamic_names(speech.read(prefix+OLD.decode())))
                    self.assertTrue(exports(runtime,b'OrtGetApiBase'))
                    # Fails closed rather than silently accepting unknown inputs.
                    with self.assertRaises(ValueError):isolate(runtime,14)

if __name__=='__main__':unittest.main()
