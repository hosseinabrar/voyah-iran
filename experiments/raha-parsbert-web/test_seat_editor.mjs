import assert from 'node:assert/strict';
import fs from 'node:fs';
const nodes={};
class Element {
 constructor(){this.children=[];this.dataset={};this.value='';}
 set id(v){nodes[v]=this;}
 get options(){return this.children;}
 append(e){this.children.push(e);if(this.children.length===1)this.value=e.value;}
 replaceChildren(){this.children=[];this.value='';}
 before(){} insertBefore(e){this.append(e);} setAttribute(){}
}
for(const id of ['expected','save','correction']){const e=new Element();e.id=id;}
const actions=['SET_SEAT_HEATING','SET_SEAT_VENTILATION','SET_SEAT_MASSAGE'];
for(const value of actions){const e=new Element();e.value=value;e.textContent=value;nodes.expected.append(e);}
globalThis.document={getElementById:id=>nodes[id],createElement:()=>new Element()};
globalThis.fetch=async()=>({ok:true,json:async()=>Object.fromEntries(actions.map(a=>[a,[
 {enabled:[false,true],level:['max','min',0,1,2,3],seat:['driver','passenger']},
 {enabled:[true],seat:['driver','passenger']}
]]))});
const url=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
const read=name=>fs.readFileSync(new URL('./web/'+name,import.meta.url),'utf8');
const editor=await import(url(read('feedback-editor.js').replace('./parameters.js',url(read('parameters.js')))));
await editor.initializeEditor();
for(const action of actions){
 editor.showEditor(action,{enabled:true,seat:'passenger'});
 assert.deepEqual(nodes['parameter-enabled'].options.map(o=>o.value),['true','false']);
 nodes['parameter-enabled'].value='false';nodes['parameter-enabled'].onchange();
 assert.deepEqual(editor.readParameters(),{enabled:false,level:0,seat:'passenger'});
 assert.equal(nodes['parameter-level'].disabled,true);
 nodes['parameter-enabled'].value='true';nodes['parameter-enabled'].onchange();
 assert.deepEqual(editor.readParameters(),{enabled:true,seat:'passenger'});
}
console.log('Seat editor on/off round-trip passed for all three controls.');
