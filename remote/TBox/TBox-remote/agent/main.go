package main

import (
	"context"
	"crypto/sha256"
	_ "embed"
	"encoding/binary"
	"flag"
	"fmt"
	"github.com/godbus/dbus/v5"
	"io"
	"log"
	"net"
	"os"

	"sync"
	"time"
	p "voyah/remote/TBox/TBox-remote/protocol"
)

//go:embed pairing.local.json
var pairData []byte

type agent struct {
	pair       p.Pairing
	session    *p.Session
	busy       chan struct{}
	simulation bool
	wake       chan struct{}
}

func (a *agent) reply(m p.Message) {
	q, e := p.Seal(a.pair, m)
	if e != nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if e = p.Call(ctx, a.pair, "/v1/reply", q, nil); e != nil {
		log.Print("Reply delivery failed: ", e)
	}
}
func (a *agent) handle(q p.Packet) {
	m, e := p.Open(a.pair, q)
	if e != nil {
		return
	}
	if m.Kind == "ping" {
		v, e := a.session.Ping(m.ID, leaseNow())
		if e == nil {
			if a.simulation {
				v.Status = "simulation"
			}
			a.reply(v)
		}
		return
	}
	if m.Kind != "command" {
		return
	}
	result := p.Message{ID: m.ID, Kind: "result", Status: "rejected"}
	select {
	case a.busy <- struct{}{}:
		defer func() { <-a.busy }()
	default:
		result.Detail = "Another command is in progress"
		a.reply(result)
		return
	}
	end, e := a.session.Accept(m, leaseNow())
	if e != nil {
		result.Detail = e.Error()
		a.reply(result)
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), end.Sub(leaseNow()))
	defer cancel()
	if a.simulation {
		result.Status = "simulated"
		result.Detail = "Simulation only; no vehicle action"
	} else {
		result.Status, result.Detail = execute(ctx, m.Command)
	}
	a.reply(result)
}
func (a *agent) run() {
	for {
		if !a.session.Active(leaseNow()) {
			<-a.wake
			continue
		}
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		var qs []p.Packet
		e := p.Call(ctx, a.pair, "/v1/poll", nil, &qs)
		cancel()
		if e == nil {
			for _, q := range qs {
				go a.handle(q)
			}
		}
		time.Sleep(250 * time.Millisecond)
	}
}
func (a *agent) sms() {
	for {
		conn, e := dbus.Connect("unix:path=/tmp/.hirain-dbus")
		if e != nil {
			log.Print("SMS bus unavailable; retrying")
			time.Sleep(5 * time.Second)
			continue
		}
		signals := make(chan *dbus.Signal, 16)
		conn.Signal(signals)
		e = conn.AddMatchSignal(dbus.WithMatchSender("gdbus.hcommon.rild"), dbus.WithMatchObjectPath("/gdbus/hcommon/rild"), dbus.WithMatchInterface("gdbus.hcommon.rild.sms"), dbus.WithMatchMember("receiveSms"))
		if e != nil {
			conn.Close()
			time.Sleep(5 * time.Second)
			continue
		}
		log.Print("Listening for OEM SMS notifications")
	watch:
		for {
			var sig *dbus.Signal
			select {
			case <-conn.Context().Done():
				break watch
			case sig = <-signals:
			}
			if sig == nil {
				break watch
			}
			if sig.Name != "gdbus.hcommon.rild.sms.receiveSms" || len(sig.Body) != 4 {
				continue
			}
			sender, ok := sig.Body[0].(string)
			content, ok2 := sig.Body[1].(string)
			stamp, ok3 := sig.Body[3].(string)
			if !ok || !ok2 || !ok3 || stamp == "" {
				continue
			}
			id := fmt.Sprintf("%x", sha256.Sum256([]byte(p.NormalizePhone(sender)+"|"+stamp+"|"+content)))
			if e = a.session.SMS(sender, id, leaseNow()); e == nil {
				wake60()
				select {
				case a.wake <- struct{}{}:
				default:
				}
				log.Print("Paired SMS received: 60-second window opened")
			}
		}
		conn.Close()
		time.Sleep(time.Second)
	}
}
func wake60() { // Timed kernel wakelock, if exposed; no permanent wake lock.
	if e := os.WriteFile("/sys/power/wake_lock", []byte("voyah_remote 60000000000"), 0600); e != nil {
		log.Print("Timed kernel wakelock unavailable; validate suspend behavior on vehicle")
	}
}

