#!/bin/bash
cd "$(dirname "$0")" || exit 1
arch=$(uname -m); [ "$arch" != x86_64 ] || arch=amd64
"./dist/telemetry-install-darwin-$arch" -root "$PWD"
result=$?
read -r -p 'Press Enter to close.' _
exit "$result"
