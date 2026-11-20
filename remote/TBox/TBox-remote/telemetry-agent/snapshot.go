package main

import (
	"strings"
	"time"
	t "voyah/remote/internal/telemetry"
)

var batteryFields = map[string]bool{}

func init() {
	for _, k := range strings.Fields("battery charging chargeConnected chargeComplete chargeWaiting chargeFlap chargeScheduled chargeStart chargeEnd chargeNextStartAt chargePowerKW chargeRemainingMinutes chargeLimit chargeCurrentLimit chargeForm batteryVoltage batteryCurrent batteryTemperature batteryHealth auxVoltage auxSOC") {
		batteryFields[k] = true
	}
}
func (s *service) pageSnapshot(page string) *t.Snapshot {
	s.keepAwake()
	snap := s.snapshot()
	// A cumulative count alone cannot detect sleep: no recent CAN messages can.
	if snap == nil || snap.CANMessages == 0 || time.Now().UnixMilli()-snap.LastCANAt >= 5000 {
		deadline := time.Now().Add(1500 * time.Millisecond)
		for time.Now().Before(deadline) {
			snap = s.snapshot()
			if snap != nil && snap.CANMessages > 0 && time.Now().UnixMilli()-snap.LastCANAt < 5000 {
				break
			}
			time.Sleep(100 * time.Millisecond)
		}
	}
	if snap == nil || snap.CANMessages == 0 || time.Now().UnixMilli()-snap.LastCANAt >= 5000 {
		return nil
	}
	if page != "battery" && page != "vehicle" {
		page = "vehicle"
	}
	for k := range snap.Values {
		keep := batteryFields[k]
		if page == "vehicle" {
			keep = !batteryFields[k] || k == "battery" || k == "charging" || k == "chargeConnected" || k == "chargeComplete" || k == "chargeWaiting" || k == "chargeFlap" || k == "chargeScheduled" || k == "chargeStart"
		}
		if !keep {
			delete(snap.Values, k)
			delete(snap.FieldTimes, k)
		}
	}
	return snap
}