var benchDiagnostics bool

func main() {
	flag.BoolVar(&benchDiagnostics, "bench-diagnostics", false, "report temporary bench adapter stages; not OEM completion")
	dir := flag.String("state", "/usr/local/lib/TBox-remote/state", "durable replay state")
	testOpen := flag.Bool("test-no-sms", false, "temporary paired bench test; renews SMS lease automatically")
	sim := flag.Bool("simulate", false, "explicit host-only simulation; never uses hardware")
	url := flag.String("gateway", "", "local simulation override")
	flag.Parse()
	pair, e := p.Load(pairData)
	if e != nil {
		log.Fatal(e)
	}
	if *url != "" {
		pair.URL = *url
	}
	if e = os.MkdirAll(*dir, 0700); e != nil {
		log.Fatal(e)
	}
	s := &p.Session{Phone: pair.Phone, Dir: *dir}
	a := &agent{pair: pair, session: s, busy: make(chan struct{}, 1), simulation: *sim, wake: make(chan struct{}, 1)}
	if *testOpen && !*sim {
		log.Print("TEST MODE: SMS gate temporarily disabled; encrypted pairing remains required")
		if e = s.SMS(pair.Phone, p.ID(), leaseNow()); e != nil {
			log.Fatal(e)
		}
		wake60()
		go func() {
			for {
				time.Sleep(45 * time.Second)
				if e := s.SMS(pair.Phone, p.ID(), leaseNow()); e != nil {
					log.Print(e)
					continue
				}
				wake60()
				select {
				case a.wake <- struct{}{}:
				default:
				}
			}
		}()
	}
	if *sim {
		log.Print("SIMULATION: creating one 60-second SMS lease")
		if e = s.SMS(pair.Phone, p.ID(), leaseNow()); e != nil {
			log.Fatal(e)
		}
	} else {
		go a.sms()
	}
	a.run()
}

var serialMu sync.Mutex

