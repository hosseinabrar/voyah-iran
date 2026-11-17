#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p TBox/TBox-remote/dist server/dist client/web-test/dist
export CGO_ENABLED=0
GOOS=linux GOARCH=arm64 go build -trimpath -ldflags='-s -w' -o TBox/TBox-remote/payload/tbox-remote ./TBox/TBox-remote/agent
GOOS=linux GOARCH=amd64 go build -trimpath -ldflags='-s -w' -o server/dist/remote-gateway ./server
for target in darwin/arm64 darwin/amd64 windows/amd64; do
 os=${target%/*}; arch=${target#*/}; suffix=''; [ "$os" != windows ] || suffix=.exe
 GOOS="$os" GOARCH="$arch" go build -trimpath -ldflags='-s -w' -o "TBox/TBox-remote/dist/install-$os-$arch$suffix" ./TBox/TBox-remote/installer
 GOOS="$os" GOARCH="$arch" go build -trimpath -ldflags='-s -w' -o "client/web-test/dist/client-$os-$arch$suffix" ./client/web-test
 done
python3 TBox/TBox-remote/package.py
