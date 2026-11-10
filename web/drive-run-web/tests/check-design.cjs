'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const css = fs.readFileSync(path.join(__dirname, '..', 'styles.css'), 'utf8');
const token = name => {
  const match = css.match(new RegExp('--' + name + ':\\s*(#[0-9A-Fa-f]{6})'));
  assert.ok(match, 'Missing color token: ' + name);
  return match[1].toUpperCase();
};
for (const [name, hex] of Object.entries({primary:'#C8FF00', background:'#0B0B0B', surface:'#171717', text:'#F5F5F0', 'secondary-text':'#949494'})) assert.equal(token(name), hex);
function luminance(hex) {
  const rgb = [1,3,5].map(i => parseInt(hex.slice(i,i+2),16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4);
  return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
}
for (const [fg,bg] of [['text','background'],['text','surface'],['secondary-text','background'],['secondary-text','surface'],['background','primary'],['text','podium-bronze']]) {
  const a=luminance(token(fg)),b=luminance(token(bg)),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  assert.ok(ratio >= 4.5, 'Insufficient text contrast: '+fg+' / '+bg);
  console.log(`${fg} / ${bg}: ${ratio.toFixed(2)}:1`);
}
assert.ok(css.includes('font-display: swap'));
assert.ok(css.includes('system-ui, sans-serif'));
assert.ok(!css.includes('gradient('), 'Unexpected decorative gradient');
console.log('PASS: requested color tokens, main text contrast pairs, font fallback, no gradients (not a complete WCAG audit)');
