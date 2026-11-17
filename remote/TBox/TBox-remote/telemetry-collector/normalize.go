package main

import "fmt"

// The remotecontrol/control_atoms.xml result predicates, not the generic
// veh_common_stat defaults, define these OEM wire values. See README-telemetry.txt.
func normalize(raw map[string]float64, times map[string]int64, out *sample, now int64) {
	for _, key := range []string{"charging", "chargeComplete", "chargeConnected", "locked", "trunk", "sunroof", "windowFL", "windowFR", "windowRL", "windowRR", "power", "lights"} {
		delete(out.Values, key)
		delete(out.FieldTimes, key)
	}
	get := func(k string) (float64, bool) { v, ok := raw[k]; return v, ok && times[k] > 0 && now-times[k] <= 60000 }
	put := func(key string, value any, signals ...string) {
		at := now
		for _, s := range signals {
			if times[s] < at {
				at = times[s]
			}
		}
		out.Values[key] = value
		out.FieldTimes[key] = at
	}
	// OEM H97 veh_common_stat.xml: raw charging states 1/2 map to
	// charging in park/drive; 3/4/5 map to idle/full/error. 0/6/7 unknown.
	if v, ok := get("bms_vehicleChargingSts"); ok && v >= 1 && v <= 5 {
		put("charging", v == 1 || v == 2, "bms_vehicleChargingSts")
		put("chargeComplete", v == 4, "bms_vehicleChargingSts")
	}
	if v, ok := get("vcu_chargePortIsConnected"); ok && (v == 0 || v == 1) {
		put("chargeConnected", v == 1, "vcu_chargePortIsConnected")
	}
	if v, ok := get("bcm_doorFLLockSts"); ok && (v == 0 || v == 1) {
		put("locked", v == 0, "bcm_doorFLLockSts")
	}
	if v, ok := get("pot_tailgateOpenSts"); ok && (v == 0 || v == 1) {
		put("trunk", v == 0, "pot_tailgateOpenSts")
	}
	if v, ok := get("bcm_sunroofSts"); ok {
		switch v {
		case 0:
			put("sunroof", "vent", "bcm_sunroofSts")
		case 3:
			put("sunroof", "closed", "bcm_sunroofSts")
		case 6:
			put("sunroof", "open", "bcm_sunroofSts")
		}
	}
	for _, side := range []struct{ raw, ui string }{{"LF", "FL"}, {"RF", "FR"}, {"LR", "RL"}, {"RR", "RR"}} {
		status := "bcm_window" + side.raw + "Sts"
		pos := "BCM_" + side.ui + "WindowHorizontalSts"
		if v, ok := get(status); ok {
			if v == 2 {
				put("window"+side.ui, "closed", status)
			} else if v == 1 {
				put("window"+side.ui, "open", status)
			}
		}
		// OEM vent command explicitly requests and confirms position 10.
		if v, ok := get(pos); ok && v == 10 && times[pos] >= times[status] && out.Values["window"+side.ui] != "closed" {
			put("window"+side.ui, "vent", pos)
		}
	}
	// Headunit setting feedback is distinct from our transmitted TBOX command.
	if mode, ok := get("ivi_reservationChargeModeSet"); ok && (mode == 0 || mode == 1) {
		put("chargeScheduled", mode == 1, "ivi_reservationChargeModeSet")
	}
	for _, part := range []string{"Start", "End"} {
		prefix := "ivi_Charge" + part + "Time"
		hour, a := get(prefix + "Hour")
		minute, b := get(prefix + "Minute")
		half, c := get(prefix + "HalfDay")
		valid, d := get(prefix + "Valid")
		if a && b && c && d && valid == 1 && hour >= 1 && hour <= 12 && minute >= 0 && minute <= 59 && (half == 0 || half == 1) {
			put("charge"+part, fmt.Sprintf("%02d:%02d", int(hour)%12+int(half)*12, int(minute)), prefix+"Hour", prefix+"Minute", prefix+"HalfDay", prefix+"Valid")
		}
	}
	p, pok := get("bcm_pepsPowerMode")
	r, rok := get("vcu_ready")
	if rok && r == 1 {
		put("power", "ready", "vcu_ready")
	} else if pok && p == 1 {
		put("power", "sleep", "bcm_pepsPowerMode")
	} else if pok && p == 2 && rok && r == 0 {
		put("power", "on", "bcm_pepsPowerMode", "vcu_ready")
	}
	low, lok := get("bcm_lowBeamSts")
	high, hok := get("bcm_highBeamSts")
	if lok && hok {
		put("lights", low == 1 || high == 1, "bcm_lowBeamSts", "bcm_highBeamSts")
	}
}
