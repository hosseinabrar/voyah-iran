"""Pack restored raster artwork into registered, tightly cropped UI sprites.
Requires Pillow. Inputs and outputs are relative to this file; no runtime drawing.
"""
from pathlib import Path
from PIL import Image
import json
p=Path(__file__).resolve().parent
root=p.parents[1]
assets=root/'assets'/'car-puzzle';assets.mkdir(exist_ok=True)
master=Image.open(p/'restored/master-open.png').convert('RGBA').resize((686,1024),Image.Resampling.LANCZOS)
regions={'hood':(0,0,686,340),'left':(0,340,343,770),'right':(343,340,686,770),'rear':(0,770,686,1024)}
manifest={}
scale=500/898
# Raster texture density 2x; CSS coordinates share one immutable canvas.
for source in sorted((p/'originals').glob('*.png')):
 n=source.stem;kind=n.split('_')[0]
 original=Image.open(source).convert('RGBA');restored=Image.open(p/'restored'/source.name).convert('RGBA')
 box=original.getchannel('A').point(lambda v:255 if v>30 else 0).getbbox()
 bb=restored.getchannel('A').point(lambda v:255 if v>30 else 0).getbbox()
 aligned=Image.new('RGBA',(686,1024))
 aligned.paste(restored.crop(bb).resize((box[2]-box[0],box[3]-box[1]),Image.Resampling.LANCZOS),box[:2])
 if n in ('hood_open','rear_open','left_both','right_both'):
  aligned=master.copy()
 if kind in ('left','right'):
  # Common windshield and roof avoid AI-generated differences between door states.
  center=(200,340,486,770)
  aligned.paste(master.crop(center),center[:2])
 region=regions[kind];tile=aligned.crop(region)
 bbox=tile.getbbox();tile=tile.crop(bbox)
 x=region[0]+bbox[0];y=region[1]+bbox[1]
 size=(round(tile.width*scale*2),round(tile.height*scale*2))
 tile.resize(size,Image.Resampling.LANCZOS).save(assets/source.name,optimize=True)
 manifest[n]={'x':round((x-343)*scale,4),'y':round((y-69)*scale,4),'w':round(tile.width*scale,4),'h':round(tile.height*scale,4)}
(p/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(root/'car-puzzle.js').write_text('export const puzzleLayers='+json.dumps(manifest)+';\n'+'''export function selectCarPieces(v={}) {
 const side=(front,rear,a,b)=>front===true?(rear===true?'both':a):(rear===true?b:'close');
 return ['hood_'+(v.hood===true?'open':'close'),
 'left_'+side(v.doorFL,v.doorRL,'fl','rl'),
 'right_'+side(v.doorFR,v.doorRR,'fr','rr'),
 'rear_'+(v.trunk===true?'open':'close')];
}
''')
print('Packed',len(manifest),'sprites;',sum(f.stat().st_size for f in assets.glob('*.png')),'bytes')
