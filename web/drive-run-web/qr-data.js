'use strict';
// Vehicle QR v1 -> the same view model used by sample trips.
window.DRIVE_RUN_QR = (() => {
  const invalid = () => { throw new Error('QR 데이터가 올바르지 않습니다. 차량에서 QR을 다시 생성해 주세요.'); };
  function decodePolyline(encoded) {
    if (typeof encoded !== 'string') return invalid();
    let index = 0, lat = 0, lng = 0;
    const points = [];
    function delta() {
      let result = 0, shift = 0, byte;
      do {
        if (index >= encoded.length || shift > 30) return invalid();
        byte = encoded.charCodeAt(index++) - 63;
        if (byte < 0 || byte > 63) return invalid();
        result += (byte & 31) * 2 ** shift;
        shift += 5;
      } while (byte >= 32);
      return result % 2 ? -(result + 1) / 2 : result / 2;
    }
    while (index < encoded.length) {
      lat += delta(); lng += delta();
      if (Math.abs(lat) > 9000000 || Math.abs(lng) > 18000000) return invalid();
      points.push([lng / 1e5, lat / 1e5]);
    }
    return points;
  }
  function decode(encoded, tripId) {
    try {
      if (typeof encoded !== 'string' || !/^[A-Za-z0-9_-]+$/.test(encoded) || encoded.length % 4 === 1) return invalid();
      const bytes = Uint8Array.from(atob(encoded.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
      const p = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes));
      if (!p || Array.isArray(p) || p.v !== 1) return invalid();
      if (typeof p.id !== 'string' || !p.id.trim() || (tripId !== null && p.id !== tripId)) return invalid();
      for (const key of ['course', 'region', 'start', 'end']) {
        if (typeof p[key] !== 'string' || !p[key].trim()) return invalid();
      }
      for (const key of ['km', 'sec']) {
        if (typeof p[key] !== 'number' || !Number.isFinite(p[key]) || p[key] < 0) return invalid();
      }
      if (!Number.isFinite(Date.parse(p.start)) || !Number.isFinite(Date.parse(p.end)) || Date.parse(p.end) < Date.parse(p.start)) return invalid();
      if (p.eff !== undefined && (typeof p.eff !== 'number' || !Number.isFinite(p.eff) || p.eff < 0)) return invalid();
      if (p.unit !== undefined && p.unit !== 'kwh' && p.unit !== 'l') return invalid();
      return {
        tripId: p.id, courseName: p.course, region: p.region,
        startedAt: p.start, endedAt: p.end, distanceKm: p.km, durationSec: p.sec,
        avgFuelEconomy: p.eff, efficiencyUnit: p.unit, path: decodePolyline(p.pl),
        isDemo: false, routeType: 'recorded', pathSource: 'vehicle_qr'
      };
    } catch { return invalid(); }
  }
  return {decode, decodePolyline};
})();
