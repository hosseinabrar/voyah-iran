package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
	t "voyah/remote/internal/telemetry"
)

func TestWakeBeforePageSnapshot(test *testing.T) {
	path := filepath.Join(test.TempDir(), "snapshot.json")
	now := time.Now().UnixMilli()
	snap := t.Snapshot{ObservedAt: now - 10000, LastCANAt: now - 10000, CANMessages: 42, Values: map[string]any{"battery": 68, "batteryVoltage": 356, "fuel": 54}, FieldTimes: map[string]int64{"battery": now, "batteryVoltage": now, "fuel": now}}
	save := func() {
		b, _ := json.Marshal(snap)
		if e := os.WriteFile(path, b, 0600); e != nil {
			test.Fatal(e)
		}
	}
	save()
	calls := 0
	s := service{snapshotPath: path, wake: func() {
		calls++
		snap.CANMessages++
		snap.LastCANAt = time.Now().UnixMilli()
		snap.ObservedAt = snap.LastCANAt
		save()
	}}
	b := s.pageSnapshot("battery")
	if calls != 1 || b == nil || b.Values["fuel"] != nil || b.Values["batteryVoltage"] == nil {
		test.Fatal(calls, b)
	}
	v := s.pageSnapshot("vehicle")
	if calls != 2 || v.Values["batteryVoltage"] != nil || v.Values["fuel"] == nil {
		test.Fatal(calls, v)
	}
	// A zero CAN count must wake even if the snapshot file was recently written.
	snap.CANMessages = 0
	save()
	s.pageSnapshot("battery")
	if calls != 3 {
		test.Fatal("zero count did not wake")
	}
}
