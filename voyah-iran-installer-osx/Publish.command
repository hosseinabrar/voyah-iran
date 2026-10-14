#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
exec "${VI_PYTHON:-python3}" build/launch.py "$@"
