#!/bin/bash
cd "$(dirname "$0")" || exit 1
arch=$(uname -m); [ "$arch" != x86_64 ] || arch=amd64
chmod +x "dist/client-darwin-$arch"
open http://127.0.0.1:18781/
"./dist/client-darwin-$arch"
