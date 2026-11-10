'use strict';
// Capture through the browser debugging protocol so Node, not Chrome, writes the PNG.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn}=require('node:child_process');
const [browser,url,output,widthArg='840',heightArg='900']=process.argv.slice(2);
const width=Number(widthArg),height=Number(heightArg);
if(!Number.isInteger(width)||!Number.isInteger(height)||width<100||width>4000||height<100||height>4000)throw new Error('Invalid capture dimensions');
if(!browser||!url||!output)throw new Error('Usage: node capture-design.cjs <browser> <page-url> <output-png>');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'drive-run-design-'));
const child=spawn(browser,['--headless','--disable-gpu','--no-sandbox','--allow-file-access-from-files','--no-first-run','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{stdio:['ignore','ignore','pipe'],windowsHide:true});
let ws;
(async()=>{
  const endpoint=await new Promise((resolve,reject)=>{
    let logs='';const timer=setTimeout(()=>reject(new Error('Browser debugging endpoint unavailable: '+logs.slice(-500))),10000);
    child.stderr.on('data',chunk=>{logs+=chunk;const match=logs.match(/DevTools listening on (ws:\/\/\S+)/);if(match){clearTimeout(timer);resolve(match[1])}});
    child.on('error',error=>{clearTimeout(timer);reject(error)});
  });
  ws=new WebSocket(endpoint);
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject});
  let nextId=0;const pending=new Map();
  ws.onmessage=event=>{const message=JSON.parse(event.data);if(!message.id)return;const p=pending.get(message.id);if(!p)return;clearTimeout(p.timer);pending.delete(message.id);message.error?p.reject(new Error(message.error.message)):p.resolve(message.result)};
  function call(method,params={},sessionId){return new Promise((resolve,reject)=>{const id=++nextId;const timer=setTimeout(()=>{pending.delete(id);reject(new Error('CDP timeout: '+method))},15000);pending.set(id,{resolve,reject,timer});ws.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}))})}
  const {targetId}=await call('Target.createTarget',{url:'about:blank'});
  const {sessionId}=await call('Target.attachToTarget',{targetId,flatten:true});
  await call('Page.enable',{},sessionId);
  await call('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<=480},sessionId);
  await call('Page.navigate',{url},sessionId);
  await call('Runtime.evaluate',{expression:"new Promise(resolve=>{if(document.readyState==='complete')resolve();else window.addEventListener('load',resolve,{once:true});setTimeout(resolve,8000)})",awaitPromise:true},sessionId);
  await call('Runtime.evaluate',{expression:"Promise.race([document.fonts.ready,new Promise(r=>setTimeout(r,2000))])",awaitPromise:true},sessionId);
  await call('Runtime.evaluate',{expression:"new Promise(resolve=>{const deadline=Date.now()+10000;function check(){const status=document.querySelector('#map-status');if(!status||!status.textContent.includes('불러오는 중')||Date.now()>deadline)resolve();else setTimeout(check,200)}check()})",awaitPromise:true},sessionId);
  const state=await call('Runtime.evaluate',{expression:"JSON.stringify({tripState:document.querySelector('#result-view')?.dataset.tripState,mapStatus:document.querySelector('#map-status')?.textContent,scrollWidth:document.documentElement.scrollWidth,innerWidth,scrollHeight:document.documentElement.scrollHeight,innerHeight})"},sessionId);
  console.log('Rendered page: '+state.result.value);
  const screenshot=await call('Page.captureScreenshot',{format:'png'},sessionId);
  fs.writeFileSync(output,Buffer.from(screenshot.data,'base64'));
  console.log('Saved UI review: '+output);
  await call('Browser.close').catch(()=>{});
})().catch(error=>{console.error(error.message);process.exitCode=1}).finally(async()=>{
  if(ws)ws.close();
  child.kill();
  await new Promise(resolve=>setTimeout(resolve,500));
  if(path.dirname(path.resolve(profile))!==path.resolve(os.tmpdir())||!path.basename(profile).startsWith('drive-run-design-'))throw new Error('Unexpected profile path');
  try{fs.rmSync(profile,{recursive:true,force:true,maxRetries:3,retryDelay:100})}catch(error){console.error('Temporary browser profile cleanup: '+error.message)}
});
