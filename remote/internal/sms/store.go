// Package sms owns the received inbox. Outgoing messages are never persisted.
package sms

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"
)

const Retention = 30 * 24 * time.Hour

var ErrInvalid = errors.New("invalid SMS request")

type Message struct {
	ID         string `json:"id"`
	Sender     string `json:"sender"`
	Body       string `json:"body"`
	At         int64  `json:"at"`
	ReceivedAt int64  `json:"receivedAt"`
}
type Result struct {
	ObservedAt int64     `json:"observedAt"`
	Messages   []Message `json:"messages,omitempty"`
	Message    *Message  `json:"message,omitempty"`
	Blocked    []string  `json:"blocked"`
	Next       string    `json:"next,omitempty"`
}
type disk struct {
	Messages []Message        `json:"messages"`
	Blocked  []string         `json:"blocked"`
	Seen     map[string]int64 `json:"seen"`
}
type Store struct {
	mu   sync.Mutex
	path string
	data disk
	now  func() time.Time
}

func Load(path string) (*Store, error) {
	s := &Store{path: path, now: time.Now, data: disk{Seen: map[string]int64{}, Blocked: []string{}}}
	b, e := os.ReadFile(path)
	if e == nil {
		if e = json.Unmarshal(b, &s.data); e != nil {
			return nil, e
		}
	} else if !os.IsNotExist(e) {
		return nil, e
	}
	if s.data.Seen == nil {
		s.data.Seen = map[string]int64{}
	}
	if s.data.Blocked == nil {
		s.data.Blocked = []string{}
	}
	if e = s.change(func() error { s.prune(); return nil }); e != nil {
		return nil, e
	}
	return s, nil
}
func Digits(v string) string {
	return strings.Map(func(r rune) rune {
		if r >= '۰' && r <= '۹' {
			return r - '۰' + '0'
		}
		if r >= '٠' && r <= '٩' {
			return r - '٠' + '0'
		}
		return r
	}, v)
}
func Sender(v string) string {
	v = strings.TrimSpace(Digits(v))
	if len(v) > 64 || !utf8.ValidString(v) {
		return ""
	}
	numeric := true
	for _, r := range v {
		if !strings.ContainsRune("0123456789+- ()", r) {
			numeric = false
		}
	}
	if numeric {
		v = strings.Map(func(r rune) rune {
			if r == ' ' || r == '-' || r == '(' || r == ')' {
				return -1
			}
			return r
		}, v)
		if strings.HasPrefix(v, "0098") {
			v = "+98" + v[4:]
		}
		if len(v) == 11 && strings.HasPrefix(v, "09") {
			v = "+98" + v[1:]
		}
	}
	for _, r := range v {
		if unicode.IsControl(r) {
			return ""
		}
	}
	return strings.ToLower(v)
}
func (s *Store) prune() {
	cut := s.now().Add(-Retention).UnixMilli()
	out := s.data.Messages[:0]
	for _, m := range s.data.Messages {
		if m.ReceivedAt > cut && m.At > cut {
			out = append(out, m)
		}
	}
	s.data.Messages = out
	for id, at := range s.data.Seen {
		if at <= cut {
			delete(s.data.Seen, id)
		}
	}
}

