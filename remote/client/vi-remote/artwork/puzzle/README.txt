Vehicle puzzle artwork
======================
User-supplied sources: originals/*.png (unchanged copies).
Restoration: built-in image_gen edit mode, transparent output.
Prompt: Preserve exact partial-car silhouette, perspective, door angle, canvas-relative placement and slice boundaries. Clean noise/pixelation. Medium neutral metallic graphite body; piano-black window trim and roof rails. Preserve real panel seams, mirrors, glass, lights and visible branding. Do not invent missing vehicle pieces.
Rear closed follow-up: no added VOYAH lettering or taillights (not visible in original closed top view).
Master-open follow-up: repair center/slice seams and unify paint while preserving four open doors, hood and trunk.

prepare.py packs these local sources into assets/car-puzzle/*.png and car-puzzle.js.
Pillow is needed only for offline packing. No image generation or canvas drawing at runtime.
Each layer is alpha-cropped; coordinates refer to one 686x1024 source canvas.
A shared windshield/roof crop avoids changes to the cabin between door states.
Twelve selectable images represent 64 combinations (4 left x 4 right x 2 hood x 2 rear).

Validation:
- tests/car-puzzle.cjs checks all 64 unique selections and required assets; renders eight representative combinations.
- tests/browser-smoke.cjs checks encrypted snapshot rendering.
- qa/states.png and qa/vehicle.png are review renders, not application assets.

Visual limitation: AI restoration changed some boundaries/material highlights. The joins near the windshield and rear roof remain visible in some combinations. These restored variants are not a pixel-exact reproduction of the original geometry. Originals are retained for comparison and further correction.
Sunroof warning remains textual; the previous unrelated car's sunroof image is no longer overlaid on the new roof. Charge/fuel flap layers are still the existing assets.
