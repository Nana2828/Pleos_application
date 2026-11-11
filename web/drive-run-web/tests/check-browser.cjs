'use strict';
// Framework-free UI checks. MapLibre is replaced with an API test double; no real tiles are verified.
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const browser = [process.env.DRIVE_RUN_BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(file => file && fs.existsSync(file));
if (!browser) { console.log('SKIP: Chrome/Edge not found; set DRIVE_RUN_BROWSER to an installed browser.'); process.exit(0); }
const scratch = fs.mkdtempSync(path.join(root, '.qa-stability-'));
const base = pathToFileURL(root + path.sep).href;
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'styles.css'), 'utf8');
const course = JSON.parse(fs.readFileSync(path.join(root, 'data/courses/bucheon-cat-91min.json'), 'utf8').replace(/^\uFEFF/, ''));
const courseFetch = `<script>window.qaCourse=${JSON.stringify(course).replaceAll('<', '\\u003c')};const originalCourseFetch=window.fetch.bind(window);window.fetch=async(input,options)=>{if(new URL(input,location.href).pathname.endsWith('/data/courses/bucheon-cat-91min.json')){if(location.search.includes('courseFetchFailure=1'))return {ok:false};if(location.search.includes('courseInvalid=1'))return {ok:true,json:async()=>({...window.qaCourse,routePreview:[[126,37],[127,38]]})};return {ok:true,json:async()=>window.qaCourse}}return originalCourseFetch(input,options)};</script>`;
const mapDouble = `<style id="maplibre-css">.maplibregl-ctrl-top-right{position:absolute;top:5px;right:5px}.maplibregl-ctrl button{display:block}</style><script>
window.qaErrors=[];window.addEventListener('error',e=>window.qaErrors.push(e.message));
window.maplibregl={LngLatBounds:class{constructor(){this.points=[]}extend(p){this.points.push(p);return this}},NavigationControl:class{},Map:class{
 constructor(options){this.options=options;this.events={};this.sources={};this.layers=[];this.touchZoomRotate={disableRotation(){}};window.qaMap=this;this.canvas=document.createElement('canvas');this.canvas.style.cssText='position:absolute;width:100%;height:100%';document.getElementById(options.container).append(this.canvas);setTimeout(()=>{this.events['style.load']?.();this.events.idle?.()},10)}
 on(name,fn){this.events[name]=fn}getCanvas(){return this.canvas}addControl(){const controls=document.createElement('div');controls.className='maplibregl-ctrl-top-right maplibregl-ctrl';for(const label of ['확대','축소']){const b=document.createElement('button');b.textContent=label;b.setAttribute('aria-label',label);controls.append(b)}document.getElementById('route-map').append(controls)}
 getSource(id){return this.sources[id]}addSource(id,source){this.sources[id]={...source,setData(data){this.data=data}}}addLayer(layer){this.layer=layer;this.layers.push(layer)}fitBounds(bounds,options){this.bounds=bounds;this.fitOptions=options}resize(){this.resized=true}areTilesLoaded(){return true}remove(){document.getElementById('route-map').replaceChildren()}
}};
</script>`;
const failedAssets = `<script>window.qaErrors=[];const appendAsset=document.head.append.bind(document.head);document.head.append=(element)=>{if(element.src?.includes('unpkg.com')||element.href?.includes('unpkg.com'))setTimeout(()=>element.onerror?.(),0);else appendAsset(element)};</script>`;
const cases = [];
for (const [width,height] of [[320,568],[375,667],[390,844],[430,932],[480,800],[1280,800]]) {
  for (const id of ['abc123','def456','ghi789','bucheon-cat-91min']) cases.push({width,height,id,query:'?tripId='+id});
  cases.push({width,height,id:'abc123',query:'?tripId=abc123',safe:34,top:44});
  if(width<=480) cases.push({width,height,id:'bucheon-cat-91min',query:'?tripId=bucheon-cat-91min',safe:34,top:44});
}
cases.push({width:390,height:844,id:'abc123',query:'',deep:true},
  {width:390,height:844,id:'def456',query:'?tripId=def456',reload:true},
  {width:390,height:844,id:'ghi789',query:'?tripId=ghi789#ranking',ranking:true},
  {width:390,height:844,id:'abc123',query:'?tripId=abc123',assetFailure:true},
  {width:375,height:667,id:'abc123',query:'?tripId=abc123',fontFallback:true},
  {width:390,height:844,id:'bucheon-cat-91min',query:'?tripId=bucheon-cat-91min',reload:true},
  {width:390,height:844,id:'bucheon-cat-91min',query:'?tripId=bucheon-cat-91min',assetFailure:true},
  {width:390,height:844,query:'?tripId=bucheon-cat-91min&courseFetchFailure=1',error:true},
  {width:390,height:844,query:'?tripId=bucheon-cat-91min&courseInvalid=1',error:true});
