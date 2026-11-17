package main

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
	t "voyah/remote/internal/telemetry"
)

func TestBatteryRequestGates(tst *testing.T) {
	now := int64(100000)
	pct := 80
	enabled := true
	snap := &t.Snapshot{Values: map[string]any{"charging": false, "chargeComplete": false, "chargeConnected": false}, FieldTimes: map[string]int64{"charging": now, "chargeComplete": now, "chargeConnected": now}}
	req := t.Request{Action: "limit", Percent: &pct}
	if got := batteryRequestStatus(req, snap, now); got != "eligible" {
		tst.Fatal(got)
	}
	snap.Values["charging"] = true
	if got := batteryRequestStatus(req, snap, now); got != "state_blocked" {
		tst.Fatal(got)
	}
	snap.Values["charging"] = false
	snap.Values["chargeComplete"] = true
	if got := batteryRequestStatus(req, snap, now); got != "state_blocked" {
		tst.Fatal(got)
	}
	snap.Values["chargeComplete"] = false
	snap.Values["chargeConnected"] = true
	req = t.Request{Action: "schedule", Enabled: &enabled, Start: "23:00", End: "07:00"}
	if got := batteryRequestStatus(req, snap, now); got != "state_blocked" {
		tst.Fatal(got)
	}
	snap.Values["chargeConnected"] = false
	snap.FieldTimes["charging"] = now - 30001
	if got := batteryRequestStatus(req, snap, now); got != "data_unavailable" {
		tst.Fatal(got)
	}
	req.Start = "25:00"
	if got := batteryRequestStatus(req, snap, now); got != "invalid" {
		tst.Fatal(got)
	}
	batteryCapabilities(snap)
	if snap.Values["chargeLimitControl"] != false || snap.Values["chargeScheduleControl"] != false {
		tst.Fatal("Unverified executor advertised")
	}
}

func TestBatterySettingsFrame(tst *testing.T) {
	snap := &t.Snapshot{Values: map[string]any{"chargeLimit": float64(80), "chargeScheduled": true, "chargeStart": "23:00", "chargeEnd": "07:00", "chargeCurrentLimit": float64(32), "chargeForm": float64(1)}}
	pct := 90
	frame, err := batteryFrame(t.Request{Action: "limit", Percent: &pct}, snap)
	if err != nil {
		tst.Fatal(err)
	}
	n := binary.LittleEndian.Uint64(frame[:])
	get := func(start, bits uint) uint64 { return (n >> (start - 1)) & ((1 << bits) - 1) }
	if get(49, 7) != 90 || get(1, 1) != 1 || get(34, 4) != 11 || get(33, 1) != 1 || get(18, 4) != 7 || get(9, 6) != 27 || get(15, 2) != 1 {
		tst.Fatalf("Incorrect frame %x", frame)
	}
	// Do not touch engine start, vehicle forbidden-start, emergency HV-off,
	// charge start/cancel, warmup or sentry bits in the same CAN frame.
	const allowed = uint64(1) | uint64(0xff)<<8 | uint64(0x1f)<<16 | uint64(0x3f)<<24 | uint64(0x1f)<<32 | uint64(0x3f)<<40 | uint64(0x7f)<<48 | uint64(7)<<56
	if n & ^allowed != 0 {
		tst.Fatalf("Unrelated control bits: %x", n)
	}
	yes := true
	frame, err = batteryFrame(t.Request{Action: "schedule", Enabled: &yes, Start: "00:00", End: "12:30"}, snap)
	if err != nil {
		tst.Fatal(err)
	}
	n = binary.LittleEndian.Uint64(frame[:])
	if get(34, 4) != 12 || get(33, 1) != 0 || get(18, 4) != 12 || get(17, 1) != 1 || get(25, 6) != 30 || get(49, 7) != 80 {
		tst.Fatalf("Noon/midnight encoding: %x", frame)
	}
	no := false
	frame, err = batteryFrame(t.Request{Action: "schedule", Enabled: &no}, snap)
	if err != nil {
		tst.Fatal(err)
	}
	n = binary.LittleEndian.Uint64(frame[:])
	if get(1, 1) != 0 || get(57, 2) != 0 || get(49, 7) != 80 {
		tst.Fatalf("Disable schedule: %x", frame)
	}
	delete(snap.Values, "chargeCurrentLimit")
	if _, err = batteryFrame(t.Request{Action: "limit", Percent: &pct}, snap); err == nil {
		tst.Fatal("Missing current must not become 5A")
	}
}

func TestEncryptedBatteryDispatch(tst *testing.T) {
	dir := tst.TempDir()
	store, _ := t.Load(filepath.Join(dir, "state.json"))
	key := make([]byte, 32)
	device := t.Device{ID: t.Random(), Key: t.Encoding.EncodeToString(key)}
	store.State.Devices = []t.Device{device}
	store.State.RemoteAccess = true
	store.State.Expires = time.Now().Add(time.Hour).Unix()
	now := time.Now().UnixMilli()
	snap := t.Snapshot{ObservedAt: now, Values: map[string]any{"charging": false, "chargeComplete": false, "chargeConnected": false, "chargeLimit": 80, "chargeScheduled": false, "chargeCurrentLimit": 32, "chargeForm": 1}, FieldTimes: map[string]int64{}}
	for k := range snap.Values {
		snap.FieldTimes[k] = now
	}
	path := filepath.Join(dir, "snapshot.json")
	save := func() { b, _ := json.Marshal(snap); os.WriteFile(path, b, 0600) }
	save()
	calls := 0
	svc := service{store: store, snapshotPath: path, nativeRun: func(ctx context.Context, b []byte) ([]byte, error) {
		calls++
		var q struct {
			CanID    int
			Data     []int
			Deadline int64
		}
		if json.Unmarshal(b, &q) != nil || q.CanID != 126 || len(q.Data) != 8 || q.Data[6] != 90 || q.Deadline <= now {
			tst.Fatalf("bad native request %s", b)
		}
		return []byte("VI_RESULT {\"status\":\"accepted\"}\n"), nil
	}}
	pct := 90
	request := func() t.Envelope {
		q := t.Envelope{Vehicle: store.State.Vehicle, Device: device.ID, ID: t.Random()}
		p, _ := t.Seal(key, q, "request", t.Request{ID: q.ID, SentAt: time.Now().UnixMilli(), Kind: "battery-command", Action: "limit", Percent: &pct})
		return p
	}
	q := request()
	reply, err := svc.handle(q)
	var r t.Reply
	if err != nil || t.Open(key, reply, "reply", &r) != nil || r.Status != "accepted" || calls != 1 {
		tst.Fatal(r, err, calls)
	}
	if _, err = svc.handle(q); err == nil || calls != 1 {
		tst.Fatal("replayed actuator request")
	}
	snap.Values["chargeComplete"] = true
	save()
	reply, err = svc.handle(request())
	t.Open(key, reply, "reply", &r)
	if err != nil || r.Status != "state_blocked" || calls != 1 {
		tst.Fatal("complete must block", r, err)
	}
	store.State.RemoteAccess = false
	reply, err = svc.handle(request())
	t.Open(key, reply, "reply", &r)
	if err != nil || r.Status != "subscription_expired" || calls != 1 {
		tst.Fatal("subscription must block", r, err)
	}
}
