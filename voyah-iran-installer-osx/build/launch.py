"""Select the configured Python before loading the publish implementation."""
import json
import os
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
path = root / "build/Toolchain.local.json"
settings = json.loads(path.read_text()) if path.exists() else {}
python = os.environ.get("VI_PYTHON") or settings.get("Python") or sys.executable
env = os.environ.copy()
env["PYTHONDONTWRITEBYTECODE"] = "1"
os.execve(python, [python, str(root / "build/publish.py"), *sys.argv[1:]], env)
