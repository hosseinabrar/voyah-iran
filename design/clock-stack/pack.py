"""Offline lossless packing of approved clock artwork.
Usage: python pack.py BASELINE_ASSET_DIRECTORY
Requires Pillow. Baseline assets are available in Git commit 4bc8f13.
Crops existing pixels; never redraws/resamples approved art.
"""
from pathlib import Path
from PIL import Image
import sys, hashlib, json
root=Path(__file__).resolve().parents[2]
src=Path(sys.argv[1])
out=root/'voyah-iran/app/src/main/assets/passenger/baked'
kotlin=root/'voyah-iran/app/src/main/java/com/abrar/voyahiran/feature/launcher/ClockStackPieces.kt'
seen={}
pieces={}
def crop(im,region,label,offset=(0,0)):
    area=im.crop(region)
    bounds=area.getchannel('A').getbbox()
    assert bounds
    area=area.crop(bounds)
    digest=hashlib.sha256(str(area.size).encode()+area.tobytes()).hexdigest()
    if digest not in seen:
        seen[digest]=label
        area.save(out/(label+'.png'),optimize=True)
    x,y=region[0]+bounds[0]+offset[0],region[1]+bounds[1]+offset[1]
    return (seen[digest],x,y,*area.size)
regions={
 1:[((0,70,95,160),'battery'),((0,230,95,320),'fuel'),
    ((0,195,390,225),'track'),((0,355,390,385),'track')],
 2:[((140,55,260,135),'engine'),((0,240,390,255),'separator')],
 3:[((120,160,165,198),'clock'),((0,200,390,220),'separator'),
    ((200,255,215,335),'divider'),((20,345,385,410),'button')]
}
for theme in ('dark','light'):
    for page,rs in regions.items():
        im=Image.open(src/f'clock-stack-{page}-{theme}.png').convert('RGBA')
        row=[crop(im,r,f'clock-stack-{label}-{theme}') for r,label in rs]
        # Verify recomposition matches the approved full-card pixels exactly.
        reconstructed=Image.new('RGBA',im.size)
        for name,x,y,w,h in row:
            reconstructed.alpha_composite(Image.open(out/(name+'.png')),(x,y))
        assert reconstructed.tobytes()==im.tobytes(),(theme,page,'pixel mismatch')
        pieces[theme,page]=row
    for page in range(4):
        im=Image.open(src/f'clock-stack-dots-{page}-{theme}.png').convert('RGBA')
        pieces[theme,10+page]=[crop(im,(0,i*19,12,i*19+13),f'clock-stack-dot-{"active" if i==page else theme}',(398,164)) for i in range(4)]
lines=['package com.abrar.voyahiran.feature.launcher','',
'/** Tight, native-size PNG pieces. Generated offline by design/clock-stack/pack.py. */',
'internal object ClockStackPieces {',
'    data class Piece(val name:String,val x:Float,val y:Float,val width:Float,val height:Float)',
'    val pages=mapOf(']
entries=[]
for (theme,page),ps in pieces.items():
    rows=', '.join('Piece("%s",%sf,%sf,%sf,%sf)'%p for p in ps)
    entries.append('        ("%s" to %s) to listOf(%s)'%(theme,page,rows))
lines+= [',\n'.join(entries), '    )',
'    val paths=pages.values.flatten().map { "passenger/baked/${it.name}.png" }.distinct()','}']
kotlin.write_text('\n'.join(lines)+'\n')
for theme in ('dark','light'):
    for page in range(1,4):(out/f'clock-stack-{page}-{theme}.png').unlink(missing_ok=True)
    for page in range(4):(out/f'clock-stack-dots-{page}-{theme}.png').unlink(missing_ok=True)
def stats(folder):
    files=list(folder.glob('clock-stack*.png'))
    return dict(files=len(files),disk_bytes=sum(f.stat().st_size for f in files),rgba_bytes=sum(Image.open(f).width*Image.open(f).height*4 for f in files))
report=dict(before=stats(src),after=stats(out),full_card_recomposition='pixel-identical')
(root/'design/clock-stack/packing-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
