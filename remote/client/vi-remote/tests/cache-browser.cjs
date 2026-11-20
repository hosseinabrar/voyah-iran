const {chromium}=require('playwright'),http=require('http'),fs=require('fs'),path=require('path'),assert=require('assert');
(async()=>{
 const root=path.resolve(__dirname,'..'),hits=[];
 const server=http.createServer((req,res)=>{const name=new URL(req.url,'http://localhost').pathname;hits.push(name);const file=path.join(root,name==='/'?'index.html':name);try{const body=fs.readFileSync(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html','.png':'image/png'})[path.extname(file)]||'application/octet-stream');res.end(body)}catch{res.statusCode=404;res.end()}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const url=`http://127.0.0.1:${server.address().port}`;
 const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
 try{
 const context=await browser.newContext(),page=await context.newPage();await page.goto(url);
 await page.evaluate(async()=>{await navigator.serviceWorker.ready;if(!navigator.serviceWorker.controller)await new Promise(r=>navigator.serviceWorker.addEventListener('controllerchange',r,{once:true}))});
 const before=hits.filter(x=>x.startsWith('/assets/')).length;
 await page.reload();await page.evaluate(async()=>{const urls=await fetch('precache.js').then(r=>r.text());const match=urls.match(/self.VI_ASSETS=(.*);/);await Promise.all(Object.keys(JSON.parse(match[1])).filter(x=>x.startsWith('assets/')).map(x=>fetch(x).then(r=>{if(!r.ok)throw Error(x)})))});
 assert.equal(hits.filter(x=>x.startsWith('/assets/')).length,before,'reloaded assets downloaded again');
 await context.setOffline(true);await page.reload();assert.equal(await page.locator('body').count(),1);
 assert(await page.evaluate(async()=>{const r=await fetch('assets/car-puzzle/hood_open.png');return r.ok&&(await r.blob()).size>0}));
 assert(await page.evaluate(async()=>{const names=await caches.keys();for(const n of names)for(const r of await (await caches.open(n)).keys())if(new URL(r.url).pathname.startsWith('/v2/'))return false;return true}));
 console.log('PASS all app assets cached, reload causes zero asset downloads, offline launch and puzzle load, no API data cached');
 }finally{await browser.close();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exit(1)});
