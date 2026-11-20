import importlib.util
from pathlib import Path
import struct
import unittest
spec = importlib.util.spec_from_file_location('location_read', Path(__file__).resolve().parents[1]/'telemetry-payload/location-read.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
class LocationReadTest(unittest.TestCase):
    def test_validity_and_original_timestamp(self):
        now = 1795188031000
        raw = bytearray(2536)
        struct.pack_into('<Qq',raw,0,5,now-1000)
        struct.pack_into('<d',raw,24,35)
        struct.pack_into('<d',raw,40,51)
        self.assertEqual(m.parse(raw,now),{'latitude':35,'longitude':51,'at':now-1000})
        for flags,stamp,lat in [(0,now,35),(4,now,35),(5,0,35),(5,now+5001,35),(5,now,91),(5,now,float('nan'))]:
            struct.pack_into('<Qq',raw,0,flags,stamp)
            struct.pack_into('<d',raw,24,lat)
            self.assertIsNone(m.parse(raw,now))
if __name__ == '__main__': unittest.main()
