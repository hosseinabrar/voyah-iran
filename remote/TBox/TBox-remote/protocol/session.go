package protocol

import (
	"crypto/sha256"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

type Session struct {
	mu         sync.Mutex
	Phone, Dir string
	lease      string
	until      time.Time
	grants     map[string]time.Time
}

func NormalizePhone(s string) string {
	s = strings.TrimSpace(s)
	s = strings.NewReplacer("۰", "0", "۱", "1", "۲", "2", "۳", "3", "۴", "4", "۵", "5", "۶", "6", "۷", "7", "۸", "8", "۹", "9", " ", "", "-", "", "(", "", ")", "").Replace(s)
	if strings.HasPrefix(s, "0098") {
		s = "+98" + s[4:]
	}
	if len(s) == 12 && strings.HasPrefix(s, "98") {
		s = "+" + s
	}
	if len(s) == 11 && strings.HasPrefix(s, "09") {
		s = "+98" + s[1:]
	}
	if len(s) != 13 || !strings.HasPrefix(s, "+989") {
		return ""
	}
	for _, c := range s[1:] {
		if c < '0' || c > '9' {
			return ""
		}
	}
	return s
}

// An exclusive, fsynced file is committed BEFORE any vehicle actuation. Restart
// never clears it. Unknown/corrupt storage fails closed; no retry executes twice.
func Record(dir, key string) error {
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	parent, err := os.Open(filepath.Dir(dir))
	if err != nil {
		return err
	}
	err = parent.Sync()
	parent.Close()
	if err != nil {
		return err
	}
	sum := sha256.Sum256([]byte(key))
	name := filepath.Join(dir, fmt.Sprintf("%x", sum))
	f, e := os.OpenFile(name, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if e != nil {
		return e
	}
	if _, e = f.WriteString(key + "\n"); e == nil {
		e = f.Sync()
	}
	ce := f.Close()
	if e != nil {
		return e
	}
	if ce != nil {
		return ce
	}
	d, e := os.Open(dir)
	if e != nil {
		return e
	}
	defer d.Close()
	return d.Sync()
}
func (s *Session) SMS(sender, event string, now time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if NormalizePhone(sender) == "" || NormalizePhone(sender) != NormalizePhone(s.Phone) {
		return errors.New("unpaired sender")
	}
	if event == "" {
		return errors.New("SMS identity missing")
	}
	if err := Record(filepath.Join(s.Dir, "sms"), event); err != nil {
		return err
	}
	s.lease = ID()
	s.until = now.Add(60 * time.Second)
	s.grants = map[string]time.Time{}
	return nil
}
func (s *Session) Active(now time.Time) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return now.Before(s.until)
}
func (s *Session) Ping(id string, now time.Time) (Message, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !now.Before(s.until) {
		return Message{}, errors.New("asleep")
	}
	if _, exists := s.grants[id]; exists {
		return Message{}, errors.New("duplicate heartbeat")
	}
	if len(s.grants) >= 2048 {
		return Message{}, errors.New("heartbeat limit")
	}
	s.grants[id] = now
	return Message{ID: id, Kind: "status", Lease: s.lease, Grant: id, Remaining: s.until.Sub(now).Milliseconds(), Status: "connected"}, nil
}
func (s *Session) Accept(m Message, now time.Time) (time.Time, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !now.Before(s.until) || m.Lease != s.lease {
		return time.Time{}, errors.New("inactive lease")
	}
	t, ok := s.grants[m.Grant]
	if !ok || now.Sub(t) >= 10*time.Second {
		return time.Time{}, errors.New("expired command grant")
	}
	if !Known(m.Command) {
		return time.Time{}, errors.New("unknown command")
	}
	if err := Record(filepath.Join(s.Dir, "commands"), m.ID); err != nil {
		return time.Time{}, errors.New("duplicate UUID or unavailable replay storage")
	}
	end := t.Add(10 * time.Second)
	if s.until.Before(end) {
		end = s.until
	}
	return end, nil
}
