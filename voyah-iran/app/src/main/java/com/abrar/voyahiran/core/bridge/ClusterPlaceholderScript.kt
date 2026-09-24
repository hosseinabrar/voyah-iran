package com.abrar.voyahiran.core.bridge
internal object ClusterPlaceholderScript {
val source = """
'use strict';
// Only our reserved missing-distance value is changed; ordinary OEM formatting is untouched.
let distance = null, unit = null, heartbeat = Date.now(), active = true;
function restore() {
    active = false;
    if (distance) distance.implementation = null;
    if (unit) unit.implementation = null;
}
rpc.exports = {
    onFridaStdin: function(data) {
        if (data.trim() === 'ping') heartbeat = Date.now();
        if (data.trim() === 'disable') Java.perform(restore);
    },
    dispose: function() { Java.perform(restore); }
};
Java.perform(function() {
    try {
        const format = Java.use('com.qinggan.cluster.GuidanceFormat');
        distance = format.toDistance.overload('int');
        unit = format.toDistanceUnit.overload('android.content.Context', 'int');
        distance.implementation = function(value) {
            return value === -2147483000 ? '-' : distance.call(this, value);
        };
        unit.implementation = function(context, value) {
            return value === -2147483000 ? '' : unit.call(this, context, value);
        };
        console.log('VI_CLUSTER_PLACEHOLDER_READY');
    } catch (e) { restore(); console.log('VI_CLUSTER_PLACEHOLDER_ERROR ' + String(e)); }
});
setInterval(function() {
    if (active && Date.now() - heartbeat > 15000) Java.perform(restore);
}, 1000);
""".trimIndent()
}
