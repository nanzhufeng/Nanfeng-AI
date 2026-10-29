import test from 'node:test';
import assert from 'node:assert/strict';
import {installModalLayerOwner,installComposerTransientLayerOwner} from '../src/modal-layer-owner.mjs';

function harness(kind){
 const listeners={};let closed=0;const input={getClientRects:()=>[{}],closest:()=>null,tabIndex:0,focus(){doc.activeElement=this}};
 const doc={activeElement:input,addEventListener(n,f){(listeners[n]??=[]).push(f)}};
 let layer={contains:n=>n===input,querySelectorAll:()=>[input],querySelector:()=>({click(){closed++}}),matches:()=>false,getAttribute:()=>null};
 const root={ownerDocument:doc,children:[],querySelectorAll:s=>s.includes('.scrim')?[layer]:[],querySelector:s=>s.includes('.scrim')?(kind==='modal'?layer:null):layer};
 const old=globalThis.MutationObserver;globalThis.MutationObserver=class{observe(){}disconnect(){}};
 try{if(kind==='modal')installModalLayerOwner(root);else installComposerTransientLayerOwner(root,()=>closed++);}finally{globalThis.MutationObserver=old;}
 const fire=(type,extra={})=>{const e={target:input,key:'Escape',timeStamp:1000,prevented:false,stopped:false,preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true},...extra};for(const fn of listeners[type]??[])fn(e);return e};
 return{fire,get closed(){return closed},replace(){layer={...layer}},input};
}
for(const kind of ['modal','composer']){
 test(`${kind}: candidate Escape does not dismiss or cancel native editing`,()=>{const h=harness(kind),e=h.fire('keydown',{isComposing:true});assert.equal(h.closed,0);assert.equal(e.prevented,false);assert.equal(e.stopped,true)});
 test(`${kind}: legacy 229 Escape does not dismiss`,()=>{const h=harness(kind);h.fire('keydown',{keyCode:229});assert.equal(h.closed,0)});
 test(`${kind}: tracked composition protects browsers omitting isComposing`,()=>{const h=harness(kind);h.fire('compositionstart');h.fire('keydown');assert.equal(h.closed,0)});
 test(`${kind}: same-input IME tail protected then explicit Escape closes`,()=>{const h=harness(kind);h.fire('compositionstart');h.fire('compositionend',{timeStamp:2000});for(const key of ['Enter','Escape']){const e=h.fire('keydown',{key,timeStamp:2099});assert.equal(h.closed,0);assert.equal(e.prevented,false);assert.equal(e.stopped,true)}h.fire('keydown',{timeStamp:2100});assert.equal(h.closed,1)});
 test(`${kind}: other input and new layer do not inherit stale protection`,()=>{const h=harness(kind);h.fire('compositionstart');h.fire('compositionend');h.fire('keydown',{target:{},timeStamp:1050});assert.equal(h.closed,1);const j=harness(kind);j.fire('compositionstart');j.replace();j.fire('keydown');assert.equal(j.closed,1)});
 test(`${kind}: unrelated earlier timestamp is not an IME tail`,()=>{const h=harness(kind);h.fire('compositionend',{timeStamp:2000});h.fire('keydown',{timeStamp:1999});assert.equal(h.closed,1)});
}
