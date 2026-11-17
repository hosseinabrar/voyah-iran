from PIL import Image,ImageDraw
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'assets'
for name in ['refresh','lock','unlock','qr']:
 s=150 if name=='qr' else 32
 im=Image.new('RGBA',(s*4,s*4));d=ImageDraw.Draw(im);c='#f3f6fa';w=10
 if name=='refresh':
  d.arc((22,22,106,106),40,320,fill=c,width=w);d.polygon([(105,23),(111,58),(78,46)],fill=c)
 elif name in ['lock','unlock']:
  d.rounded_rectangle((27,56,101,116),radius=10,fill=c);d.arc((40,8,88,85),180,360 if name=='lock' else 290,fill=c,width=10);d.ellipse((58,77,70,89),fill='#101820');d.line((64,85,64,99),fill='#101820',width=7)
 else:
  for x,y in [(24,24),(336,24),(24,336)]:
   d.rectangle((x,y,x+216,y+216),outline=c,width=28);d.rectangle((x+64,y+64,x+152,y+152),fill=c)
  for x,y in [(336,336),(440,336),(336,440),(492,492),(440,440),(544,388)]:d.rectangle((x,y,x+40,y+40),fill=c)
 im.resize((s,s),Image.Resampling.LANCZOS).save(p/(name+'.png'),optimize=True)
