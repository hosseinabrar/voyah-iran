const assert=require('assert/strict'),path=require('path');
const {chromium}=require('playwright');
(async()=>{
const browser=await chromium.launch({channel:'chrome',headless:true});
try {for(const theme of ['light','dark']) {
 const page=await browser.newPage({viewport:{width:1920,height:720}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.commands=[];window.Passenger={send:(kind,value)=>commands.push({kind,value})};});
 await page.goto('file://'+path.resolve(__dirname,'../../app/src/main/assets/passenger',theme+'.html'),{waitUntil:'domcontentloaded'});
 await page.waitForTimeout(250);
 assert.equal(await page.locator('.panel').count(),6);
 assert.equal(await page.locator('.seat button:enabled').count(),0,'unknown CAN state cannot issue commands');
 await page.evaluate(()=>{
  const s={fresh:true,FCM_SW_REQ_FB:2,IVI_FRAG_TASTE_FB:2,IVI_FRAG_CONCERNTION_FB:2,FCM_DURATION_CONTROL:1,
   getAirLeftTemperature:220,getAirRightTemperature:230,getAirWindSpeed:3,getAirSWStatus:1,getAirACStatus:1,getAirMode:1,getAirDUALStatus:0,getAirFrontWindowDefogger:0,getAirRearWindowDefogger:0,getAirCirculationMode:0,getAirSupplyStatus:0};
  for(const side of ['LEFT','RIGHT'])for(const key of ['HEATING_COMMAND','VENTILATION_COMMAND','MASS_INTEN','MASS_COMMAND'])s['FRONT_SEAT_'+key+'_'+side]=key==='MASS_COMMAND'?1:side==='RIGHT'?2:0;
  passenger.update('vehicle',s);
  const items=Array.from({length:8},(_,i)=>({id:'app'+i,label:'App '+i,icon:'music/launcher_next.png'}));
  passenger.update('apps',{items,favorites:items.slice(0,5).map(i=>i.id)});
  passenger.update('weather',{city:'آران و بیدگل',temperature:20,icon:'weather/launcher_weather_moon.png',hours:Array.from({length:5},(_,i)=>({time:Date.now()+(i+1)*3600000,temperature:20-i,icon:'weather/launcher_weather_moon.png'}))});
  passenger.update('media',{title:'Tamasha',artist:'Shadmehr Aghili',position:72000,duration:180000,playing:true,seek:true,cover:'music/launcher_next.png'});
  passenger.update('volume',{value:11});
 });
 await page.waitForTimeout(400);
 assert.equal(await page.evaluate(()=>commands.length),0,'painting confirmed state must not issue commands');
 assert.equal(await page.locator('.forecast-hour').count(),5);
 assert.equal(await page.locator('.weather-city').evaluate(e=>getComputedStyle(e).textOverflow),'ellipsis');
 await page.locator('[data-scent="2"]').click();
 assert.deepEqual(await page.evaluate(()=>JSON.parse(commands.at(-1).value)),{op:'scent',value:'0'});
 await page.locator('[data-owner="passenger"] [data-feature="massage"]').click();
 assert.deepEqual(await page.evaluate(()=>JSON.parse(commands.at(-1).value)),{op:'passenger-massage',value:'3'});
 await page.locator('.app-grid .app-tile').first().dispatchEvent('pointerdown',{clientX:900,clientY:170,pointerId:1});await page.waitForTimeout(600);
 assert.equal(await page.locator('.remove-favorite').count(),5);
 await page.locator('.remove-favorite').first().click({force:true});
 assert.equal(await page.locator('.add-symbol').count(),1);
 await page.locator('.add-symbol').click({force:true});
 assert.equal(await page.locator('.picker-list button').count(),4,'picker excludes existing shortcuts');
 await page.locator('.picker-list button').first().click();
 assert.equal(await page.locator('.add-symbol').count(),0);
 await page.locator('.weather-card').click();assert.equal(await page.locator('.remove-favorite').count(),0);
 await page.locator('.all-slot').click();assert.equal(await page.evaluate(()=>commands.at(-1).kind),'allApps');
 await page.evaluate(()=>passenger.update('vehicle',{fresh:false}));assert.equal(await page.locator('.seat button:enabled').count(),0);
 assert.deepEqual(errors,[]);
 console.log('PASS '+theme+': initial fail-closed, feedback without writes, 5 hourly slots, city ellipsis, scent toggle, seat action, shortcut editing/filter/dismiss, all apps, stale CAN');
 await page.close();
}}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
