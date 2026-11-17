export function updatedTime(at,now=Date.now()){
 if(!Number.isFinite(at)||at<=0)return 'هنوز دریافت نشده';
 const seconds=Math.max(0,Math.floor((now-at)/1000));
 if(seconds<10)return 'آنلاین';
 if(seconds<60)return `${seconds} ثانیه پیش`;
 if(seconds<3600)return `${Math.floor(seconds/60)} دقیقه پیش`;
 return new Date(at).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
}
