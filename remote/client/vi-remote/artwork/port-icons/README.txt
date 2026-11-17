Port badges: offline PNG assets, 80x80 pixels for 40x40 CSS display.
Sources: built-in imagegen, white lightning/fuel silhouette on black background.
Prompt: isolated bold flat white pictogram, black uniform background, no circle,
no texture, gradient, shadows or text.
export.py thresholds the source symbol and places it within a mathematically
circular, solid-color disk, then supersamples down with Lanczos. No runtime drawing.
Charge colors: green when charging; otherwise yellow when connector present;
otherwise red when flap open. Hidden if none reported.
The collector normalizes H97 OEM veh_common_stat.xml charging states:
bms_vehicleChargingSts 1/2 charging, 3/4/5 not charging, others unknown.
Connector: vcu_chargePortIsConnected 0/1.
Fuel badge consumes fuelFlap; OEM fuel enum decoding is not inferred here.
Browser preview uses synthetic telemetry; no vehicle deployment performed.
