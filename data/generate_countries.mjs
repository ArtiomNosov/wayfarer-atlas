// Offline generation from IANA tzdata public-domain ISO table + ICU locale data.
// Run with Node.js 22+ (full ICU); no keys, signups, network, or user data.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const source = process.argv[2] ?? '/usr/share/zoneinfo/iso3166.tab';
const en = new Intl.DisplayNames(['en'], { type: 'region' });
const ru = new Intl.DisplayNames(['ru'], { type: 'region' });
if (ru.of('DE') !== 'Германия') throw new Error('Node full ICU with Russian locale is required');
const countries = fs.readFileSync(source, 'utf8').split('\n')
  .filter(line => /^[A-Z]{2}\t/.test(line))
  .map(line => line.slice(0, 2))
  .map(code => ({ code, nameEn: en.of(code), nameRu: ru.of(code), centroidLon: null, centroidLat: null, isoAssigned: true }));
if (countries.length < 240) throw new Error('ISO table is incomplete');
// XK is CLDR's user-assigned Kosovo code, used by visa datasets. Do not label it ISO-assigned.
countries.push({code: 'XK', nameEn: en.of('XK'), nameRu: ru.of('XK'), centroidLon: null, centroidLat: null, isoAssigned: false});
countries.sort((left, right) => left.code.localeCompare(right.code));
fs.writeFileSync(path.join(root, 'countries.json'), JSON.stringify({
  version: 1,
  source: 'IANA tzdata iso3166.tab; Unicode CLDR names via Node.js Intl.DisplayNames / ICU',
  updatedAt: '2026-10-25',
  countries,
}, null, 2) + '\n');
console.log(JSON.stringify({ countries: countries.length, node: process.version, icu: process.versions.icu, cldr: process.versions.cldr, unicode: process.versions.unicode }));
