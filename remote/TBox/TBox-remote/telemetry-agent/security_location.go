package main

// OEM locationd lives on the TBox. Never use Android/phone coordinates or
// refresh a fix timestamp merely because the gateway delivered a response.
import (
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"github.com/godbus/dbus/v5"
	"math"
	"net"
	"strconv"
	"strings"
	"time"
)

const locationBus = "gdbus.hcommon.location"
const locationPath = dbus.ObjectPath("/gdbus/hcommon/location")

func parseRMC(sentence string, now time.Time) *vehicleLocation {
	sentence = strings.TrimSpace(sentence)
	if len(sentence) > 256 || len(sentence) < 10 || sentence[0] != '$' {
		return nil
	}
	star := strings.LastIndexByte(sentence, '*')
	if star < 1 || len(sentence) != star+3 {
		return nil
	}
	checksum, e := strconv.ParseUint(sentence[star+1:], 16, 8)
	if e != nil {
		return nil
	}
	var sum byte
	for _, b := range []byte(sentence[1:star]) {
		sum ^= b
	}
	if uint64(sum) != checksum {
		return nil
	}
	f := strings.Split(sentence[1:star], ",")
	if len(f) < 10 || len(f[0]) != 5 || f[0][2:] != "RMC" || f[2] != "A" {
		return nil
	}
	if len(f) > 12 && (f[12] == "N" || f[12] == "E" || f[12] == "S") {
		return nil
	}
	coord := func(s, hem string, limit float64) (float64, bool) {
		n, e := strconv.ParseFloat(s, 64)
		if e != nil || math.IsNaN(n) || math.IsInf(n, 0) || n < 0 {
			return 0, false
		}
		degrees := math.Floor(n / 100)
		minutes := n - degrees*100
		if minutes >= 60 {
			return 0, false
		}
		v := degrees + minutes/60
		if v > limit {
			return 0, false
		}
		if hem == "S" || hem == "W" {
			v = -v
		}
		return v, true
	}
	if f[4] != "N" && f[4] != "S" || f[6] != "E" && f[6] != "W" {
		return nil
	}
	lat, a := coord(f[3], f[4], 90)
	lon, b := coord(f[5], f[6], 180)
	if !a || !b {
		return nil
	}
	if len(f[1]) < 6 || len(f[9]) != 6 {
		return nil
	}
	stamp, e := time.Parse("020106150405", f[9]+f[1][:6])
	if e != nil || stamp.After(now.Add(5*time.Second)) || stamp.Before(now.Add(-24*time.Hour)) {
		return nil
	}
	return &vehicleLocation{Latitude: lat, Longitude: lon, At: stamp.UnixMilli()}
}
func locationContract(doc string) bool {
	var n struct {
		Interfaces []struct {
			Name    string `xml:"name,attr"`
			Signals []struct {
				Name string `xml:"name,attr"`
				Args []struct {
					Type string `xml:"type,attr"`
				} `xml:"arg"`
			} `xml:"signal"`
		} `xml:"interface"`
	}
	if xml.Unmarshal([]byte(doc), &n) != nil {
		return false
	}
	for _, i := range n.Interfaces {
		if i.Name != locationBus {
			continue
		}
		for _, s := range i.Signals {
			if s.Name == "update_nmea_info" && len(s.Args) == 2 && s.Args[0].Type == "i" && s.Args[1].Type == "s" {
				return true
			}
		}
	}
	return false
}
func (s *securityService) locationSession(ctx context.Context) error {
	raw, e := (&net.Dialer{Timeout: 4 * time.Second}).DialContext(ctx, "unix", "/tmp/.hirain-dbus")
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
	probe, cancel := context.WithTimeout(ctx, 4*time.Second)
	defer cancel()
	var doc string
	if e = c.Object(locationBus, locationPath).CallWithContext(probe, "org.freedesktop.DBus.Introspectable.Introspect", 0).Store(&doc); e != nil {
		return e
	}
	if !locationContract(doc) {
		return errors.New("unsupported OEM location contract")
	}
	signals := make(chan *dbus.Signal, 64)
	c.Signal(signals)
	defer c.RemoveSignal(signals)
	if e = c.AddMatchSignal(dbus.WithMatchSender(locationBus), dbus.WithMatchObjectPath(locationPath), dbus.WithMatchInterface(locationBus), dbus.WithMatchMember("update_nmea_info")); e != nil {
		return e
	}
	raw.SetDeadline(time.Time{})
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-c.Context().Done():
			return fmt.Errorf("location disconnected")
		case sig, ok := <-signals:
			if !ok {
				return errors.New("location closed")
			}
			if sig == nil || sig.Path != locationPath || sig.Name != locationBus+".update_nmea_info" || len(sig.Body) != 2 {
				continue
			}
			if _, ok := sig.Body[0].(int32); !ok {
				continue
			}
			nmea, ok := sig.Body[1].(string)
			if !ok || len(nmea) > 8192 {
				continue
			}
			for _, line := range strings.Split(nmea, "\n") {
				if p := parseRMC(line, time.Now()); p != nil {
					s.mu.Lock()
					if s.location == nil || p.At > s.location.At {
						s.location = p
					}
					s.mu.Unlock()
				}
			}
		}
	}
}
func (s *securityService) runLocation(ctx context.Context) {
	for {
		_ = s.locationSession(ctx)
		select {
		case <-ctx.Done():
			return
		case <-time.After(3 * time.Second):
		}
	}
}
