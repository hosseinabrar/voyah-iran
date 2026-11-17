export function chargeStatus(v={}) {
 if(v.charging===true)return {label:'در حال شارژ',color:'green',blink:true};
 if(v.chargeComplete===true&&v.chargeConnected!==false)return {label:'شارژ کامل',color:'green',complete:true};
 if(v.chargeWaiting===true)return {label:'شارژ زمانبندی شده',color:'yellow',waiting:true};
 if(v.chargeConnected===true)return {label:'گان متصل',color:'yellow'};
 if(v.chargeFlap===true)return {label:'درپوش شارژ باز',color:'red'};
 return null;
}
export function portBadges(v={}) {
 const badges=[],s=chargeStatus(v);
 if(s)badges.push({...s,port:'charge',asset:'charge-'+s.color,x:-91});
 if(v.fuelFlap===true)badges.push({port:'fuel',asset:'fuel-red',x:63,color:'red',label:'درپوش باک باز'});
 return badges;
}
