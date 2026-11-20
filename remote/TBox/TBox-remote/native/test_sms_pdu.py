import importlib.util, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('pdu',Path(__file__).resolve().parents[1]/'telemetry-payload/sms-pdu.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def pdu(body,header=b'',dcs=8):
    # Synthetic international sender +1234, 2026-11-20 12:34:56 UTC.
    raw=body.encode('utf-16-be') if dcs==8 else body
    data=(bytes([len(header)])+header if header else b'')+raw
    return bytes.fromhex('00')+bytes([64 if header else 0])+bytes.fromhex('0491214300')+bytes([dcs])+bytes.fromhex('62110221436500')+bytes([len(data)])+data
class PDUTests(unittest.TestCase):
    def test_persian(self):
        sender,body,stamp,part=m.decode(pdu('تست بک'))
        self.assertEqual((sender,body,stamp,part),('+1234','تست بک','2026-11-20T12:34:56Z',None))
    def test_multipart_out_of_order(self):
        a=m.Assembly()
        self.assertIsNone(a.accept(pdu('بک',bytes([0,3,7,2,2])),10))
        r=a.accept(pdu('تست ',bytes([0,3,7,2,1])),11)
        self.assertEqual(r['body'],'تست بک')
    def test_gsm_septets_and_extension(self):
        values=[65,27,20,66]; bits=sum(v<<(7*i) for i,v in enumerate(values))
        self.assertEqual(m.gsm(bytearray(bits.to_bytes(4,'little')),4),'A^B')
    def test_reject_truncated_and_invalid(self):
        valid=pdu('سلام')
        for n in range(len(valid)):
            with self.assertRaises((ValueError,IndexError,UnicodeError)):m.decode(valid[:n])
        with self.assertRaises(ValueError):m.decode(pdu('x',bytes([0,3,7,2,3])))
    def test_expired_parts(self):
        a=m.Assembly();a.accept(pdu('الف',bytes([0,3,7,2,1])),10)
        self.assertIsNone(a.accept(pdu('ب',bytes([0,3,7,2,2])),400))
if __name__=='__main__':unittest.main()
