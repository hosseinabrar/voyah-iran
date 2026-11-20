package main

import (
	"bytes"
	"fmt"
	"net/http/httptest"
	"testing"
	"time"
	t "voyah/remote/internal/telemetry"
)

func nmea(body string) string {
	var sum byte
	for _, b := range []byte(body) {
		sum ^= b
	}
	return fmt.Sprintf("$%s*%02X", body, sum)
}
func TestOEMLocationValidation(tst *testing.T) {
	now := time.Date(2026, 11, 20, 12, 0, 0, 0, time.UTC)
	body := "GNRMC,120000.00,A,3542.0000,N,05124.0000,E,0,0,201126,,,A"
	p := parseRMC(nmea(body), now)
	if p == nil || p.Latitude != 35.7 || p.Longitude != 51.4 || p.At != now.UnixMilli() {
		tst.Fatalf("fix: %+v", p)
	}
	for _, v := range []string{nmea(body) + "x", nmea("GNRMC,120000.00,V,3542.0000,N,05124.0000,E,0,0,201126,,,A"), nmea("GNRMC,120000.00,A,3562.0000,N,05124.0000,E,0,0,201126,,,A"), nmea("GNRMC,120000.00,A,3542.0000,N,05124.0000,E,0,0,201126,,,E"), "$"} {
		if parseRMC(v, now) != nil {
			tst.Fatal("invalid fix", v)
		}
	}
	if parseRMC(nmea(body), now.Add(-time.Minute)) != nil || parseRMC(nmea(body), now.Add(25*time.Hour)) != nil {
		tst.Fatal("future/stale fix")
	}
	if !locationContract(`<node><interface name="gdbus.hcommon.location"><signal name="update_nmea_info"><arg type="i"/><arg type="s"/></signal></interface></node>`) {
		tst.Fatal("OEM contract")
	}
	if locationContract(`<node><interface name="gdbus.hcommon.location"><signal name="update_nmea_info"><arg type="s"/></signal></interface></node>`) {
		tst.Fatal("unknown ABI")
	}
}
func TestSecurityRejectsHeadunitInjection(tst *testing.T) {
	store, err := t.Load(tst.TempDir() + "/state.json")
	if err != nil {
		tst.Fatal(err)
	}
	store.State.RemoteAccess = true
	store.State.Expires = time.Now().Add(time.Hour).Unix()
	svc := service{store: store, headunit: "172.16.104.40"}
	for _, host := range []string{"172.16.104.40", "127.0.0.1", "8.8.8.8"} {
		r := httptest.NewRequest("POST", "/local?security=1", bytes.NewBufferString(`{"action":"security-poll","dvr":true,"location":{"latitude":35,"longitude":51}}`))
		r.RemoteAddr = host + ":1234"
		w := httptest.NewRecorder()
		svc.local(w, r)
		if w.Code < 400 || svc.security.location != nil || svc.security.dvr != nil {
			tst.Fatal("headunit injection", host, w.Code)
		}
	}
}
func TestSecurityNoInventedCameraOrFix(tst *testing.T) {
	s := securityService{}
	if status, _ := s.execute("a", t.Request{Action: "capture", Camera: 1}); status != "camera_unavailable" {
		tst.Fatal(status)
	}
	at := time.Now().Add(-time.Minute).UnixMilli()
	s.location = &vehicleLocation{Latitude: 35, Longitude: 51, At: at}
	_, r := s.execute("a", t.Request{Action: "state"})
	if r["location"].(*vehicleLocation).At != at {
		tst.Fatal("response refreshed fix age")
	}
	s.revoke("")
	if !s.lease.IsZero() {
		tst.Fatal("lease retained")
	}
}

func TestOEMLocationFreshness(t *testing.T) {
	now := int64(1795188031000)
	p := vehicleLocation{Latitude: 35, Longitude: 51, At: now - 1000}
	if !validOEMLocation(p, now) {
		t.Fatal("fresh fix rejected")
	}
	for _, stamp := range []int64{0, now - 86400001, now + 5001} {
		p.At = stamp
		if validOEMLocation(p, now) {
			t.Fatal("invalid timestamp accepted", stamp)
		}
	}
	p.At = now
	p.Latitude = 91
	if validOEMLocation(p, now) {
		t.Fatal("invalid coordinates accepted")
	}
}
