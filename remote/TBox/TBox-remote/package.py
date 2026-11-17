from pathlib import Path
import hashlib,shutil
root=Path(__file__).resolve().parent
for name in ['hrild-remote','libremote-bridge.so']:
 shutil.copy2(root/'native'/name,root/'payload'/name)
files=sorted(f for f in (root/'payload').iterdir() if f.is_file() and f.name!='SHA256SUMS')
(root/'payload/SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in files))
