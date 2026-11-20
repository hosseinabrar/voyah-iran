package sms

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func inbox(t *testing.T) *Store {
	t.Helper()
	s, e := Load(filepath.Join(t.TempDir(), "messages.json"))
	if e != nil {
		t.Fatal(e)
	}
	s.now = func() time.Time { return time.Date(2026, 11, 18, 14, 32, 0, 0, time.UTC) }
	return s
}
func query(t *testing.T, s *Store, a, id, to, c string) *Result {
	t.Helper()
	r, e := s.Query(a, id, to, c)
	if e != nil {
		t.Fatal(e)
	}
	return r
}
func TestPersistDeleteBlockRetention(t *testing.T) {
	s := inbox(t)
	now := s.now()
	if e := s.Receive("۰۹۱۲۱۲۳۴۵۶۷", "کد شما: 482731", now.Format(time.RFC3339)); e != nil {
		t.Fatal(e)
	}
	r := query(t, s, "list", "", "", "")
	if len(r.Messages) != 1 || r.Messages[0].Sender != "+989121234567" {
		t.Fatal(r)
	}
	id := r.Messages[0].ID
	query(t, s, "delete", id, "", "")
	s.Receive("+98 912 1234567", "کد شما: 482731", now.Format(time.RFC3339))
	if len(query(t, s, "list", "", "", "").Messages) != 0 {
		t.Fatal("deleted duplicate returned")
	}
	query(t, s, "block", "", "10001234", "")
	s.Receive("10001234", "blocked secret", now.Format(time.RFC3339))
	raw, _ := os.ReadFile(s.path)
	if strings.Contains(string(raw), "blocked secret") {
		t.Fatal("blocked content persisted")
	}
	query(t, s, "unblock", "", "10001234", "")
	s.Receive("10001234", "allowed", now.Format(time.RFC3339))
	s.Receive("Service", "old", now.Add(-31*24*time.Hour).Format(time.RFC3339))
	if len(query(t, s, "list", "", "", "").Messages) != 1 {
		t.Fatal("old retained")
	}
	again, e := Load(s.path)
	if e != nil {
		t.Fatal(e)
	}
	again.now = s.now
	if len(query(t, again, "list", "", "", "").Messages) != 1 {
		t.Fatal("persistence")
	}
	info, _ := os.Stat(s.path)
	if info.Mode().Perm() != 0600 {
		t.Fatal(info.Mode())
	}
	s.now = func() time.Time { return now.Add(31 * 24 * time.Hour) }
	if e = s.Cleanup(); e != nil {
		t.Fatal(e)
	}
	raw, _ = os.ReadFile(s.path)
	if strings.Contains(string(raw), "allowed") {
		t.Fatal("expiry did not purge disk")
	}
}
func TestBlockRemovesExistingAndSurvivesReload(t *testing.T) {
	s := inbox(t)
	s.Receive("Service", "x", "")
	query(t, s, "block", "", "SERVICE", "")
	if len(query(t, s, "list", "", "", "").Messages) != 0 {
		t.Fatal("existing blocked message retained")
	}
	again, e := Load(s.path)
	if e != nil {
		t.Fatal(e)
	}
	again.Receive("Service", "y", "")
	r := query(t, again, "list", "", "", "")
	if len(r.Messages) != 0 || len(query(t, again, "blocked", "", "", "").Blocked) != 1 {
		t.Fatal(r)
	}
}
func TestPageBoundariesAndWireSize(t *testing.T) {
	s := inbox(t)
	now := s.now()
	for i := 0; i < 43; i++ {
		s.Receive("1000", strings.Repeat("سلام", 400), now.Add(time.Duration(-i)*time.Minute).Format(time.RFC3339))
	}
	r := query(t, s, "list", "", "", "")
	if len(r.Messages) != 20 || r.Next == "" {
		t.Fatal(r)
	}
	p := query(t, s, "list", "", "", r.Next)
	last := query(t, s, "list", "", "", p.Next)
	if len(p.Messages) != 20 || len(last.Messages) != 3 || last.Next != "" {
		t.Fatal("pagination")
	}
	b, _ := json.Marshal(r)
	if len(b) > 13000 {
		t.Fatal("encrypted envelope too large", len(b))
	}
	m := query(t, s, "get", r.Messages[0].ID, "", "").Message
	if len(m.Body) != 3200 {
		t.Fatal("detail truncated", len(m.Body))
	}
}
func TestWriteFailureDoesNotAcknowledge(t *testing.T) {
	s := inbox(t)
	s.path = filepath.Join(s.path, "not-a-directory")
	if _, e := s.Query("block", "", "123", ""); e == nil {
		t.Fatal("write failure acknowledged")
	}
	if len(s.data.Blocked) != 0 {
		t.Fatal("in-memory mutation leaked")
	}
}
func TestGSMTime(t *testing.T) {
	n := time.Date(2026, 11, 18, 11, 2, 0, 0, time.UTC)
	if got := messageTime("26/11/18,14:32:00+14", n); !got.Equal(n) {
		t.Fatal(got)
	}
	if got := messageTime("broken", n); !got.Equal(n) {
		t.Fatal(got)
	}
}
func TestConcurrentDelivery(t *testing.T) {
	s := inbox(t)
	done := make(chan error, 30)
	for i := 0; i < 30; i++ {
		go func(i int) { done <- s.Receive("1000", fmt.Sprint(i), "") }(i)
	}
	for i := 0; i < 30; i++ {
		if e := <-done; e != nil {
			t.Fatal(e)
		}
	}
	if len(s.data.Messages) != 30 {
		t.Fatal(len(s.data.Messages))
	}
}
