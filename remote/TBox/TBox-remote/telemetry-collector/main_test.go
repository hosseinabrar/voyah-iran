package main

import (
	"testing"
)

func TestIntelOneBased(t *testing.T) {
	v, ok := decode([]byte{0, 0, 0x10, 0, 0, 0, 0, 0}, signal{Start: 21, Bits: 1, Factor: 1})
	if !ok || v != 1 {
		t.Fatal(v, ok)
	}
	v, ok = decode([]byte{0x0f, 0x01, 0, 0, 0, 0, 0, 0}, signal{Start: 1, Bits: 10, Factor: .1})
	if !ok || v != 27.1 {
		t.Fatal(v, ok)
	}
	if _, ok = decode([]byte{1}, signal{Start: 9, Bits: 1, Factor: 1}); ok {
		t.Fatal("truncated frame accepted")
	}
}

func TestOEMEnumsAndFreshness(t *testing.T) {
	s := sample{Values: map[string]any{}, FieldTimes: map[string]int64{}}
	raw := map[string]float64{"bcm_doorFLLockSts": 0, "pot_tailgateOpenSts": 1, "bcm_sunroofSts": 0, "bcm_windowLFSts": 2, "BCM_FRWindowHorizontalSts": 10, "bcm_pepsPowerMode": 1}
	times := map[string]int64{}
	for k := range raw {
		times[k] = 100000
	}
	normalize(raw, times, &s, 100000)
	if s.Values["locked"] != true || s.Values["trunk"] != false || s.Values["sunroof"] != "vent" || s.Values["windowFL"] != "closed" || s.Values["windowFR"] != "vent" || s.Values["power"] != "sleep" {
		t.Fatal(s)
	}
	normalize(raw, times, &s, 200000)
	if _, exists := s.Values["locked"]; exists {
		t.Fatal("stale value remained valid")
	}
}

func TestChargingNormalization(t *testing.T) {
	for state := float64(0); state <= 7; state++ {
		out := sample{Values: map[string]any{}, FieldTimes: map[string]int64{}}
		raw := map[string]float64{"bms_vehicleChargingSts": state, "vcu_chargePortIsConnected": 1}
		times := map[string]int64{"bms_vehicleChargingSts": 1000, "vcu_chargePortIsConnected": 1000}
		normalize(raw, times, &out, 1000)
		value, ok := out.Values["charging"]
		if ok != (state >= 1 && state <= 5) {
			t.Fatalf("state %v validity", state)
		}
		if ok && value != (state == 1 || state == 2) {
			t.Fatalf("state %v charging", state)
		}
		if ok && out.Values["chargeComplete"] != (state == 4) {
			t.Fatalf("state %v complete", state)
		}
		if out.Values["chargeConnected"] != true {
			t.Fatal("connector")
		}
		normalize(raw, times, &out, 62000)
		if _, ok = out.Values["charging"]; ok {
			t.Fatal("stale charging retained")
		}
		if _, ok = out.Values["chargeConnected"]; ok {
			t.Fatal("stale connector retained")
		}
	}
}

func TestScheduleSettingsFeedback(t *testing.T) {
	out := sample{Values: map[string]any{}, FieldTimes: map[string]int64{}}
	raw := map[string]float64{"ivi_reservationChargeModeSet": 1, "ivi_ChargeStartTimeHour": 12, "ivi_ChargeStartTimeHalfDay": 0, "ivi_ChargeStartTimeMinute": 5, "ivi_ChargeStartTimeValid": 1, "ivi_ChargeEndTimeHour": 12, "ivi_ChargeEndTimeHalfDay": 1, "ivi_ChargeEndTimeMinute": 30, "ivi_ChargeEndTimeValid": 1}
	times := map[string]int64{}
	for k := range raw {
		times[k] = 1000
	}
	normalize(raw, times, &out, 1000)
	if out.Values["chargeStart"] != "00:05" || out.Values["chargeEnd"] != "12:30" || out.Values["chargeScheduled"] != true {
		t.Fatal(out.Values)
	}
}
