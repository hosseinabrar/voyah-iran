const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../app/src/main');
const source = name => fs.readFileSync(path.join(root, 'java/com/abrar/voyahiran/feature/launcher', name + '.kt'), 'utf8');
const code = source('LauncherWeatherCode');
for (const [codes, resource] of [['0, 1','moon'],['2','night_cloudy'],['80','night_light_shower'],['81','night_shower'],['82','night_heavy_shower'],['85, 86','night_light_snow_shower']]) {
  assert.ok(code.includes(`${codes} -> R.drawable.launcher_weather_${resource}`));
}
for (const name of new Set([...code.matchAll(/R\.drawable\.(\w+)/g)].map(m => m[1]))) {
  const data = fs.readFileSync(path.join(root, 'res/drawable-nodpi', name + '.png'));
  assert.equal(data.subarray(1, 4).toString(), 'PNG', name);
}
assert.ok(source('LauncherDashboard').includes('LauncherWeatherCode.icon(weather.code,weather.day)'));
assert.ok(source('PassengerWeatherView').includes('LauncherWeatherCode.icon(code,day)'));
assert.ok(source('PassengerWeatherView').includes('it.code to it.day'));
assert.ok(!source('LauncherDashboard').includes('nightCloudBitmap'));
assert.ok(source('LauncherWeather').includes('hourly=temperature_2m,weather_code,is_day'));
console.log('Weather day/night resource and consumer contracts passed.');