// Persist before publishing the new state; a failed write leaves the old state intact.
func (s *Store) change(fn func() error) error {
	old, _ := json.Marshal(s.data)
	if e := fn(); e != nil {
		json.Unmarshal(old, &s.data)
		return e
	}
	b, e := json.Marshal(s.data)
	if e == nil && string(old) == string(b) {
		if _, err := os.Stat(s.path); err == nil {
			return nil
		}
	}
	if e == nil {
		e = os.MkdirAll(filepath.Dir(s.path), 0700)
	}
	var f *os.File
	if e == nil {
		f, e = os.CreateTemp(filepath.Dir(s.path), ".sms-*")
	}
	if e == nil {
		defer os.Remove(f.Name())
		e = f.Chmod(0600)
		if e == nil {
			_, e = f.Write(b)
		}
		if e == nil {
			e = f.Sync()
		}
		ce := f.Close()
		if e == nil {
			e = ce
		}
		if e == nil {
			e = os.Rename(f.Name(), s.path)
		}
	}
	if e != nil {
		json.Unmarshal(old, &s.data)
		return e
	}
	if dir, err := os.Open(filepath.Dir(s.path)); err == nil {
		dir.Sync()
		dir.Close()
	}
	return nil
}
func (s *Store) Cleanup() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.change(func() error { s.prune(); return nil })
}
func (s *Store) Receive(sender, body, stamp string) error {
	sender = Sender(sender)
	if sender == "" || len(body) > 6000 || !utf8.ValidString(body) || strings.ContainsRune(body, 0) {
		return ErrInvalid
	}
	for _, r := range body {
		if unicode.IsControl(r) && r != '\n' && r != '\r' && r != '\t' {
			return ErrInvalid
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.change(func() error {
		s.prune()
		for _, b := range s.data.Blocked {
			if b == sender {
				return nil
			}
		}
		sum := sha256.Sum256([]byte(sender + "\x00" + stamp + "\x00" + body))
		id := hex.EncodeToString(sum[:])
		if _, ok := s.data.Seen[id]; ok {
			return nil
		}
		now := s.now()
		at := messageTime(stamp, now)
		if at.Before(now.Add(-Retention)) {
			return nil
		}
		s.data.Seen[id] = now.UnixMilli()
		s.data.Messages = append(s.data.Messages, Message{id, sender, body, at.UnixMilli(), now.UnixMilli()})
		sort.Slice(s.data.Messages, func(i, j int) bool {
			a, b := s.data.Messages[i], s.data.Messages[j]
			if a.At == b.At {
				return a.ID > b.ID
			}
			return a.At > b.At
		})
		if len(s.data.Messages) > 1000 {
			s.data.Messages = s.data.Messages[:1000]
		}
		// Bound replay history even if a modem produces an abnormal event flood.
		if len(s.data.Seen) > 4000 {
			var oldest string
			var at int64 = 1<<63 - 1
			for k, v := range s.data.Seen {
				if v < at {
					oldest, at = k, v
				}
			}
			delete(s.data.Seen, oldest)
		}
		return nil
	})
}
func messageTime(v string, now time.Time) time.Time {
	if t, e := time.Parse(time.RFC3339, v); e == nil && t.Before(now.Add(5*time.Minute)) {
		return t
	}
	// OEM GSM timestamp timezone is expressed in quarters of an hour.
	if len(v) >= 18 {
		base, e := time.Parse("06/01/02,15:04:05", v[:17])
		if e == nil {
			var sign int = 1
			tail := v[17:]
			if strings.HasPrefix(tail, "-") {
				sign = -1
			}
			tail = strings.TrimLeft(tail, "+-")
			n := 0
			ok := tail != ""
			for _, r := range tail {
				if r < '0' || r > '9' {
					ok = false
					break
				}
				n = n*10 + int(r-'0')
			}
			if ok && n <= 56 {
				base = base.Add(-time.Duration(sign*n) * 15 * time.Minute)
				if base.Before(now.Add(5 * time.Minute)) {
					return base
				}
			}
		}
	}
	return now
}
func (s *Store) Query(action, id, sender, cursor string) (*Result, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	var selected *Message
	e := s.change(func() error {
		s.prune()
		switch action {
		case "list", "blocked":
		case "get":
			for _, m := range s.data.Messages {
				if m.ID == id {
					copy := m
					selected = &copy
					break
				}
			}
			if selected == nil {
				return os.ErrNotExist
			}
		case "delete":
			out := s.data.Messages[:0]
			for _, m := range s.data.Messages {
				if m.ID != id {
					out = append(out, m)
				}
			}
			s.data.Messages = out
		case "block", "unblock":
			sender = Sender(sender)
			if sender == "" {
				return ErrInvalid
			}
			out := []string{}
			for _, v := range s.data.Blocked {
				if v != sender {
					out = append(out, v)
				}
			}
			if action == "block" {
				if len(out) >= 128 {
					return ErrInvalid
				}
				out = append(out, sender)
				msgs := s.data.Messages[:0]
				for _, m := range s.data.Messages {
					if m.Sender != sender {
						msgs = append(msgs, m)
					}
				}
				s.data.Messages = msgs
			}
			sort.Strings(out)
			s.data.Blocked = out
		default:
			return ErrInvalid
		}
		return nil
	})
	if e != nil {
		return nil, e
	}
	r := &Result{ObservedAt: s.now().UnixMilli(), Message: selected}
	if action == "blocked" || action == "block" || action == "unblock" {
		r.Blocked = append([]string{}, s.data.Blocked...)
	}
	if action == "list" {
		start := 0
		if cursor != "" {
			start = len(s.data.Messages)
			for i, m := range s.data.Messages {
				if m.ID == cursor {
					start = i + 1
					break
				}
			}
		}
		end := start + 20
		if end > len(s.data.Messages) {
			end = len(s.data.Messages)
		}
		r.Messages = []Message{}
		for _, m := range s.data.Messages[start:end] {
			rr := []rune(m.Body)
			if len(rr) > 80 {
				m.Body = string(rr[:80]) + "…"
			}
			r.Messages = append(r.Messages, m)
		}
		if end < len(s.data.Messages) && end > start {
			r.Next = s.data.Messages[end-1].ID
		}
	}
	return r, nil
}
