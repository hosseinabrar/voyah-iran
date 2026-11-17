package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"
	"time"
	t "voyah/remote/internal/telemetry"
)

// The installer probes the native VNS session before enabling commands.
func batteryCapabilities(v *t.Snapshot) {
	if v == nil {
		return
	}
	if v.Values == nil {
		v.Values = map[string]any{}
	}
	if v.FieldTimes == nil {
		v.FieldTimes = map[string]int64{}
	}
	_, err := os.Stat("/data/vi-remote/native-ready")
	v.Values["chargeLimitControl"] = err == nil
	v.Values["chargeScheduleControl"] = err == nil
	v.FieldTimes["chargeLimitControl"] = time.Now().UnixMilli()
	v.FieldTimes["chargeScheduleControl"] = time.Now().UnixMilli()
}

func batteryRequestStatus(req t.Request, v *t.Snapshot, now int64) string {
	if req.Action != "limit" && req.Action != "schedule" {
		return "invalid"
	}
	if req.Action == "limit" && (req.Percent == nil || *req.Percent < 60 || *req.Percent > 100 || *req.Percent%5 != 0) {
		return "invalid"
	}
	if req.Action == "schedule" {
		if req.Enabled == nil {
			return "invalid"
		}
		if *req.Enabled {
			_, a := time.Parse("15:04", req.Start)
			_, b := time.Parse("15:04", req.End)
			if a != nil || b != nil || len(req.Start) != 5 || len(req.End) != 5 || req.Start == req.End {
				return "invalid"
			}
		}
	}
	if v == nil {
		return "data_unavailable"
	}
	keys := []string{"charging", "chargeConnected"}
	if req.Action == "limit" {
		keys = append(keys, "chargeComplete")
	}
	for _, key := range keys {
		at := v.FieldTimes[key]
		if at <= 0 || at > now+5000 || now-at > 30000 {
			return "data_unavailable"
		}
		if _, ok := v.Values[key].(bool); !ok {
			return "data_unavailable"
		}
	}
	if v.Values["charging"] == true || req.Action == "limit" && v.Values["chargeComplete"] == true || req.Action == "schedule" && v.Values["chargeConnected"] == true {
		return "state_blocked"
	}
	return "eligible"
}

// Build only the charging settings frame (0x7e), never a general CAN endpoint.
// Bit positions are shared by the inspected X and C OEM can-in.xml profiles.
func batteryFrame(req t.Request, v *t.Snapshot) ([8]byte, error) {
	var data [8]byte
	put := func(start, bits uint, value uint64) {
		for i := uint(0); i < bits; i++ {
			if value&(1<<i) != 0 {
				bit := start - 1 + i
				data[bit/8] |= 1 << (bit % 8)
			}
		}
	}
	limit, ok := v.Values["chargeLimit"].(float64)
	if !ok {
		return data, fmt.Errorf("missing limit")
	}
	scheduled, ok := v.Values["chargeScheduled"].(bool)
	if !ok {
		return data, fmt.Errorf("missing schedule")
	}
	start, _ := v.Values["chargeStart"].(string)
	end, _ := v.Values["chargeEnd"].(string)
	if req.Action == "limit" {
		limit = float64(*req.Percent)
	} else {
		scheduled = *req.Enabled
		start = req.Start
		end = req.End
	}
	if limit < 60 || limit > 100 || int(limit)%5 != 0 {
		return data, fmt.Errorf("invalid limit")
	}
	current, ok := v.Values["chargeCurrentLimit"].(float64)
	if !ok || current < 5 || current > 68 {
		return data, fmt.Errorf("missing current limit")
	}
	form, ok := v.Values["chargeForm"].(float64)
	if !ok || form < 0 || form > 3 {
		return data, fmt.Errorf("missing charge form")
	}
	put(9, 6, uint64(current-5))
	put(15, 2, uint64(form))
	put(49, 7, uint64(limit))
	put(59, 1, 1)
	if scheduled {
		a, err := time.Parse("15:04", start)
		if err != nil {
			return data, err
		}
		b, err := time.Parse("15:04", end)
		if err != nil || start == end {
			return data, fmt.Errorf("invalid schedule")
		}
		put(1, 1, 1)
		put(33, 1, uint64(a.Hour()/12))
		put(34, 4, uint64((a.Hour()+11)%12+1))
		put(41, 6, uint64(a.Minute()))
		put(17, 1, uint64(b.Hour()/12))
		put(18, 4, uint64((b.Hour()+11)%12+1))
		put(25, 6, uint64(b.Minute()))
		put(57, 1, 1)
		put(58, 1, 1)
	}
	return data, nil
}

func (s *service) executeBattery(req t.Request) string {
	snap := s.snapshot()
	now := time.Now().UnixMilli()
	if status := batteryRequestStatus(req, snap, now); status != "eligible" {
		return status
	}
	// Preserve the other setting; missing/stale fields cannot be replaced by zeros.
	keys := []string{"chargeLimit", "chargeScheduled", "chargeCurrentLimit", "chargeForm"}
	if snap.Values["chargeScheduled"] == true && req.Action == "limit" {
		keys = append(keys, "chargeStart", "chargeEnd")
	}
	for _, key := range keys {
		at := snap.FieldTimes[key]
		if at <= 0 || now-at > 30000 || at > now+5000 {
			return "data_unavailable"
		}
	}
	data, err := batteryFrame(req, snap)
	if err != nil {
		return "data_unavailable"
	}
	parent := s.ctx
	if parent == nil {
		parent = context.Background()
	}
	ctx, cancel := context.WithTimeout(parent, 6*time.Second)
	defer cancel()
	deadline := time.Now().Add(5500 * time.Millisecond).UnixMilli()
	// []byte encodes as base64; use integers for the Python JSON contract.
	payload := make([]int, 8)
	for i, b := range data {
		payload[i] = int(b)
	}
	input, _ := json.Marshal(map[string]any{"canID": 126, "data": payload, "deadline": deadline})
	run := s.nativeRun
	if run == nil {
		run = func(ctx context.Context, input []byte) ([]byte, error) {
			cmd := exec.CommandContext(ctx, "/data/vi-remote/bin/vns-battery")
			cmd.Stdin = bytes.NewReader(input)
			return cmd.CombinedOutput()
		}
	}
	output, err := run(ctx, input)
	if err != nil {
		log.Printf("Battery native command failed: %v", err)
		return "failed"
	}
	for _, line := range strings.Split(string(output), "\n") {
		if strings.HasPrefix(line, "VI_RESULT ") {
			var r struct{ Status string }
			if json.Unmarshal([]byte(strings.TrimPrefix(line, "VI_RESULT ")), &r) == nil && r.Status == "accepted" {
				return "accepted"
			}
		}
	}
	return "failed"
}
