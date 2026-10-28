import unittest
import tempfile
import subprocess
from pathlib import Path
from model_delta import encode,decode,BLOCK

class DeltaTests(unittest.TestCase):
    def test_roundtrips(self):
        base=bytes(range(256))*1000
        for target in [base,base[:123],base+b'growth',base[:70000]+b'changed'+base[70007:]]:
            self.assertEqual(decode(base,encode(base,target)),target)
        self.assertEqual(len(encode(base,base)),80)

    def test_corruption_rejected(self):
        base=b'a'*100000;delta=encode(base,b'b'+base[1:])
        for badbase,bad in [(b'x'+base[1:],delta),(base,delta[:-1]),(base,delta+b'x'),(base,delta[:-1]+b'x')]:
            with self.assertRaises(ValueError):
                decode(badbase,bad)

    def test_java_real_training_delta(self):
        root=Path(__file__).resolve().parents[2]
        prior=subprocess.run(['git','show','HEAD~1:voyah-iran/raha-runtime/src/main/assets/raha-intent/model.onnx'],cwd=root,text=True,capture_output=True)
        if prior.returncode:self.skipTest('Pre-migration model not present in parent revision')
        pointer=prior.stdout
        oid=next(line.split(':',1)[1] for line in pointer.splitlines() if line.startswith('oid '))
        old=root/'.git/lfs/objects'/oid[:2]/oid[2:4]/oid
        if not old.exists():self.skipTest('Previous LFS model not cached')
        base=(root/'voyah-iran/raha-runtime/src/main/assets/raha-intent/model.base.onnx').read_bytes()
        model=old.read_bytes();delta=encode(base,model)
        self.assertEqual(decode(base,delta),model)
        self.assertLess(len(delta),4*1024*1024)
        print('Real training delta bytes:',len(delta),'full model:',len(model))
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);(p/'model.base.onnx').write_bytes(base);(p/'model.delta').write_bytes(delta)
            subprocess.run(['javac','--release','17','-d',temp,str(root/'voyah-iran/raha-runtime/tools/RahaModelDelta.java')],check=True)
            subprocess.run(['java','-cp',temp,'RahaModelDelta',temp,str(p/'restored')],check=True)
            self.assertEqual((p/'restored').read_bytes(),model)
            (p/'model.delta').write_bytes(delta[:-1])
            failed=subprocess.run(['java','-cp',temp,'RahaModelDelta',temp,str(p/'invalid')],capture_output=True)
            self.assertNotEqual(failed.returncode,0)

if __name__=='__main__':unittest.main()
