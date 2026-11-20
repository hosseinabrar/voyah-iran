#!/usr/bin/env python
"""Read-only OEM LocationProxy getter. ABI gated; never starts or reconfigures GNSS."""
from __future__ import print_function
import ctypes as c
import hashlib
import json
import math
import os
import struct
import sys
import time

# Stock C 2.2.1 and installed VT/C 2.2.2 share this exact library/ABI.
KNOWN = {'9a876f8a7fd957b87575a2866ba76ee35aed90980a9cd4aba09584df533dde3b': 2536}

def parse(raw, now):
    flags, stamp = struct.unpack_from('<Qq', raw, 0)
    lat = struct.unpack_from('<d', raw, 24)[0]
    lon = struct.unpack_from('<d', raw, 40)[0]
    if flags & 5 != 5 or not all(not math.isnan(v) and not math.isinf(v) for v in (lat, lon)):
        return None
    if abs(lat) > 90 or abs(lon) > 180 or stamp > now + 5000 or stamp < now - 86400000:
        return None
    return {'latitude': lat, 'longitude': lon, 'at': stamp}

def main():
    paths = ['/mnt/hirain_app/usr/lib/libhframework.so', '/mnt/hirain_app/lib/libhframework.so']
    path = next((p for p in paths if os.path.isfile(p)), None)
    if not path:
        return 2
    with open(path, 'rb') as f:
        size = KNOWN.get(hashlib.sha256(f.read()).hexdigest())
    if not size:
        return 2
    lib = c.CDLL(path)
    singleton = getattr(lib, '_ZN6hirain9SingletonI13LocationProxyE11getInstanceEv')
    singleton.restype = c.c_void_p
    proxy = singleton()
    if not proxy:
        return 3
    read = getattr(lib, '_ZN13LocationProxy10getLocInfoER13LocationInfos')
    read.argtypes = [c.c_void_p, c.c_void_p]
    read.restype = c.c_int
    buf = c.create_string_buffer(size)
    if read(proxy, buf) != 1:
        return 3
    fix = parse(buf.raw, int(time.time() * 1000))
    if not fix:
        return 4
    print(json.dumps(fix))
    return 0

if __name__ == '__main__':
    sys.exit(main())
