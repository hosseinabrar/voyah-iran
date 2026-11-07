#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
REPO="$(cd ../.. && pwd)"
bash "$REPO/voyah-iran/gradlew" -p "$REPO/voyah-iran" :command-core:writeOldRahaClasspath --console=plain
mkdir -p build
"$JAVA_HOME/bin/javac" -cp "$(cat "$REPO/voyah-iran/raha-core/build/old-raha-classpath.txt")" -d build OldRahaWorker.java
exec ../raha-parsbert-web/.venv/bin/python -m uvicorn server:app --host "${RAHA_HOST:-127.0.0.1}" --port "${RAHA_OLD_PORT:-18770}" "$@"
