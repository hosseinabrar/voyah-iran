importScripts('./precache.js');
const root=new URL('./',self.location.href);
const assets=new Map(Object.entries(self.VI_ASSETS).map(([path,revision])=>{
 const url=new URL(path,root);const key=new URL(url);key.searchParams.set('__vi_revision',revision);return [url.pathname,key.href];
}));
self.addEventListener('install',event=>event.waitUntil((async()=>{
 const cache=await caches.open(self.VI_CACHE);
 // Content revisions reuse unchanged artwork across app updates as well as launches.
 for(const [path,key] of assets){
  const existing=await caches.match(key);
  if(existing){await cache.put(key,existing);continue;}
  const response=await fetch(new Request(new URL(path,root),{cache:'no-cache'}));
  if(!response.ok)throw Error('App asset unavailable: '+path);
  const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await response.clone().arrayBuffer())),b=>b.toString(16).padStart(2,'0')).join('');
  if(digest!==new URL(key).searchParams.get('__vi_revision'))throw Error('App update incomplete: '+path);
  await cache.put(key,response);
 }
})()));
self.addEventListener('activate',event=>event.waitUntil((async()=>{
 for(const key of await caches.keys())if(key.startsWith('vi-remote-')&&key!==self.VI_CACHE)await caches.delete(key);
 await self.clients.claim();
})()));
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=='GET'||url.origin!==root.origin)return;
 const path=url.pathname===root.pathname?new URL('index.html',root).pathname:url.pathname;
 const key=assets.get(path);
 // No telemetry, SMS, camera responses, map tiles or arbitrary URLs in this cache.
 if(!key)return;
 event.respondWith((async()=>{
  const cache=await caches.open(self.VI_CACHE);const cached=await cache.match(key);
  if(cached)return cached;
  const response=await fetch(event.request);
  if(response.ok)await cache.put(key,response.clone());
  return response;
 })());
});
