'use strict';
// No ART/Java bridge, JNI calls, heap scanning or replacement of OEM functions.
console.log('VI_CAN_NATIVE_SCRIPT_LOADED');
(function () {
    const hooks = [];
    let queue = [], dropped = 0, seq = 0, timer = null, stopped = false;
    let rxBytes = 0, rxChunks = 0, txBytes = 0, lastBeat = Date.now(), marksSeen = 0;
    function flush(all) {
        if (dropped) { console.log('VI_CAN_BATCH ' + JSON.stringify([{kind:'DROPPED',count:dropped}])); dropped=0; }
        do {
            const batch=queue.splice(0,128);
            if(batch.length) console.log('VI_CAN_BATCH '+JSON.stringify(batch));
        } while(all && queue.length);
    }
    function record(kind, extra) {
        if(queue.length >= 2048) { dropped++; return; }
        queue.push(Object.assign({kind:kind,seq:++seq,device_ms:Date.now()},extra));
    }
    function detach(reason) {
        if(stopped) return;
        stopped=true;
        hooks.forEach(h=>h.detach());
        if(timer!==null) clearInterval(timer);
        record(reason,{rx_bytes:rxBytes,rx_chunks:rxChunks,tx_bytes:txBytes});
        flush(true);
        console.log('VI_CAN_DETACHED');
    }
    try {
        // QG UEvent is a separate native transport: libqg_runtime calls
        // libpateouevent.recvfrom before BundleFormat/VehicleState parsing.
        const transportNames=['libqg_canbus_jni.so','libpateouevent.so','libqg_runtime.so'];
        let transports=[];
        function refreshTransports() {
            transports=transportNames.flatMap(name=>{
                try {return [Process.getModuleByName(name)];} catch (_) {return [];}
            });
        }
        refreshTransports();
        if(!transports.length) throw Error('No supported native CAN/UEvent transport loaded');
        const libc=Process.getModuleByName('libc.so');
        function address(name) { return libc.getExportByName(name); }
        const access=new NativeFunction(address('access'),'int',['pointer','int']);
        const unlink=new NativeFunction(address('unlink'),'int',['pointer']);
        const open=new NativeFunction(address('open'),'int',['pointer','int']);
        const read=new NativeFunction(address('read'),'int',['int','pointer','uint']);
        const close=new NativeFunction(address('close'),'int',['int']);
        const paths={};
        ['heartbeat','stop','marks'].forEach(n=>paths[n]=Memory.allocUtf8String(VI_DIR+'/'+n));
        const markBuffer=Memory.alloc(65536);
        function readMarks() {
            const fd=open(paths.marks,0);
            if(fd<0) return;
            try {
                const n=read(fd,markBuffer,65535);
                if(n<=0) return;
                const lines=markBuffer.readUtf8String(n).split('\n');
                // A partial last line is left for the next poll.
                for(;marksSeen<lines.length-1;marksSeen++) {
                    const line=lines[marksSeen];
                    if(/^[a-f0-9]{32}\|[0-9]+$/.test(line)) {
                        const parts=line.split('|');
                        record('AUTOPARK_MANUAL_MARK',{marker_id:parts[0],host_click_ms:parts[1],meaning:'Manual operator mark, not a hardware button event'});
                    }
                }
            } finally { close(fd); }
        }
        function callerTransport(address) {
            return transports.find(m=>address.compare(m.base)>=0 && address.compare(m.base.add(m.size))<0);
        }
        function capture(direction, operation, fd, buffer, n, requested, transport) {
            if(n<=0 || stopped) return;
            if(direction==='RX') {rxBytes+=n;rxChunks++;} else txBytes+=n;
            if(queue.length>=2048) {dropped++;return;}
            const length=Math.min(n,requested,8192);
            const bytes=new Uint8Array(buffer.readByteArray(length));
            let hex='';
            for(let i=0;i<bytes.length;i++) hex+=('0'+bytes[i].toString(16)).slice(-2);
            record(direction+'_TRANSPORT',{operation:operation,source_module:transport.path,fd:fd,length:n,captured:length,truncated:length!==n,hex:hex});
        }
        let receiveHooks=0;
        ['read','recv','recvfrom','write','send','sendto'].forEach(function(name) {
            let addr;
            try {addr=address(name);} catch (_) {return;}
            const direction=['read','recv','recvfrom'].includes(name)?'RX':'TX';
            hooks.push(Interceptor.attach(addr,{
                onEnter:function(args) {
                    this.capture=callerTransport(this.returnAddress);
                    if(this.capture) {this.fd=args[0].toInt32();this.buffer=args[1];this.requested=args[2].toUInt32();}
                },
                onLeave:function(result) {
                    if(!this.capture) return;
                    try {capture(direction,name,this.fd,this.buffer,result.toInt32(),this.requested,this.capture);} catch (_) {record('CAPTURE_ERROR',{operation:name});}
                }
            }));
            if(direction==='RX') receiveHooks++;
        });
        if(receiveHooks===0) throw Error('No receive hooks available');
        record('START',{mode:'native-transport-v2',arch:Process.arch,pointer_size:Process.pointerSize,modules:transports.map(m=>m.path),
            scope:'Successful read/recv/recvfrom/write/send/sendto from CAN JNI and QG runtime/Pateo UEvent libraries, before Java parsing. Transport chunks, not guaranteed complete physical CAN frames; ioctl and traffic not delivered to these libraries are not covered.'});
        flush(true);
        console.log('VI_CAN_READY');
        let lastStatus=Date.now();
        timer=setInterval(function() {
            try {
                readMarks();
                if(access(paths.heartbeat,0)===0) {unlink(paths.heartbeat);lastBeat=Date.now();}
                if(access(paths.stop,0)===0) {detach('STOP_DETACHED');return;}
                if(Date.now()-lastBeat>30000) {detach('WATCHDOG_DETACHED');return;}
                if(Date.now()-lastStatus>=5000) {
                    refreshTransports();
                    record('CAPTURE_STATUS',{rx_bytes:rxBytes,rx_chunks:rxChunks,tx_bytes:txBytes,modules:transports.map(m=>m.path)});lastStatus=Date.now();
                }
                flush(false);
            } catch(e) {record('CAPTURE_ERROR',{message:String(e)});}
        },100);
    } catch(e) {
        detach('START_FAILED');
        console.log('VI_CAN_FAILED: '+String(e));
    }
})();
