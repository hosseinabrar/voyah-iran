package main

import (
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
	t "voyah/remote/internal/telemetry"
)

func TestAuthorizationReplayAndRevocation(test *testing.T) {
	s, _ := t.Load(filepath.Join(test.TempDir(), "state.json"))
	key := make([]byte, 32)
	d := t.Device{ID: t.Random(), Name: "Phone", Key: t.Encoding.EncodeToString(key)}
	s.State.Devices = []t.Device{d}
	svc := service{store: s}
	request := func() t.Envelope {
		q := t.Envelope{Vehicle: s.State.Vehicle, Device: d.ID, ID: t.Random()}
		p, e := t.Seal(key, q, "request", t.Request{ID: q.ID, Kind: "ping", SentAt: time.Now().UnixMilli()})
		if e != nil {
			test.Fatal(e)
		}
		return p
	}
	q := request()
	reply, e := svc.handle(q)
	if e != nil {
		test.Fatal(e)
	}
	var r t.Reply
	if e = t.Open(key, reply, "reply", &r); e != nil || r.Status != "subscription_expired" {
		test.Fatal(r, e)
	}
	if _, e = svc.handle(q); e == nil {
		test.Fatal("accepted replay")
	}
	s.State.RemoteAccess = true
	s.State.Expires = time.Now().Add(time.Hour).Unix()
	reply, e = svc.handle(request())
	if e != nil {
		test.Fatal(e)
	}
	t.Open(key, reply, "reply", &r)
	if r.Status != "ok" {
		test.Fatal(r)
	}
	s.State.Devices = nil
	if _, e = svc.handle(request()); e == nil {
		test.Fatal("revoked device accepted")
	}
}

func TestPairHandshakeAndHeadunitManagement(test *testing.T) {
	s, _ := t.Load(filepath.Join(test.TempDir(), "state.json"))
	svc := service{store: s, headunit: "172.16.104.40", relay: "https://remote.voyah-iran.ir"}
	local := func(body string, host string) *httptest.ResponseRecorder {
		req := httptest.NewRequest("POST", "/local", strings.NewReader(body))
		req.RemoteAddr = host + ":1234"
		w := httptest.NewRecorder()
		svc.local(w, req)
		return w
	}
	if local(`{"action":"list"}`, "8.8.8.8").Code != 403 {
		test.Fatal("untrusted LAN accepted")
	}
	if local(fmt.Sprintf(`{"action":"entitlement","remoteAccess":true,"expires":%d}`, time.Now().Add(time.Hour).Unix()), "172.16.104.40").Code != 200 {
		test.Fatal("entitlement")
	}
	w := local(`{"action":"invite","name":"Phone 1"}`, "172.16.104.40")
	var qr struct{ Vehicle, Invitation, Secret, Public string }
	if json.Unmarshal(w.Body.Bytes(), &qr) != nil || qr.Secret == "" {
		test.Fatal(w.Body.String())
	}
	phone, _ := t.GenerateKey()
	key, _ := t.Encoding.DecodeString(qr.Secret)
	envelope := t.Envelope{Vehicle: qr.Vehicle, Device: qr.Invitation, ID: t.Random()}
	request, _ := t.Seal(key, envelope, "request", map[string]any{"id": envelope.ID, "sentAt": time.Now().UnixMilli(), "public": t.Encoding.EncodeToString(phone.PublicKey().Bytes())})
	reply, err := svc.handle(request)
	if err != nil {
		test.Fatal(err)
	}
	var result map[string]string
	if t.Open(key, reply, "reply", &result) != nil || result["status"] != "paired" {
		test.Fatal(result)
	}
	serverPublic, _ := t.Encoding.DecodeString(qr.Public)
	shared, _ := t.Shared(phone.Bytes(), serverPublic)
	q := t.Envelope{Vehicle: qr.Vehicle, Device: result["device"], ID: t.Random()}
	q, _ = t.Seal(shared, q, "request", t.Request{ID: q.ID, SentAt: time.Now().UnixMilli(), Kind: "ping"})
	if _, err = svc.handle(q); err != nil {
		test.Fatal("phone key not accepted", err)
	}
	if strings.Contains(local(`{"action":"list"}`, "172.16.104.40").Body.String(), `"key"`) {
		test.Fatal("key leaked")
	}
	if local(fmt.Sprintf(`{"action":"rename","id":%q,"name":"Renamed"}`, result["device"]), "172.16.104.40").Code != 200 {
		test.Fatal("rename")
	}
	if local(fmt.Sprintf(`{"action":"delete","id":%q}`, result["device"]), "172.16.104.40").Code != 200 {
		test.Fatal("delete")
	}
	if len(s.State.Devices) != 0 {
		test.Fatal("device not removed")
	}
}
