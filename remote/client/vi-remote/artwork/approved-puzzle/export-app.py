"""Export approved puzzle tiles as tightly cropped transparent PNGs at 2x UI size."""
from pathlib import Path
from PIL import Image
from collections import deque
import json
P=Path(__file__).resolve().parent
APP=P.parent.parent
manifest=json.loads((P/'manifest.json').read_text())
scale=500/1339
out=APP/'assets/car-puzzle';out.mkdir(exist_ok=True)
layers={}
for name,q in manifest['pieces'].items():
 im=Image.open(P/q['file']).convert('RGBA');w,h=im.size;px=im.load()
 # Flood only exterior white matte; enclosed highlights stay untouched.
 seen=set();todo=deque()
 for x in range(w):todo.extend([(x,0),(x,h-1)])
 for y in range(h):todo.extend([(0,y),(w-1,y)])
 while todo:
  x,y=todo.popleft()
  if (x,y) in seen or not(0<=x<w and 0<=y<h):continue
  seen.add((x,y))
  r,g,b,a=px[x,y]
  if min(r,g,b)<200:continue
  px[x,y]=(0,0,0,0)
  todo.extend([(x-1,y),(x+1,y),(x,y-1),(x,y+1)])
 bounds=im.getbbox();im=im.crop(bounds)
 width,height=im.size
 im=im.resize((round(width*scale*2),round(height*scale*2)),Image.Resampling.LANCZOS)
 im.save(out/f'{name}.png',optimize=True)
 layers[name]={'x':round((q['x']+bounds[0]-513.5)*scale,5),'y':round((q['y']+bounds[1]-106)*scale,5),'w':round(width*scale,5),'h':round(height*scale,5)}
js=APP/'car-puzzle.js'
select=js.read_text().split('export function selectCarPieces',1)[1]
js.write_text('export const puzzleLayers='+json.dumps(layers,separators=(',',':'))+';\nexport function selectCarPieces'+select)
print('Exported',len(layers),'tiles;',sum(f.stat().st_size for f in out.glob('*.png')),'bytes')
