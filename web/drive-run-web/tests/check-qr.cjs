'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const elements = new Map();
function element(key) {
  if (!elements.has(key)) elements.set(key, {textContent:'', hidden:false, dataset:{}, children:[], setAttribute(){}});
  return elements.get(key);
}
let mapped, initialized = 0;
const context = vm.createContext({window:{}, TextDecoder, Uint8Array, atob, URLSearchParams,
  document:{querySelector:element}, location:{search:''}});
context.window.driveRouteMap = {updateTrip(trip){mapped=trip}, init(){initialized++}};
for (const file of ['data.js','qr-data.js']) vm.runInContext(fs.readFileSync(path.join(root,file),'utf8'),context);
vm.runInContext(fs.readFileSync(path.join(root,'app.js'),'utf8').split('// 서버가 전달한 순서')[0],context);
const qr = context.window.DRIVE_RUN_QR;
const payload = {v:1,id:'vehicle-1',course:'수원 하트 🚗',region:'수원',start:'2026-11-05T10:12:00+09:00',end:'2026-11-05T11:36:10+09:00',km:42.3,sec:5050,eff:6.5,unit:'kwh',pl:'_p~iF~ps|U_ulLnnqC_mqNvxq`@'};
const encode = p => Buffer.from(JSON.stringify(p)).toString('base64url');
assert.deepEqual(JSON.parse(JSON.stringify(qr.decode(encode(payload),payload.id).path)),[[-120.2,38.5],[-120.95,40.7],[-126.453,43.252]]);
let checks=1;
for (const change of [{v:2},{id:3},{km:-1},{sec:'5'},{eff:null},{unit:'gas'},{start:'bad'},{end:'2020-01-01'},{pl:'_'},{pl:'?'}]) {
  assert.throws(()=>qr.decode(encode({...payload,...change}),payload.id)); checks++;
}
for (const bad of ['', '!', 'a', Buffer.from([255]).toString('base64url'), Buffer.from('null').toString('base64url')]) {
  assert.throws(()=>qr.decode(bad,null));checks++;
}
assert.throws(()=>qr.decode(encode(payload),'wrong'));checks++;
async function load(query) {context.location.search=query; await vm.runInContext('loadTripFromUrl()',context);}
(async()=>{
 await load('?tripId=vehicle-1&d='+encode(payload));
 assert.equal(element('#result-view').dataset.tripState,'ready');
 assert.equal(element('#course-name').textContent,payload.course);
 assert.equal(element('#distance').textContent,'42.3');
 assert.equal(element('#duration').textContent,'1:24:10');
 assert.equal(element('#fuel-unit').textContent,'km/kWh');
 assert.match(element('#trip-times').textContent,/시작 .*종료 /);
 assert.equal(mapped.pathSource,'vehicle_qr');
 assert.equal(element('.demo-badge').textContent,'VEHICLE QR');checks++;
 await load('?d='+encode({...payload,unit:'l'}));assert.equal(element('#fuel-unit').textContent,'km/L');checks++;
 const optional={...payload};delete optional.eff;delete optional.unit;
 await load('?d='+encode(optional));assert.equal(element('#fuel').textContent,'—');assert.equal(element('#fuel-unit').textContent,'—');checks++;
 for (const query of ['?tripId=abc123&d=', '?tripId=abc123&d=!', '?d='+encode(payload)+'&d='+encode(payload), '?tripId=wrong&d='+encode(payload)]) {
  const before=initialized;await load(query);assert.equal(element('#result-view').dataset.tripState,'error');assert.equal(initialized,before);checks++;
 }
 await load('?tripId=abc123');assert.equal(mapped.tripId,'abc123');assert.equal(mapped.isDemo,true);assert.equal(element('.demo-badge').textContent,'DEMO / 01');checks++;
 await load('');assert.equal(mapped.tripId,'abc123');checks++;
 console.log('PASS: '+checks+' QR decoding and result integration scenarios');
})().catch(error=>{console.error(error);process.exitCode=1});
