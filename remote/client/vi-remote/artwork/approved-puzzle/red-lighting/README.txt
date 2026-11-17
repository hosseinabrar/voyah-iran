Red warning puzzle lighting — 2026-11-16
Generated with built-in imagegen, one call per open puzzle tile.
Prompt specification: lighting-only edit; exact original tile framing and geometry; strong red incident lighting matching approved-reference.png only on moving open panels. Front mirrors follow open front doors; hatch includes spoiler, glass and wiper. Fixed fenders, roof and closed panels remain neutral. Preserve texture, seams, logos and white matte.
Original pieces and runtime assets: ../backups/before-red-lighting-20261116.tar.gz (archive paths relative to repository).
Original edited tiles also retained here as original-*.png.
assemble.cjs imports generated red pixels onto original geometry, preserves non-red pixels and tile boundary bands. It is an offline authoring tool, not shipped runtime code. Resolve playwright through NODE_PATH; optional CHROME_PATH.
Then run ../export-app.py to produce tightly cropped transparent runtime PNGs.
Validation: all 64 state selections unique with four available tiles; browser screenshot of eight representative states at ../../puzzle/qa/states.png.
No server deployment or commit performed.
