from pathlib import Path
from PIL import Image,ImageDraw
import json,itertools,zipfile
P=Path(__file__).resolve().parent
W,H=1027,1532
O=P/'pieces';O.mkdir(exist_ok=True)
images={}
for n in ['closed','front','rear','both']:
 src=Image.open(P/'sources'/f'{n}.png').convert('RGB')
 im=Image.new('RGB',(W,H),'white');im.paste(src,(0,0));images[n]=im
base=images['closed']
# All shared roof and cut boundaries use the same approved closed master.
# Blend only to shared content, never blur any image.
for n in ['front','rear','both']:
 mask=Image.new('L',(W,H)); mp=mask.load()
 for y in range(500,1180):
  fy=min(1,(y-500)/18,(1179-y)/18)
  for x in range(W):
   fx=max(0,min(1,(345-x)/18,(x-680)/18)) if False else (min(1,max(0,(345-x)/18)) if x<513 else min(1,max(0,(x-680)/18)))
   mp[x,y]=round(255*fy*fx)
 images[n]=Image.composite(images[n],base,mask)
regions={'hood_close':('closed',(0,0,W,500)),'rear_open':('closed',(0,1180,W,H))}
for side,xx in [('left',(0,513)),('right',(513,W))]:
 for state,source in [('close','closed'),('fl' if side=='left' else 'fr','front'),('rl' if side=='left' else 'rr','rear'),('both','both')]:
  regions[f'{side}_{state}']=(source,(xx[0],500,xx[1],1180))
manifest={'canvas':[W,H],'pieces':{},'method':'Approved full-image crops; common master roof and boundary bands; no geometry warping. White background retained in this preview set.'}
for n,(src,b) in regions.items():
 images[src].crop(b).save(O/f'{n}.png',optimize=True)
 manifest['pieces'][n]={'file':f'pieces/{n}.png','x':b[0],'y':b[1],'width':b[2]-b[0],'height':b[3]-b[1]}
(P/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
left=['left_close','left_fl','left_rl','left_both'];right=['right_close','right_fr','right_rr','right_both']
preview=Image.new('RGB',(4*300,4*478),'#e6e8ea')
for i,(l,r) in enumerate(itertools.product(left,right)):
 im=Image.new('RGB',(W,H),'white')
 for n in ['hood_close',l,r,'rear_open']:
  q=manifest['pieces'][n];im.paste(Image.open(O/f'{n}.png'),(q['x'],q['y']))
 if i==9:im.save(P/'mixed-example.png')
 thumb=im.resize((300,448),Image.Resampling.LANCZOS)
 x=(i%4)*300;y=(i//4)*478;preview.paste(thumb,(x,y));ImageDraw.Draw(preview).text((x+6,y+454),f'{l} + {r}',fill='black')
preview.save(P/'all-16-combinations.jpg',quality=94)
with zipfile.ZipFile(P/'approved-puzzle-10.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(O.glob('*.png')):z.write(f,f.relative_to(P))
 z.write(P/'manifest.json','manifest.json')
print('Created 10 pieces and 16 composite previews')

# Add front/rear alternatives without changing any of the approved ten pieces.
for name,source,box in [
 ('hood_open','hood-open',(0,0,W,500)),
 ('rear_close','trunk-closed',(0,1180,W,H)),
]:
 src=Image.open(P/'sources'/f'{source}.png').convert('RGB')
 layer=Image.new('RGB',(W,H),'white');layer.paste(src,(0,0))
 # Keep the exact shared boundary; transition within the new tile only.
 mask=Image.new('L',(W,H),255)
 d=ImageDraw.Draw(mask)
 if name=='hood_open':
  for y in range(480,500):d.line((0,y,W,y),fill=round(255*(499-y)/19))
 else:
  for y in range(1180,1200):d.line((0,y,W,y),fill=round(255*(y-1180)/19))
 Image.composite(layer,base,mask).crop(box).save(O/f'{name}.png',optimize=True)
 manifest['pieces'][name]={'file':f'pieces/{name}.png','x':box[0],'y':box[1],'width':box[2]-box[0],'height':box[3]-box[1]}
(P/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
sheet=Image.new('RGB',(1200,926),'white')
for i,(hood,trunk) in enumerate(itertools.product(['hood_close','hood_open'],['rear_close','rear_open'])):
 im=Image.new('RGB',(W,H),'white')
 for n in [hood,'left_close','right_close',trunk]:
  q=manifest['pieces'][n];im.paste(Image.open(O/f'{n}.png'),(q['x'],q['y']))
 if hood=='hood_open' and trunk=='rear_close':im.save(P/'hood-open-trunk-closed.png')
 sheet.paste(im.resize((300,448),Image.Resampling.LANCZOS),(i*300,30))
 ImageDraw.Draw(sheet).text((i*300+4,8),hood+' / '+trunk,fill='black')
sheet.crop((0,0,1200,478)).save(P/'hood-trunk-combinations.jpg',quality=95)
with zipfile.ZipFile(P/'approved-puzzle-12.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(O.glob('*.png')):z.write(f,f.relative_to(P))
 z.write(P/'manifest.json','manifest.json')
print('Added hood_open and rear_close; 12 pieces total')
