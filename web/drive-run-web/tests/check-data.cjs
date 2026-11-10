'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const context = {window: {}};
vm.runInNewContext(fs.readFileSync(path.join(root, 'data.js'), 'utf8'), context);
const {DRIVE_RUN_TRIPS: trips, DRIVE_RUN_MOCK: mock} = context.window;
const rad = degrees => degrees * Math.PI / 180;
function pathLengthKm(points) {
  let total = 0;
  for (let i = 1; i < points.length; i++) {
    const [lon1, lat1] = points[i - 1], [lon2, lat2] = points[i];
    const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
      + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2;
    total += 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
  }
  return total;
}
const expected = {abc123: 4.859, def456: 2.076, ghi789: 2.256};
for (const [id, trip] of Object.entries(trips)) {
  assert.equal(trip.tripId, id);
  assert.equal(trip.isDemo, true);
  assert.equal(trip.routeType, 'demo');
  assert.equal(trip.pathSource, 'synthetic');
  assert.equal(trip.summarySource, 'independent_demo');
  assert.equal(trip.roadValidated, false);
  assert.notEqual(trip.tripId, trip.userId);
  assert.ok(trip.path.every(p => p.length === 2 && p.every(Number.isFinite)));
  assert.ok(Math.abs(pathLengthKm(trip.path) - expected[id]) < .001);
  assert.equal((new Date(trip.endedAt) - new Date(trip.startedAt)) / 1000, trip.durationSec);
  console.log(`${id}: summary ${trip.distanceKm} km / coordinate polyline ${pathLengthKm(trip.path).toFixed(3)} km (independent demo)`);
}
assert.deepEqual(Object.keys(trips).sort(), Object.keys(expected).sort());
assert.equal(mock.ranking.currentUserId, 'demo-user-me');
assert.equal(mock.ranking.isDemo, true);
for (const person of [...mock.ranking.podium, ...mock.ranking.categories.flatMap(c => c.entries)]) {
  assert.equal(typeof person.isMe, 'boolean');
  assert.equal(person.isMe, person.userId === mock.ranking.currentUserId);
}
console.log('PASS: sample IDs, metadata, identity flags, coordinate distances and durations');
