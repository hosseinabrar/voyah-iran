// Source-level guards; real Neshan navigation/layout needs a vehicle test.
const fs=require('fs'), path=require('path'), assert=require('assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../../app/src/main/java/com/abrar/voyahiran/feature/navigation/NeshanVoiceNavigation.kt'),'utf8');
assert(source.includes('LauncherPreferences.enabled(app) &&'));
assert(source.includes('NavigationPreferences(app).navigator == NavigationPreferences.NESHAN'));
assert(source.includes('val preparedCard = if (preferCard) prepareLauncherCard(app) else null'));
assert(source.includes('val reuse = preparedCard ?: visibleDisplay'));
assert(source.indexOf('prepareLauncherCard(app)')<source.indexOf('active.destinationSent = true'));
const prepare=source.split('private suspend fun prepareLauncherCard')[1].split('private suspend fun bridgeRetry')[0];
assert(prepare.includes('VoyahLauncherActivity::class.java'));
assert(prepare.includes('launchDisplayId=0'));
assert(prepare.includes('VoyahLauncherActivity.mapPageVisible'));
assert(prepare.includes('"launcher-map-claim"'));
assert(prepare.includes('MAP_DISPLAY=$card MAP_VISIBLE=1'));
assert(prepare.includes('stable>=3'));
assert(prepare.includes('stableDisplay!=card'));
assert(prepare.includes('destination was not sent'));
assert(!prepare.includes('force-stop') && !prepare.includes('launcher-map-clean'));
console.log('PASS: voice route prefers prepared launcher card, waits for visibility, no destructive reload/fullscreen fallback');
