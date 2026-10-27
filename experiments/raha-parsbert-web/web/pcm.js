// Resample microphone input to 16 kHz PCM16. No art/frames or audio files.
class PCM extends AudioWorkletProcessor {
  constructor() {super(); this.sum=0; this.count=0; this.phase=0; this.buffer=[]; this.stopped=true; this.port.onmessage=e=>{if(e.data==='start'){this.buffer=[];this.sum=this.count=this.phase=0;this.stopped=false;this.port.postMessage('started');}else if(e.data==='flush'){this.stopped=true;this.flush();this.port.postMessage('flushed');}};}
  flush() {if(this.buffer.length){const pcm=new Int16Array(this.buffer);this.buffer=[];this.port.postMessage(pcm.buffer,[pcm.buffer]);}}
  process(inputs) {
    const samples=inputs[0]?.[0];
    if (!samples || this.stopped) return true;
    const ratio=sampleRate/16000;
    for(const value of samples) {
      this.sum+=value; this.count++; this.phase++;
      if(this.phase>=ratio) {
        this.buffer.push(Math.round(Math.max(-1,Math.min(1,this.sum/this.count))*32767));
        this.phase-=ratio; this.sum=0; this.count=0;
      }
    }
    if(this.buffer.length>=800) {
      this.flush();
    }
    return true;
  }
}
registerProcessor('pcm',PCM);