func execute(ctx context.Context, id string) (string, string) {
	var t, v uint64
	if _, e := fmt.Sscanf(id, "%d:%d", &t, &v); e != nil {
		return "rejected", "Invalid command"
	}
	serialMu.Lock()
	defer serialMu.Unlock()
	var raw [4]byte
	f, e := os.Open("/dev/urandom")
	if e != nil {
		return "failed", e.Error()
	}
	_, e = io.ReadFull(f, raw[:])
	f.Close()
	if e != nil {
		return "failed", e.Error()
	}
	serial := uint64(binary.LittleEndian.Uint32(raw[:])&0x7fffffff) + 1
	data := append(field(1, serial), field(2, t)...)
	data = append(data, field(3, v)...)
	// Preserve factory parsers and their checks. Required extension messages are
	// encoded explicitly; optional fields retain OEM defaults.
	ext := extension(t, v, time.Now())
	if len(ext) > 0 {
		data = append(data, bytesField(4, ext)...)
	}
	d := net.Dialer{}
	c, e := d.DialContext(ctx, "unix", "/run/TBox-remote-oem.sock")
	if e != nil {
		return "failed", "OEM bridge unavailable: " + e.Error()
	}
	defer c.Close()
	deadline, _ := ctx.Deadline()
	c.SetDeadline(deadline)
	ms := time.Until(deadline).Milliseconds()
	if ms <= 0 {
		return "timeout", "Command expired before dispatch"
	}
	header := make([]byte, 8)
	binary.LittleEndian.PutUint32(header, uint32(len(data)))
	binary.LittleEndian.PutUint32(header[4:], uint32(ms))
	if _, e = c.Write(append(header, data...)); e != nil {
		return "failed", e.Error()
	}
	if _, e = io.ReadFull(c, raw[:]); e != nil {
		return "timeout", "No confirmed OEM result; operation may still be in progress"
	}
	n := binary.LittleEndian.Uint32(raw[:])
	if n > 65536 {
		return "failed", "Invalid OEM response length"
	}
	reply := make([]byte, n)
	if _, e = io.ReadFull(c, reply); e != nil {
		return "failed", e.Error()
	}
	fields, e := parse(reply)
	if e != nil || fields[1] != serial || fields[2] != t {
		return "failed", "Unmatched OEM result"
	}
	result, ok := fields[3]
	if !ok {
		return "failed", "OEM result has no completion status"
	}
	if benchDiagnostics {
		details := map[uint64]string{
			250: "Bench: unsupported command; nothing sent",
			251: "Bench: insufficient remaining time; nothing sent",
			252: "Bench: requested cached state observed; physical execution still needs verification",
			253: "Bench: probe refused or failed before submission; inspect TBox diagnostic log",
			254: "Bench: submitted to VNS; no MCU result observed; execution unconfirmed",
			255: "Bench: MCU result received; this is not execution confirmation; requested state unconfirmed",
		}
		if detail, ok := details[fields[4]]; ok && result != 0 {
			return "unconfirmed", detail
		}
	}
	if result == 0 {
		if t == 33 && v == 1 {
			return "accepted", "Factory service accepted the climate schedule (starts in 1 minute, runs 10 minutes)"
		}
		return "completed", "Factory service confirmed completion"
	}
	return "failed", fmt.Sprintf("Factory result %d (error %d)", result, fields[4])
}
func varint(v uint64) []byte {
	var b [10]byte
	n := binary.PutUvarint(b[:], v)
	return append([]byte(nil), b[:n]...)
}
func field(n, v uint64) []byte { return append(varint(n<<3), varint(v)...) }
func parse(b []byte) (map[uint64]uint64, error) {
	m := map[uint64]uint64{}
	for len(b) > 0 {
		if len(b) == 1 && b[0] == 0 {
			break
		}
		tag, n := binary.Uvarint(b)
		if n <= 0 {
			return nil, fmt.Errorf("invalid tag")
		}
		b = b[n:]
		switch tag & 7 {
		case 0:
			v, k := binary.Uvarint(b)
			if k <= 0 {
				return nil, fmt.Errorf("invalid value")
			}
			m[tag>>3] = v
			b = b[k:]
		case 2:
			v, k := binary.Uvarint(b)
			if k <= 0 || v > uint64(len(b)-k) {
				return nil, fmt.Errorf("invalid bytes")
			}
			b = b[k+int(v):]
		default:
			return nil, fmt.Errorf("unsupported field")
		}
	}
	return m, nil
}

func bytesField(n uint64, b []byte) []byte {
	out := varint(n<<3 | 2)
	out = append(out, varint(uint64(len(b)))...)
	return append(out, b...)
}
func extension(t, v uint64, now time.Time) []byte {
	var b []byte
	add := func(n, x uint64) { b = append(b, field(n, x)...) }
	switch t {
	case 33:
		add(1, v)
		add(4, 10)
		add(5, v)
		if v == 1 {
			when := now.Add(time.Minute)
			var date []byte
			for i, x := range []int{when.Year(), int(when.Month()), when.Day(), when.Hour(), when.Minute(), when.Second()} {
				date = append(date, field(uint64(i+1), uint64(x))...)
			}
			b = append(b, bytesField(6, date)...)
		}
	case 48:
		add(1, v)
		add(2, 10)
		if v == 1 {
			add(3, 1)
			for n := uint64(4); n <= 7; n++ {
				add(n, 3)
			}
		}
	case 49:
		add(3, v)
		add(4, 10)
		if v == 1 {
			add(5, 1)
			for n := uint64(6); n <= 9; n++ {
				add(n, 3)
			}
			add(10, 1)
		}
	case 52:
		on := uint64(0)
		if v != 0 {
			on = 1
		}
		add(1, on)
		add(2, 0)
		bat, ac := uint64(1), uint64(1)
		if v&16 != 0 {
			bat = 2
		}
		if v&1 != 0 {
			ac = 2
		}
		add(3, bat)
		add(4, 10)
		add(5, ac)
		add(6, 10)
	}
	return b
}
