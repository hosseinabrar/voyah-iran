package telemetry

import (
	"path/filepath"
	"testing"
	"time"
)

func TestEncryptionAndBinding(t *testing.T) {
	a, _ := GenerateKey()
	b, _ := GenerateKey()
	ka, _ := Shared(a.Bytes(), b.PublicKey().Bytes())
	kb, _ := Shared(b.Bytes(), a.PublicKey().Bytes())
	q := Envelope{Vehicle: Random(), Device: Random(), ID: Random()}
	p, e := Seal(ka, q, "request", Request{ID: q.ID, Kind: "ping"})
	if e != nil {
		t.Fatal(e)
	}
	var r Request
	if e = Open(kb, p, "request", &r); e != nil || r.ID != q.ID {
		t.Fatal(e)
	}
	if Open(kb, p, "reply", &r) == nil {
		t.Fatal("direction reflection")
	}
	p.Device = Random()
	if Open(kb, p, "request", &r) == nil {
		t.Fatal("device substitution")
	}
}
func TestPairLimitPersistenceAndExpiry(t *testing.T) {
	s, e := Load(filepath.Join(t.TempDir(), "state.json"))
	if e != nil {
		t.Fatal(e)
	}
	now := time.Now()
	if s.Allowed(now) {
		t.Fatal("default entitlement enabled")
	}
	s.State.RemoteAccess = true
	s.State.Expires = now.Add(time.Hour).Unix()
	phone, _ := GenerateKey()
	pub := Encoding.EncodeToString(phone.PublicKey().Bytes())
	for n := 0; n < 3; n++ {
		i, e := s.Invite("Phone", now)
		if e != nil {
			t.Fatal(e)
		}
		if _, e = s.Pair(i.ID, i.Secret, pub, now); e != nil {
			t.Fatal(e)
		}
		if _, e = s.Pair(i.ID, i.Secret, pub, now); e == nil {
			t.Fatal("reused invitation")
		}
	}
	if _, e = s.Invite("fourth", now); e == nil {
		t.Fatal("fourth phone")
	}
	r, e := Load(s.Path)
	if e != nil || len(r.State.Devices) != 3 {
		t.Fatal("persistence", e)
	}
	if r.Allowed(now.Add(2 * time.Hour)) {
		t.Fatal("expired entitlement")
	}
}
