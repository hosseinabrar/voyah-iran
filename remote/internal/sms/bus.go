package sms

import (
	"context"
	"encoding/xml"
	"errors"
	"github.com/godbus/dbus/v5"
	"log"
	"net"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

const destination = "gdbus.hcommon.rild"
const object = dbus.ObjectPath("/gdbus/hcommon/rild")
const iface = "gdbus.hcommon.rild.sms"

var ErrUnavailable = errors.New("SMS unavailable")
var ErrUnknown = errors.New("SMS delivery status unknown")
var ErrRejected = errors.New("SMS rejected")

type Bus struct {
	mu       sync.Mutex
	conn     *dbus.Conn
	sendGate chan struct{}
	store    *Store
}

func NewBus(s *Store) *Bus { return &Bus{store: s, sendGate: make(chan struct{}, 1)} }

// Both audited C and live X decoders use nibble+'0', not hexadecimal A-F.
func Encode(body string) (string, error) {
	if strings.TrimSpace(body) == "" || !utf8.ValidString(body) || len(body) > 1400 || utf8.RuneCountInString(body) > 500 {
		return "", ErrInvalid
	}
	for _, r := range body {
		if r == 0 || r > 0xffff {
			return "", ErrInvalid
		}
	} // OEM converts to UCS-2BE.
	b := make([]byte, 2*len(body))
	for i, v := range []byte(body) {
		b[2*i] = '0' + v>>4
		b[2*i+1] = '0' + v&15
	}
	return string(b), nil
}
func Recipient(v string) (string, error) {
	v = Sender(v)
	if len(v) < 3 || len(v) > 21 {
		return "", ErrInvalid
	}
	for i, r := range v {
		if r == '+' && i == 0 {
			continue
		}
		if r < '0' || r > '9' {
			return "", ErrInvalid
		}
	}
	return v, nil
}
func (b *Bus) Send(ctx context.Context, to, body string) error {
	to, e := Recipient(to)
	if e != nil {
		return e
	}
	wire, e := Encode(body)
	if e != nil {
		return e
	}
	select {
	case b.sendGate <- struct{}{}:
		defer func() { <-b.sendGate }()
	default:
		return ErrUnavailable
	}
	b.mu.Lock()
	c := b.conn
	b.mu.Unlock()
	if c == nil || !c.Connected() {
		return ErrUnavailable
	}
	ctx, cancel := context.WithTimeout(ctx, 6*time.Second)
	defer cancel()
	var ok bool
	e = c.Object(destination, object).CallWithContext(ctx, iface+".sendSms", 0, to, wire, int32(len(wire))).Store(&ok)
	if e != nil {
		return ErrUnknown
	}
	if !ok {
		return ErrRejected
	}
	return nil
}

type argument struct {
	Type      string `xml:"type,attr"`
	Direction string `xml:"direction,attr"`
}
type member struct {
	Name string     `xml:"name,attr"`
	Args []argument `xml:"arg"`
}

func compatible(doc string) bool {
	var node struct {
		Interfaces []struct {
			Name    string   `xml:"name,attr"`
			Methods []member `xml:"method"`
			Signals []member `xml:"signal"`
		} `xml:"interface"`
	}
	if xml.Unmarshal([]byte(doc), &node) != nil {
		return false
	}
	for _, i := range node.Interfaces {
		if i.Name != iface {
			continue
		}
		send, recv := false, false
		for _, m := range i.Methods {
			if m.Name != "sendSms" {
				continue
			}
			in, out := "", ""
			for _, a := range m.Args {
				if a.Direction == "out" {
					out += a.Type
				} else {
					in += a.Type
				}
			}
			send = in == "ssi" && out == "b"
		}
		for _, m := range i.Signals {
			if m.Name == "receiveSms" {
				sig := ""
				for _, a := range m.Args {
					sig += a.Type
				}
				recv = sig == "ssis"
			}
		}
		return send && recv
	}
	return false
}
func (b *Bus) session(ctx context.Context) error {
	dialer := net.Dialer{Timeout: 4 * time.Second}
	raw, e := dialer.DialContext(ctx, "unix", "/tmp/.hirain-dbus")
	if e != nil {
		return e
	}
	defer raw.Close()
	raw.SetDeadline(time.Now().Add(6 * time.Second))
	c, e := dbus.NewConn(raw, dbus.WithContext(ctx))
	if e != nil {
		return e
	}
	defer c.Close()
	if e = c.Auth(nil); e != nil {
		return e
	}
	if e = c.Hello(); e != nil {
		return e
	}
	signals := make(chan *dbus.Signal, 128)
	c.Signal(signals)
	defer c.RemoveSignal(signals)
	probe, cancel := context.WithTimeout(ctx, 4*time.Second)
	defer cancel()
	var doc string
	if e = c.Object(destination, object).CallWithContext(probe, "org.freedesktop.DBus.Introspectable.Introspect", 0).Store(&doc); e != nil {
		return e
	}
	if !compatible(doc) {
		return ErrUnavailable
	}
	if e = c.AddMatchSignal(dbus.WithMatchSender(destination), dbus.WithMatchObjectPath(object), dbus.WithMatchInterface(iface), dbus.WithMatchMember("receiveSms")); e != nil {
		return e
	}
	raw.SetDeadline(time.Time{})
	b.mu.Lock()
	b.conn = c
	b.mu.Unlock()
	defer func() { b.mu.Lock(); b.conn = nil; b.mu.Unlock() }()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-c.Context().Done():
			return ErrUnavailable
		case sig, ok := <-signals:
			if !ok {
				return ErrUnavailable
			}
			if sig == nil || sig.Name != iface+".receiveSms" || sig.Path != object || len(sig.Body) != 4 {
				continue
			}
			sender, a := sig.Body[0].(string)
			body, z := sig.Body[1].(string)
			length, k := sig.Body[2].(int32)
			stamp, d := sig.Body[3].(string)
			if !a || !z || !k || !d || length < 0 || int(length) != len(body) || len(stamp) > 64 {
				continue
			}
			if err := b.store.Receive(sender, body, stamp); err != nil {
				log.Print("SMS receive persistence failed")
			}
		}
	}
}
func (b *Bus) Run(ctx context.Context) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				if b.store.Cleanup() != nil {
					log.Print("SMS retention cleanup failed")
				}
			}
		}
	}()
	for ctx.Err() == nil {
		if b.session(ctx) != nil && ctx.Err() == nil {
			log.Print("SMS interface reconnecting")
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(5 * time.Second):
		}
	}
}