for (const query of ['?tripId=missing','?tripId=','?tripId=abc123&tripId=def456','?tripId=%3Cscript%3E','?tripId=constructor','?tripId=__proto__','?tripId='+('a'.repeat(65))]) cases.push({width:320,height:568,query,error:true});
cases.forEach((c,i) => {
  c.file = 'frame-'+i+'.html';
  let sheet = css.replace('env(safe-area-inset-bottom, 0px)', (c.safe||0)+'px').replace('env(safe-area-inset-top, 0px)', (c.top||0)+'px');
  // Explicitly exercise the system-font fallback without a network request.
  if(c.fontFallback) sheet = sheet.replace(/@font-face\s*\{[\s\S]*?\}/, '').replace('font-family: Pretendard,', 'font-family:');
  fs.writeFileSync(path.join(scratch,c.file), html
    .replace('<link rel="stylesheet" href="styles.css" />','<style>'+sheet+'</style>')
    .replace('<script src="course-data.js"></script>',courseFetch+'<script src="course-data.js"></script>')
    .replace('<script src="map.js"></script>',(c.assetFailure?failedAssets:mapDouble)+'<script src="map.js"></script>')
    .replace(/src="(data|course-data|map|qr-data|app)\.js"/g, (_, name) => 'src="'+base+name+'.js"'));
});
const script = `const cases=${JSON.stringify(cases)};
Promise.all(cases.map(c=>new Promise(resolve=>{
 const f=document.createElement('iframe');f.style.cssText='border:0;width:'+c.width+'px;height:'+c.height+'px';f.src=c.file+c.query;let loads=0;
 f.onload=()=>{if(c.reload&&loads++===0){f.contentWindow.location.reload();return}setTimeout(async()=>{
  const w=f.contentWindow,d=w.document,fail=[];const check=(ok,msg)=>{if(!ok)fail.push(msg)};
  const wait=()=>new Promise(r=>setTimeout(r,20));
  const clone=()=>JSON.parse(JSON.stringify(w.DRIVE_RUN_MOCK.ranking));
  const expectedTrip=()=>c.id==='bucheon-cat-91min'?w.DRIVE_RUN_COURSES.normalize(w.qaCourse,'부천·인천'):w.DRIVE_RUN_TRIPS[c.id];
  try{
   const view=d.querySelector('#result-view');
   check(view.dataset.tripState===(c.error?'error':'ready'),'lookup state');
   if(c.error){check(!d.querySelector('#trip-message').hidden,'error message');check([...view.children].filter(e=>e.id!=='trip-message').every(e=>e.hidden),'fallback record exposed');check(!w.qaMap,'map created for invalid ID')}
   else{
    const trip=expectedTrip(),planned=trip.routeType==='planned';
    check(d.querySelector('#distance').textContent===(planned?trip.plannedDistanceKm:trip.distanceKm).toFixed(1),'distance');
    check(d.querySelector('#fuel').textContent===(planned?'—':trip.avgFuelEconomy.toFixed(1)),'fuel');
    check(d.querySelector('#duration').textContent===(planned?'91분':w.durationText(trip.durationSec)),'duration');
    if(planned){check(d.querySelector('#distance-kind').textContent==='계획 거리'&&d.querySelector('#duration-label').textContent==='예상시간','plan labels');check(d.querySelector('#region').textContent==='부천·인천','course region');check(d.querySelector('#result-title').textContent==='PLANNED ROUTE'&&!d.querySelector('#drive-date').textContent.includes('2026'),'no fabricated drive date')}
    check(d.querySelector('#drive-date').textContent.includes(c.id),'trip id');
    check(d.querySelector('#course-name').textContent===trip.courseName,'course name');
    check(d.querySelector('#route-notice').textContent.includes(planned?'18개 경유지':'별도 샘플'),'route type notice');
    check(d.querySelector('#ranking-view').hidden!==!!c.ranking,'direct hash');
    if(c.assetFailure)check(!d.querySelector('#retry-map').hidden&&d.querySelector('#map-status').classList.contains('map-error'),'asset failure');
    else{check(w.qaMap.sources['drive-route'].data.features[0].geometry.coordinates.length===trip.path.length,'route coordinates');if(!c.ranking)check(w.qaMap.bounds.points.length===trip.path.length+(trip.waypoints?.length||0),'fit bounds');if(planned){check(w.qaMap.sources['drive-waypoints'].data.features.length===18,'18 waypoint markers');check(JSON.stringify(w.qaMap.sources['drive-route'].data.features[0].geometry.coordinates)===JSON.stringify(trip.path),'all course coordinates');check(w.qaMap.sources['drive-waypoints'].data.features.every((f,i)=>f.properties.name===w.qaCourse.points[i].name&&f.properties.order===i+1),'waypoint names and order')};check(d.querySelectorAll('.maplibregl-ctrl button').length===2,'zoom control');check(w.qaMap.options.dragPan!==false&&w.qaMap.options.touchZoomRotate!==false,'navigation enabled');check(w.qaMap.layers.find(l=>l.id==='drive-route-line').paint['line-color']===w.getComputedStyle(d.documentElement).getPropertyValue('--primary').trim(),'lime map route');check(Number.parseFloat(w.getComputedStyle(d.querySelector('#distance')).fontSize)>2*Number.parseFloat(w.getComputedStyle(d.querySelector('#duration')).fontSize),'distance typography hierarchy')}
   }
   const search=w.location.search;
   for(const page of ['ranking','result']){d.querySelector('[data-page='+page+']').click();check(w.location.search===search&&!d.querySelector('#'+page+'-view').hidden,'navigation query preservation')}
   if(!c.error&&!c.assetFailure){w.driveRouteMap.resize();const trip=expectedTrip(),line=w.qaMap.layers.find(l=>l.id==='drive-route-line');check(w.qaMap.resized&&w.qaMap.bounds.points.length===trip.path.length+(trip.waypoints?.length||0),'resize and refit after page return');const casing=w.qaMap.layers.find(layer=>layer.id==='drive-route-casing');check(casing&&casing.paint['line-width']>line.paint['line-width']&&casing.paint['line-color']===w.getComputedStyle(d.documentElement).getPropertyValue('--map-route-casing').trim(),'dark route casing')}
   w.showPage('ranking');
   check(d.querySelector('.ranking-footnote').textContent.includes('고정 데모'),'fixed ranking notice');
   check(w.DRIVE_RUN_MOCK.ranking.currentUserId==='demo-user-me','QR changed ranking identity');
   const pageHeight=d.documentElement.scrollHeight,podium=d.querySelector('#overall-podium').innerHTML;
   for(let i=0;i<3;i++){w.selectCategory(i);check(d.querySelector('.rank-row.me .rank-number').textContent===(i===0?'2위':'3위'),'sample self rank');check(d.querySelector('#overall-podium').innerHTML===podium,'podium changed with tab')}
   for(const count of [3,20,50]){
    const group=clone();group.categories.forEach((cat,k)=>{const selfPosition=count-1-k;cat.entries=Array.from({length:count},(_,i)=>({userId:'friend-'+i,isMe:i===selfPosition,name:i===selfPosition?'별명 변경한 사용자':'아주 긴 닉네임을 사용하는 친구 '+i,value:'기록 '+i}))});
    w.renderRanking(group);
    for(let tab=0;tab<3;tab++){
     w.selectCategory(tab);const rows=[...d.querySelectorAll('.rank-row')];check(rows.length===count,'all '+count+' participants');check(rows.every((row,i)=>row.querySelector('.rank-number').textContent===(i+1)+'위'),'rank order');
     check(d.documentElement.scrollHeight===pageHeight,'friends increased page height');
     w.scrollTo(0,0);d.querySelector('#find-my-rank').click();const list=d.querySelector('#ranking-scroll'),r=list.getBoundingClientRect(),me=d.querySelector('.rank-row.me').getBoundingClientRect();
     check(me.top>=r.top-1&&me.bottom<=r.bottom+1,'find current category self');check(w.scrollY===0,'find scrolled page');
     if(count>=20)check(list.scrollHeight>list.clientHeight&&list.scrollTop>0,'list-only scroll');
    }
   }
   if(c.deep){
    check(!w.isMyEntry({name:'나'}),'nickname-only identity');check(!w.isMyEntry({name:'나',isMe:false,userId:'demo-user-me'}),'explicit false priority');check(w.isMyEntry({name:'다른 별명',isMe:true}),'explicit true');check(w.isMyEntry({name:'다른 별명',userId:'demo-user-me'}),'userId identity');check(!w.isMyEntry({userId:'abc123'}),'tripId used as userId');
    const missing=clone();missing.categories[0].entries=[null,{}, {name:'나',isMe:false},{name:'',isMe:true,value:null}];w.renderRanking(missing);w.selectCategory(0);check(d.querySelectorAll('.rank-row').length===4,'missing rows retained');check(d.querySelector('.rank-row.me .friend-value').textContent==='—','missing ranking value');check(d.querySelector('.friend-name').textContent==='이름 없음','missing name');check(!d.querySelector('#ranking-list').textContent.includes('undefined'),'undefined display');
    w.renderRanking(null);check(d.querySelector('#ranking-empty').hidden===false&&d.querySelector('#my-rank').textContent==='—','null ranking');w.renderRanking({categories:'bad',podium:'bad'});check(d.querySelectorAll('.ranking-tab').length===0,'invalid collections');w.renderRanking({categories:[null,{entries:[null]}],podium:[null]});w.selectCategory(1);check(d.querySelectorAll('.rank-row').length===1,'null entries');
    const escaped=clone();escaped.categories[0].entries[0].name='<img src=x onerror=alert(1)>';w.renderRanking(escaped);w.selectCategory(0);check(!d.querySelector('.friend-name img'),'unsafe HTML insertion');
    w.renderTrip({tripId:'empty',distanceKm:NaN,durationSec:-1,avgFuelEconomy:Infinity});check(['distance','duration','fuel'].every(id=>d.getElementById(id).textContent==='—'),'missing numeric display');check(w.durationText(10.9)==='0:00:10'&&w.durationText(null)==='—','duration normalization');check(w.shortDate(null)==='—','missing date');
    const trip=w.DRIVE_RUN_TRIPS.abc123;w.driveRouteMap.updateTrip({...trip,path:new Array(2)});check(w.qaMap.sources['drive-route'].data.features.length===0,'sparse path');w.driveRouteMap.updateTrip({...trip,path:[[null,37],[127,37]]});check(w.qaMap.sources['drive-route'].data.features.length===0,'invalid coordinates');
    w.driveRouteMap.updateTrip({...trip,routeType:'planned'});check(d.querySelector('#route-notice').textContent.includes('계획'),'planned route');w.driveRouteMap.updateTrip({...trip,isDemo:false,routeType:'recorded'});check(d.querySelector('#route-notice').textContent.includes('이동'),'recorded route');w.driveRouteMap.updateTrip({...trip,isDemo:false,routeType:undefined});check(d.querySelector('#route-notice').textContent.includes('미확인'),'unknown route');w.driveRouteMap.updateTrip({...trip,routeType:'constructor'});check(d.querySelector('#route-notice').textContent.includes('미확인'),'unrecognized route key');
    const oldMap=w.qaMap;oldMap.getCanvas().dispatchEvent(new w.Event('webglcontextlost'));oldMap.events.idle();check(!d.querySelector('#retry-map').hidden,'context loss hidden by idle');await w.driveRouteMap.init();await wait();oldMap.events.error();check(d.querySelector('#retry-map').hidden,'stale map event');w.dispatchEvent(new w.Event('offline'));w.qaMap.events.idle();check(!d.querySelector('#retry-map').hidden,'offline hidden by idle');await w.driveRouteMap.init();await wait();
    const provider=w.getTripById;let resolveOld,resolveNew;
    w.history.replaceState(null,'','?tripId=abc123');w.getTripById=id=>new Promise(resolve=>{if(id==='abc123')resolveOld=resolve;else resolveNew=resolve});const oldLoad=w.loadTripFromUrl();w.history.replaceState(null,'','?tripId=def456');const newLoad=w.loadTripFromUrl();resolveNew(w.DRIVE_RUN_TRIPS.def456);await newLoad;resolveOld(trip);await oldLoad;check(d.querySelector('#course-name').textContent===w.DRIVE_RUN_TRIPS.def456.courseName,'stale API response');
    w.getTripById=async()=>({...trip,tripId:'wrong'});await w.loadTripFromUrl();check(view.dataset.tripState==='error','response id mismatch');w.getTripById=async()=>{throw null};await w.loadTripFromUrl();check(view.dataset.tripState==='error','null provider error');w.getTripById=provider;w.history.replaceState(null,'',search||'?tripId=abc123');await w.loadTripFromUrl();await wait();
   }
   w.renderRanking(w.DRIVE_RUN_MOCK.ranking);w.showPage('ranking');
   const tabs=d.querySelectorAll('.ranking-tab');tabs[2].dispatchEvent(new w.KeyboardEvent('keydown',{key:'Home',bubbles:true}));check(tabs[0].getAttribute('aria-selected')==='true','keyboard Home');tabs[0].dispatchEvent(new w.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));check(d.activeElement===tabs[1]&&w.getComputedStyle(tabs[1]).outlineWidth==='3px','keyboard focus');
   for(const el of d.querySelectorAll('#ranking-view button,.nav-item')){const r=el.getBoundingClientRect();check(r.width>=44&&r.height>=44,'touch target')}
   const scrolls={};for(const page of ['result','ranking']){w.showPage(page);check(d.documentElement.scrollWidth<=w.innerWidth,'horizontal overflow '+page);for(const el of d.querySelectorAll('.view:not([hidden]) .metric,.view:not([hidden]) .rank-row,.view:not([hidden]) .my-rank')){const r=el.getBoundingClientRect();check(r.left>=-.5&&r.right<=w.innerWidth+.5,'horizontal clipping '+page)}scrolls[page]=Math.max(0,d.documentElement.scrollHeight-w.innerHeight);w.scrollTo(0,10000);const last=d.querySelector(page==='ranking'?'.ranking-dashboard':c.error?'#trip-message':'.metric-grid').getBoundingClientRect();check(last.bottom<=d.querySelector('.bottom-nav').getBoundingClientRect().top+1,'menu hides content '+page);w.scrollTo(0,0)}
   check(!d.querySelector('#compare-btn,.recent-record'),'removed controls');check(d.querySelectorAll('.map-attribution a').length===3,'attribution');check((w.qaErrors||[]).length===0,'uncaught script error');
   resolve({size:c.width+'x'+c.height,id:c.id||'invalid',safe:c.safe||0,reload:!!c.reload,assetFailure:!!c.assetFailure,fontFallback:!!c.fontFallback,fail,scrolls});
  }catch(e){resolve({size:c.width+'x'+c.height,query:c.query,error:e.stack})}
 },40)};document.body.append(f);
}))).then(results=>{document.body.replaceChildren();const out=document.createElement('pre');out.id='qa-results';out.textContent=JSON.stringify({cases:results.length,failed:results.filter(r=>r.error||r.fail.length),results});document.body.append(out)});`;
const harness = path.join(scratch,'index.html');
fs.writeFileSync(harness,'<!doctype html><meta charset="utf-8"><body><script>'+script+'</script>');
try {
  const result = spawnSync(browser,['--headless','--disable-gpu','--no-sandbox','--allow-file-access-from-files','--virtual-time-budget=35000','--dump-dom',pathToFileURL(harness).href],{encoding:'utf8',timeout:60000,maxBuffer:8*1024*1024});
  const match = result.stdout?.match(/<pre id="qa-results">([\s\S]*?)<\/pre>/);
  if (!match) throw new Error('Browser did not return results: '+(result.error?.message||result.stderr||'unknown error'));
  const decoded = match[1].replaceAll('&lt;','<').replaceAll('&gt;','>').replaceAll('&amp;','&');
  const report = JSON.parse(decoded);
  console.log(JSON.stringify({cases:report.cases,failed:report.failed,layouts:report.results.filter(r=>(r.id==='abc123'||r.id==='bucheon-cat-91min')&&!r.assetFailure).map(r=>({id:r.id,size:r.size,safe:r.safe,scrolls:r.scrolls}))},null,2));
  if (report.failed.length) process.exitCode=1;
  else console.log('PASS: browser checks (MapLibre API test double; actual map tiles/touch not verified)');
  if (process.argv.includes('--screenshots') && !report.failed.length) {
    const output = path.join(root, '.artifacts', 'design-review');
    fs.mkdirSync(output, {recursive:true});
    const preview = path.join(scratch, 'preview.html');
    const frame = cases.find(c=>c.width===390&&c.id==='abc123'&&!c.safe);
    fs.writeFileSync(preview, '<!doctype html><meta charset="utf-8"><style>body{margin:0;padding:20px;display:flex;gap:20px;background:#171717}iframe{border:0;width:390px;height:844px}</style><iframe title="기록 UI 검사" src="'+frame.file+'?tripId=abc123"></iframe><iframe title="랭킹 UI 검사" src="'+frame.file+'?tripId=abc123#ranking"></iframe>');
    const target = path.join(output,'ui-review.png');
    const shot = spawnSync(process.execPath,[path.join(__dirname,'capture-design.cjs'),browser,pathToFileURL(preview).href,target],{encoding:'utf8',timeout:45000});
    console.log(fs.existsSync(target) ? 'UI review screenshot (map test double): '+target : 'SKIP screenshot: '+(shot.error?.message||shot.stderr));
  }
} finally {
  // Only delete the files in the scratch directory created by this run, after validating its parent.
  if (path.dirname(path.resolve(scratch)) !== root || !path.basename(scratch).startsWith('.qa-stability-')) throw new Error('Unexpected scratch path');
  for (const file of fs.readdirSync(scratch)) fs.unlinkSync(path.join(scratch,file));
  fs.rmdirSync(scratch);
}
