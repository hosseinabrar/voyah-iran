#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
mkdir -p .build/settings-cache
xcrun swiftc -module-cache-path .build/settings-cache build/BuildSettings.swift -o .build/VoyahBuildSettings
exec .build/VoyahBuildSettings "$PWD/build/BuildSettings.local.json"
