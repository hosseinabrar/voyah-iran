"""Remove known tile seams and antialias without moving car geometry.
Works on the four color-corrected original tiles, never generated artwork.
"""
from pathlib import Path
from PIL import Image, ImageFilter, ImageChops
import json
P=Path(__file__).resolve().parent
O=P/'antialiased';O.mkdir(exist_ok=True)
names=['hood_close','left_both','right_both','rear_open']
im=Image.new('RGBA',(686,1024))
for n in names:im.alpha_composite(Image.open(P/(n+'.png')))
a=im.getchannel('A');rgb=im.convert('RGB');px=rgb.load()
# Only known artificial straight cut lines; no panel/door outlines touched.
# Interpolate along the normal, between intact immediate neighbors.
for start,end in [(338,342),(767,771)]:
 for x in range(686):
  if a.getpixel((x,start-1))<230 or a.getpixel((x,end+1))<230:continue
  c0=px[x,start-1];c1=px[x,end+1]
  for y in range(start,end+1):
   t=(y-start+1)/(end-start+2)
   px[x,y]=tuple(round(u*(1-t)+v*t) for u,v in zip(c0,c1))
for y in range(343,767):
 c0=px[339,y];c1=px[346,y]
 for x in range(340,346):
  t=(x-339)/7
  px[x,y]=tuple(round(u*(1-t)+v*t) for u,v in zip(c0,c1))
# Low-radius anti-aliasing, not reconstruction or sharpening of block artifacts.
rgb=rgb.filter(ImageFilter.GaussianBlur(.58))
# Soft coverage only at silhouette; preserve original 50% contour exactly.
soft=a.filter(ImageFilter.GaussianBlur(.48));av=list(a.getdata());sv=list(soft.getdata())
soft.putdata([max(128,s) if v>=128 else min(127,s) for v,s in zip(av,sv)])
# Prevent dark fringes: smooth premultiplied colors, then unpremultiply.
channels=[]
for channel in rgb.split():
 premul=ImageChops.multiply(channel,a).filter(ImageFilter.GaussianBlur(.4))
 coverage=a.filter(ImageFilter.GaussianBlur(.4))
 channels.append(Image.new('L',im.size))
 channels[-1].putdata([min(255,round(c*255/v)) if v else 0 for c,v in zip(premul.getdata(),coverage.getdata())])
result=Image.merge('RGBA',(*channels,soft))
# Exactly partition the canvas; no overlap/double-alpha at internal seams.
regions={'hood_close':(0,0,686,340),'left_both':(0,340,343,770),'right_both':(343,340,686,770),'rear_open':(0,770,686,1024)}
rebuilt=Image.new('RGBA',im.size)
for name,box in regions.items():
 tile=Image.new('RGBA',im.size);tile.paste(result.crop(box),box[:2]);tile.save(O/(name+'.png'),optimize=True);rebuilt.alpha_composite(tile)
assert rebuilt.tobytes()==result.tobytes()
assert all((v>=128)==(s>=128) for v,s in zip(av,soft.getdata()))
result.save(O/'assembled.png',optimize=True)
# 2x is interpolation for comfortable viewing, not claimed recovered detail.
result.resize((1372,2048),Image.Resampling.LANCZOS).save(O/'assembled-2x.png',optimize=True)
for label,img in [('before',im),('after',result)]:
 bg=Image.new('RGBA',img.size,'white');bg.alpha_composite(img);bg.convert('RGB').save(O/(label+'.png'))
compare=Image.new('RGB',(1372,1024),'white');compare.paste(Image.open(O/'before.png'),(0,0));compare.paste(Image.open(O/'after.png'),(686,0));compare.save(O/'comparison.png')
(O/'verification.json').write_text(json.dumps({'canvas':[686,1024],'silhouette_50_percent_identical':True,'four_piece_reassembly_byte_identical':True,'seam_rows':[[338,342],[767,771]],'seam_columns':[340,345],'transform':None,'display_2x':'Lanczos interpolation only'},indent=2)+'\n')
print('PASS: silhouette unchanged; four-piece composition identical to corrected image')
