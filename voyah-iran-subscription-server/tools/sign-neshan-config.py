"""Publisher-only: pip install cryptography. No private key is shipped to clients."""
import argparse
import base64
import json
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=root / 'configs/neshan.source.json')
parser.add_argument('--key', type=Path, default=root / 'secrets/private.pem')
parser.add_argument('--output', type=Path, default=root / 'deployment/configs/neshan.json')
args = parser.parse_args()
doc = json.loads(args.source.read_text(encoding='utf-8-sig'))
assert doc['purpose'] == 'voyah-iran-neshan-config' and doc['schemaVersion'] == 1
assert doc['revision'] > 0 and doc['packageName'] == 'org.rajman.neshan.traffic.tehran.navigator'
assert len({p['apkSha256'] for p in doc['profiles']}) == len(doc['profiles'])
payload = json.dumps(doc, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
if args.output.exists():
    old = json.loads(base64.b64decode(json.loads(args.output.read_text())['payload']))
    if doc != old and doc['revision'] <= old['revision']:
        raise SystemExit('Increase revision before changing published configuration.')
key = serialization.load_pem_private_key(args.key.read_bytes(), password=None)
signature = key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
envelope = json.dumps({'payload': base64.b64encode(payload).decode(),
                      'signature': base64.b64encode(signature).decode()}, indent=2) + '\n'
assert len(envelope.encode()) <= 524288
args.output.parent.mkdir(parents=True, exist_ok=True)
temporary = args.output.with_suffix('.new')
temporary.write_text(envelope, encoding='utf-8', newline='\n')
temporary.replace(args.output)
print('Signed public configuration:', args.output)
