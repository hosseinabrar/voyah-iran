const fs=require('fs'),path=require('path'),assert=require('assert');
(async()=>{
 const root=path.resolve(__dirname,'..');
 const {portBadges}=await import('data:text/javascript;base64,'+fs.readFileSync(path.join(root,'port-badges.js')).toString('base64'));
 assert.deepEqual(portBadges({}),[]);
 assert.deepEqual(portBadges({chargeFlap:false,chargeConnected:false,charging:false}),[]);
 for(const [v,expected] of [
 [{chargeFlap:true},'charge-red'],
 [{chargeFlap:true,chargeConnected:true},'charge-yellow'],
 [{chargeFlap:false,chargeConnected:true},'charge-yellow'],
 [{chargeFlap:true,chargeConnected:true,charging:true},'charge-green'],
 [{charging:true},'charge-green'],
 [{chargeComplete:true},'charge-green'],
 [{chargeWaiting:true},'charge-yellow'],
 [{fuelFlap:true},'fuel-red']]){
  assert.equal(portBadges(v)[0].asset,expected);
  assert(fs.existsSync(path.join(root,'assets',expected+'.png')));
 }
 assert.equal(portBadges({charging:true})[0].blink,true);
 assert.equal(portBadges({chargeComplete:true})[0].blink,undefined);
 assert.equal(portBadges({chargeComplete:true,chargeConnected:false}).length,0);
 console.log('PASS: port visibility and charging/connector/flap precedence');
})();
