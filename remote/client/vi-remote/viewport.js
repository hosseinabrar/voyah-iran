const root=document.getElementById('vehicle'),frame=document.createElement('div');
frame.className='page-frame';root.querySelector('header').after(frame);
const panels=['telemetry','batteryPanel','securityPanel','messagesPanel'].map(id=>document.getElementById(id));
for(const panel of panels)frame.append(panel);
let queued=false;
function fit(){queued=false;const h=frame.clientHeight;if(!h||root.hidden)return;
 const scene=document.querySelector('.car-scene');
 document.querySelector('.car-stage').style.setProperty('--stage-scale',String(Math.min(1,scene.clientHeight/450,frame.clientWidth/390)));
 for(const panel of panels.slice(1)){if(panel.hidden)continue;const scale=Math.min(1,h/Math.max(1,panel.scrollHeight));panel.style.setProperty('--panel-scale',String(scale));}
}
function schedule(){if(!queued){queued=true;requestAnimationFrame(fit)}}
function viewport(){document.documentElement.style.setProperty('--app-height',`${window.visualViewport?.height||innerHeight}px`);schedule()}
new ResizeObserver(schedule).observe(frame);
for(const panel of panels)new ResizeObserver(schedule).observe(panel);
new MutationObserver(schedule).observe(root,{subtree:true,attributes:true,attributeFilter:['hidden','class'],childList:true,characterData:true});
window.visualViewport?.addEventListener('resize',viewport);window.addEventListener('resize',viewport);document.fonts.ready.then(schedule);viewport();
