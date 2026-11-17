from pathlib import Path
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parent
assets=root.parent.parent/'assets'
for name,symbol,color in [('charge-red','lightning','#FF3545'),('charge-yellow','lightning','#FFBE20'),('charge-green','lightning','#20C963'),('fuel-red','fuel','#FF3545')]:
    mask=Image.open(root/(symbol+'-source.png')).convert('L').point(lambda v: 255 if v>128 else 0)
    mask=mask.crop(mask.getbbox())
    mask.thumbnail((304,304),Image.Resampling.LANCZOS)
    out=Image.new('RGBA',(512,512))
    ImageDraw.Draw(out).ellipse((0,0,511,511),fill=color)
    out.paste((255,255,255,255),((512-mask.width)//2,(512-mask.height)//2),mask)
    out.resize((80,80),Image.Resampling.LANCZOS).save(assets/(name+'.png'),optimize=True)
preview=Image.new('RGB',(480,120),'#101820')
for i,name in enumerate(['charge-red','charge-yellow','charge-green','fuel-red']):
    icon=Image.open(assets/(name+'.png'))
    preview.paste(icon,(20+i*120,20),icon)
preview.save(root/'preview.png')
