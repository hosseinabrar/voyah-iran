package protocol

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func keys(t *testing.T) (Pairing, Pairing) {
	t.Helper()
	a, _ := ecdh.X25519().GenerateKey(rand.Reader)
	b, _ := ecdh.X25519().GenerateKey(rand.Reader)
	enc := base64.StdEncoding.EncodeToString
	return Pairing{Private: enc(a.Bytes()), Peer: enc(b.PublicKey().Bytes()), Role: "client"}, Pairing{Private: enc(b.Bytes()), Peer: enc(a.PublicKey().Bytes()), Role: "agent"}
}
func TestAuthenticatedEncryption(t *testing.T) {
	c, a := keys(t)
	m := Message{ID: ID(), Kind: "command", Command: "10:1"}
	q, e := Seal(c, m)
	if e != nil {
		t.Fatal(e)
	}
	got, e := Open(a, q)
	if e != nil || got.Command != m.Command {
		t.Fatal(got, e)
	}
	if _, e = Open(c, q); e == nil {
		t.Fatal("reflected packet accepted")
	}
	q.ID = ID()
	if _, e = Open(a, q); e == nil {
		t.Fatal("changed UUID accepted")
	}
}
func TestWrongKeyAndTamper(t *testing.T) {
	c, a := keys(t)
	_, wrong := keys(t)
	q, _ := Seal(c, Message{ID: ID(), Kind: "ping"})
	if _, e := Open(wrong, q); e == nil {
		t.Fatal("wrong key accepted")
	}
	b, _ := base64.StdEncoding.DecodeString(q.Data)
	b[len(b)-1] ^= 1
	q.Data = base64.StdEncoding.EncodeToString(b)
	if _, e := Open(a, q); e == nil {
		t.Fatal("tamper accepted")
	}
}
func TestPhoneNormalization(t *testing.T) {
	for _, n := range []string{"09358624411", "+989358624411", "00989358624411", "989358624411", "۰۹۳۵۸۶۲۴۴۱۱", "+98 (935) 862-4411"} {
		if NormalizePhone(n) != "+989358624411" {
			t.Fatal(n)
		}
	}
	for _, n := range []string{"", "9358624411", "009893586244110", "x09358624411"} {
		if NormalizePhone(n) != "" {
			t.Fatal(n)
		}
	}
}
func TestLeaseAndReplay(t *testing.T) {
	now := time.Now()
	s := &Session{Phone: "09358624411", Dir: t.TempDir()}
	if s.Active(now) {
		t.Fatal("starts awake")
	}
	if s.SMS("09358624412", "x", now) == nil {
		t.Fatal("wrong sender")
	}
	if e := s.SMS("+989358624411", "sms1", now); e != nil {
		t.Fatal(e)
	}
	ping, _ := s.Ping(ID(), now)
	m := Message{ID: ID(), Kind: "command", Command: "10:1", Lease: ping.Lease, Grant: ping.Grant}
	if _, e := s.Accept(m, now); e != nil {
		t.Fatal(e)
	}
	if _, e := s.Accept(m, now); e == nil {
		t.Fatal("replay accepted")
	}
	if s.SMS("09358624411", "sms1", now.Add(30*time.Second)) == nil {
		t.Fatal("SMS replay renewed lease")
	}
	if s.Active(now.Add(60 * time.Second)) {
		t.Fatal("lease exceeds 60 seconds")
	}
	restarted := &Session{Phone: s.Phone, Dir: s.Dir}
	if restarted.Active(now) {
		t.Fatal("restart reopens lease")
	}
	if restarted.SMS(s.Phone, "sms1", now) == nil {
		t.Fatal("restart lost SMS journal")
	}
}
func TestStaleGrantAndOldLease(t *testing.T) {
	now := time.Now()
	s := &Session{Phone: "09358624411", Dir: t.TempDir()}
	s.SMS(s.Phone, "first", now)
	g, _ := s.Ping(ID(), now)
	m := Message{ID: ID(), Command: "10:0", Lease: g.Lease, Grant: g.Grant}
	if _, e := s.Accept(m, now.Add(10*time.Second)); e == nil {
		t.Fatal("stale queued command accepted")
	}
	s.SMS(s.Phone, "second", now.Add(time.Second))
	if _, e := s.Accept(m, now.Add(time.Second)); e == nil {
		t.Fatal("old lease accepted")
	}
}
func TestStorageFailureFailsClosed(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "not-a-dir")
	os.WriteFile(file, []byte("x"), 0600)
	s := &Session{Phone: "09358624411", Dir: file}
	if s.SMS(s.Phone, "first", time.Now()) == nil {
		t.Fatal("storage failure ignored")
	}
}
func TestUUIDFormat(t *testing.T) {
	for i := 0; i < 100; i++ {
		if !ValidID(ID()) {
			t.Fatal("bad uuid")
		}
	}
}

func TestReplayedHeartbeatCannotRefreshGrant(t *testing.T) {
	now := time.Now()
	s := &Session{Phone: "09358624411", Dir: t.TempDir()}
	s.SMS(s.Phone, "first", now)
	id := ID()
	s.Ping(id, now)
	if _, e := s.Ping(id, now.Add(20*time.Second)); e == nil {
		t.Fatal("relay replay renewed command grant")
	}
}
