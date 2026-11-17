from PIL import Image
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
opened=Image.open(root/'artwork/sources/open.png').convert('RGBA')
closed=Image.open(root/'artwork/sources/closed.png').convert('RGBA')
print(opened.size,closed.size)
# All coordinates refer to the aligned source canvas. Each shipped layer is tightly cropped.
w,h=opened.size; origin=100; scale=500/1480
parts={'doorFL':(0,475,265,770),'doorFR':(710,475,944,770),'doorRL':(0,780,265,1110),'doorRR':(710,780,944,1110),'hood':(190,110,760,425),'sunroof':(350,610,600,1040),'trunk':(235,1210,720,1540),'chargeFlap':(100,1270,205,1380),'fuelFlap':(745,1270,850,1380)}
layers={}
for key,box in [('car',closed.getbbox())]+list(parts.items()):
 source=closed if key=='car' else opened
 crop=source.crop(box);bbox=crop.getbbox()
 if not bbox:continue
 crop=crop.crop(bbox);x=box[0]+bbox[0];y=box[1]+bbox[1]
 size=(round(crop.width*scale),round(crop.height*scale));crop.resize(size,Image.Resampling.LANCZOS).save(root/'assets'/f'{key}.png',optimize=True)
 layers[key]={'x':round((x-w/2)*scale,2),'y':round((y-origin)*scale,2),'w':size[0],'h':size[1]}
(root/'car-layers.js').write_text('export const carLayers='+json.dumps(layers)+';\n')
