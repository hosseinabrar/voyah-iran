#!/bin/bash
cd "$(dirname "$0")" || exit 1
arch=$(uname -m); [ "$arch" != x86_64 ] || arch=amd64
chmod +x "dist/install-darwin-$arch"
"./dist/install-darwin-$arch" -root "$PWD" -uninstall
result=$?
read -r -p 'Press Enter to close.' _
exit "$result"
