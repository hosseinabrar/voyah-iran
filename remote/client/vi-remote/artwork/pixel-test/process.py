"""Deterministic color/clarity test. No resampling, warping or alpha changes."""
from pathlib import Path
from PIL import Image, ImageFilter, ImageDraw
import json, hashlib
HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'puzzle/originals'
NAMES=['hood_close','left_both','right_both','rear_open']
# All masks use the original 686x1024 coordinate system.
rails=[[(195,338),(205,338),(216,399),(226,444),(232,526),(235,774),(224,796),(218,771),(217,549),(210,447),(200,401)]]
trims=[[(3,431),(163,356),(172,360),(173,366),(12,444)],[(14,631),(167,547),(175,550),(176,557),(23,644)]]
mask=Image.new('L',(686,1024));d=ImageDraw.Draw(mask)
for poly in rails+trims:
 d.polygon(poly,fill=255)
 d.polygon([(686-x,y) for x,y in poly],fill=255)
mask=mask.filter(ImageFilter.GaussianBlur(.65))
ant=Image.new('L',(686,1024));ImageDraw.Draw(ant).ellipse((335,750,353,795),fill=255)
ant=ant.filter(ImageFilter.GaussianBlur(.7))
report={}
for name in NAMES:
 original=Image.open(SOURCE/(name+'.png')).convert('RGBA')
 rgb=original.convert('RGB')
 # Gentle denoising; preserve edges with thresholded unsharp mask.
 rgb=Image.blend(rgb,rgb.filter(ImageFilter.MedianFilter(3)),.12)
 rgb=rgb.filter(ImageFilter.UnsharpMask(radius=.65,percent=65,threshold=3))
 pix=list(rgb.getdata()); mm=list(mask.getdata()); aa=list(ant.getdata()); out=[]
 for (r,g,b),m,a in zip(pix,mm,aa):
  lum=.2126*r+.7152*g+.0722*b
  # Neutral graphite; preserve colored lighting/branding detail.
  saturation=max(r,g,b)-min(r,g,b)
  neutral=.7 if saturation<48 else .15
  gain=1.035
  vals=[(v*(1-neutral)+lum*neutral)*gain for v in (r,g,b)]
  # Piano black retains source highlight structure instead of flat black fill.
  strength=m/255
  black=lum*.24+2
  vals=[v*(1-strength)+black*strength for v in vals]
  # Shark-fin antenna uses graphite body tone, retaining its own shading.
  if a:
   tone=lum*.67+20
   vals=[v*(1-a/255)+tone*a/255 for v in vals]
  out.append(tuple(max(0,min(255,round(v))) for v in vals))
 rgb.putdata(out);result=rgb.convert('RGBA');result.putalpha(original.getchannel('A'))
 # Preserve completely transparent pixel RGB as well.
 result.paste(original,(0,0),original.getchannel('A').point(lambda x:255 if x==0 else 0))
 assert result.size==original.size
 assert result.getchannel('A').tobytes()==original.getchannel('A').tobytes()
 result.save(HERE/(name+'.png'),optimize=True)
 report[name]={'size':list(result.size),'alpha_identical':True,'geometry':'unchanged; no resize/crop/transform','sha256':hashlib.sha256((HERE/(name+'.png')).read_bytes()).hexdigest()}
for label,folder in [('before',SOURCE),('after',HERE)]:
 composite=Image.new('RGBA',(686,1024))
 for n in NAMES:composite.alpha_composite(Image.open(folder/(n+'.png')).convert('RGBA'))
 composite.save(HERE/(label+'-transparent.png'),optimize=True)
 background=Image.new('RGBA',composite.size,'white');background.alpha_composite(composite);background.convert('RGB').save(HERE/(label+'.png'))
comparison=Image.new('RGB',(1372,1024),'white');comparison.paste(Image.open(HERE/'before.png'),(0,0));comparison.paste(Image.open(HERE/'after.png'),(686,0));comparison.save(HERE/'comparison.png')
(HERE/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
