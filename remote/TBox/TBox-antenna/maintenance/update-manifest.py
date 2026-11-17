from pathlib import Path
import hashlib
root = Path(__file__).resolve().parents[1]
payload = root / "payload"
files = sorted(f for f in payload.iterdir() if f.is_file() and f.name != "SHA256SUMS")
(payload / "SHA256SUMS").write_text("".join(hashlib.sha256(f.read_bytes()).hexdigest() + "  " + f.name + "\n" for f in files))
