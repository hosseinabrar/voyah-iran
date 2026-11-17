"""Local non-generative contour cleanup; no scale, warp or door changes."""
from pathlib import Path
from PIL import Image, ImageFilter, ImageDraw
from statistics import median
import math,json
P=Path(__file__).resolve().parent
O=P/'edge-refined';O.mkdir(exist_ok=True)
im=Image.open(P/'antialiased/assembled.png').convert('RGBA'); a=im.getchannel('A'); out=a.copy()
# Smooth silhouette only in user-marked hood and rear flanks.
for y0,y1 in [(70,340),(665,945)]:
 edges=[]
 for y in range(y0,y1):
  xs=[x for x in range(120,560) if a.getpixel((x,y))>=128]
  edges.append((min(xs),max(xs)) if xs else (343,343))
 clean=[]
 for i in range(len(edges)):
  vals=[]
  for side in [0,1]:
   vals.append(median(edges[j][side] for j in range(max(0,i-4),min(len(edges),i+5))))
  clean.append(vals)
 for i,(l,r) in enumerate(clean):
  weights=[(j,math.exp(-((j-i)/3)**2/2)) for j in range(max(0,i-8),min(len(clean),i+9))]
  left=sum(clean[j][0]*w for j,w in weights)/sum(w for j,w in weights)
  right=sum(clean[j][1]*w for j,w in weights)/sum(w for j,w in weights)
  # Fade cleanup at region ends, avoiding a new horizontal seam.
  t=min(1,i/10,(len(clean)-1-i)/10)
  left=edges[i][0]*(1-t)+left*t;right=edges[i][1]*(1-t)+right*t
  for x in range(120,560):
   coverage=round(255*max(0,min(1,x-left+.5,right-x+.5)))
   out.putpixel((x,y0+i),coverage)
# Top lip: remove stairsteps with local coverage filtering.
m=Image.new('L',im.size);d=ImageDraw.Draw(m);d.rectangle((210,62,435,92),fill=255);m=m.filter(ImageFilter.GaussianBlur(3))
out=Image.composite(out.filter(ImageFilter.GaussianBlur(.8)),out,m)
# Local material/edge antialiasing. Feather masks, retain panel lines.
m=Image.new('L',im.size);d=ImageDraw.Draw(m)
for poly in [[(150,340),(150,170),(185,102),(221,87),(212,130),(180,180),(176,340)],[(437,78),(483,99),(524,154),(538,301),(507,310),(494,160)],[(205,436),(230,438),(245,600),(244,795),(213,795)],[(443,424),(487,424),(472,799),(438,799)],[(150,666),(184,666),(207,937),(166,917),(145,797)],[(506,660),(539,739),(528,877),(490,946),(481,928),(506,800)]]: d.polygon(poly,fill=220)
m=m.filter(ImageFilter.GaussianBlur(4))
rgb=im.convert('RGB'); smooth=rgb.filter(ImageFilter.MedianFilter(3)).filter(ImageFilter.GaussianBlur(.8));rgb=Image.composite(smooth,rgb,m)
# Extend edge colors from nearest opaque pixel to avoid black halo on new coverage.
p=rgb.load()
for y in range(60,950):
 for x in range(120,560):
  if out.getpixel((x,y)) and a.getpixel((x,y))<128:
   choices=[(dx*dx+dy*dy,x+dx,y+dy) for dx in range(-5,6) for dy in range(-3,4) if 0<=y+dy<1024 and a.getpixel((x+dx,y+dy))>=200]
   if choices:
    _,xx,yy=min(choices);p[x,y]=p[xx,yy]
result=rgb.convert('RGBA');result.putalpha(out);result.save(O/'assembled.png')
regions={'hood_close':(0,0,686,340),'left_both':(0,340,343,770),'right_both':(343,340,686,770),'rear_open':(0,770,686,1024)}
rebuilt=Image.new('RGBA',im.size)
for n,b in regions.items():
 tile=Image.new('RGBA',im.size);tile.paste(result.crop(b),b[:2]);tile.save(O/(n+'.png'));rebuilt.alpha_composite(tile)
assert rebuilt.getchannel("A").tobytes()==result.getchannel("A").tobytes()
for n,img in [('before',im),('after',result)]:
 bg=Image.new('RGBA',im.size,'white');bg.alpha_composite(img);bg.convert('RGB').save(O/(n+'.png'))
print(O)
