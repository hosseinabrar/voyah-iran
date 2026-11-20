package main

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
	"voyah/remote/internal/sms"
	t "voyah/remote/internal/telemetry"
)

func TestEncryptedSMSAuthorizationReplayAndNoSentStorage(test *testing.T) {
	dir := test.TempDir()
	store, _ := t.Load(filepath.Join(dir, "state.json"))
	inbox, e := sms.Load(filepath.Join(dir, "messages.json"))
	if e != nil {
		test.Fatal(e)
	}
	key := make([]byte, 32)
	device := t.Device{ID: t.Random(), Key: t.Encoding.EncodeToString(key)}
	store.State.Devices = []t.Device{device}
	calls := 0
	svc := service{store: store, messages: inbox, wake: func() { test.Fatal("SMS woke CAN") }, smsSend: func(ctx context.Context, to, body string) error {
		calls++
		if to != "09121234567" || body != "private outgoing" {
			test.Fatal("payload altered")
		}
		return nil
	}}
	request := func(action string) t.Envelope {
		q := t.Envelope{Vehicle: store.State.Vehicle, Device: device.ID, ID: t.Random()}
		sealed, e := t.Seal(key, q, "request", t.Request{ID: q.ID, Kind: "sms", Action: action, Recipient: "09121234567", Body: "private outgoing", SentAt: time.Now().UnixMilli()})
		if e != nil {
			test.Fatal(e)
		}
		return sealed
	}
	exchange := func(q t.Envelope) t.Reply {
		a, e := svc.handle(q)
		if e != nil {
			test.Fatal(e)
		}
		var r t.Reply
		if e = t.Open(key, a, "reply", &r); e != nil {
			test.Fatal(e)
		}
		return r
	}
	if r := exchange(request("send")); r.Status != "subscription_expired" || calls != 0 {
		test.Fatal(r, calls)
	}
	store.State.RemoteAccess = true
	store.State.Expires = time.Now().Add(time.Hour).Unix()
	q := request("send")
	if r := exchange(q); r.Status != "ok" || calls != 1 {
		test.Fatal(r, calls)
	}
	if _, e = svc.handle(q); e == nil || calls != 1 {
		test.Fatal("SMS replay sent twice")
	}
	b, _ := os.ReadFile(filepath.Join(dir, "messages.json"))
	if strings.Contains(string(b), "private outgoing") {
		test.Fatal("sent SMS stored")
	}
	if r := exchange(request("list")); r.SMS == nil || len(r.SMS.Messages) != 0 {
		test.Fatal(r)
	}
	store.State.Devices = nil
	if _, e = svc.handle(request("send")); e == nil || calls != 1 {
		test.Fatal("revoked device sent SMS")
	}
}
