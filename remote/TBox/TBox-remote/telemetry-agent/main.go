package main

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
	"voyah/remote/internal/sms"
	t "voyah/remote/internal/telemetry"
)

type service struct {
	security                      securityService
	messages                      *sms.Store
	smsSend                       func(context.Context, string, string) error
	ctx                           context.Context
	wake                          func()
	nativeRun                     func(context.Context, []byte) ([]byte, error)
	store                         *t.Store
	snapshotPath, relay, headunit string
	client                        *http.Client
}

func (s *service) snapshot() *t.Snapshot {
	b, e := os.ReadFile(s.snapshotPath)
	if e != nil {
		return nil
	}
	var v t.Snapshot
	if json.Unmarshal(b, &v) != nil || v.ObservedAt <= 0 || v.ObservedAt > time.Now().Add(5*time.Second).UnixMilli() {
		return nil
	}
	return &v
}
func abs(n int64) int64 {
	if n < 0 {
		return -n
	}
	return n
}
func (s *service) handle(q t.Envelope) (t.Envelope, error) {
	s.store.Mu.Lock()
	defer s.store.Mu.Unlock()
	st := &s.store.State
	now := time.Now()
	if q.Vehicle != st.Vehicle || len(q.ID) != 43 {
		return q, fmt.Errorf("invalid route")
	}
	for _, i := range st.Invitations {
		if i.ID == q.Device && i.Expires > now.Unix() {
			key, _ := t.Encoding.DecodeString(i.Secret)
			var req struct {
				ID, Public string
				SentAt     int64
			}
			if e := t.Open(key, q, "request", &req); e != nil {
				return q, e
			}
			if req.ID != q.ID || abs(now.UnixMilli()-req.SentAt) > 15000 {
				return q, fmt.Errorf("expired")
			}
			d, e := s.store.Pair(i.ID, i.Secret, req.Public, now)
			if e != nil {
				return t.Seal(key, q, "reply", map[string]string{"status": e.Error(), "id": q.ID})
			}
			return t.Seal(key, q, "reply", map[string]string{"status": "paired", "device": d.ID, "name": d.Name, "id": q.ID})
		}
	}
	for _, d := range st.Devices {
		if d.ID != q.Device {
			continue
		}
		key, e := t.Encoding.DecodeString(d.Key)
		if e != nil {
			return q, e
		}
		var req t.Request
		if e = t.Open(key, q, "request", &req); e != nil {
			return q, e
		}
		if req.ID != q.ID || abs(now.UnixMilli()-req.SentAt) > 15000 {
			return q, fmt.Errorf("expired")
		}
		if _, ok := st.Seen[q.ID]; ok {
			return q, fmt.Errorf("replay")
		}
		for id, at := range st.Seen {
			if now.UnixMilli()-at > 60000 {
				delete(st.Seen, id)
			}
		}
		st.Seen[q.ID] = now.UnixMilli()
		if e = s.store.Save(); e != nil {
			return q, e
		}
		reply := t.Reply{ID: q.ID, Status: "ok"}
		if !s.store.Allowed(now) {
			reply.Status = "subscription_expired"
		} else if req.Kind == "snapshot" {
			reply.Snapshot = s.pageSnapshot(req.Page)
			if req.Page == "battery" {
				batteryCapabilities(reply.Snapshot)
			}
			if reply.Snapshot == nil {
				reply.Status = "data_unavailable"
			}
		} else if req.Kind == "security" {
			if req.Action == "state" {
				snap := s.snapshot()
				if snap == nil || snap.CANMessages == 0 || time.Now().UnixMilli()-snap.LastCANAt >= 5000 {
					if s.wake != nil {
						s.wake()
					} else {
						requestWake()
					}
				}
			}
			reply.Status, reply.Security = s.security.execute(d.ID, req)
		} else if req.Kind == "sms" {
			reply.Status, reply.SMS = s.executeSMS(req)
		} else if req.Kind == "battery-command" {
			reply.Status = s.executeBattery(req)
		} else if req.Kind != "ping" {
			reply.Status = "unsupported"
		}
		return t.Seal(key, q, "reply", reply)
	}
	return q, fmt.Errorf("unpaired")
}
func (s *service) local(w http.ResponseWriter, r *http.Request) {
	host, _, _ := net.SplitHostPort(r.RemoteAddr)
	if host != s.headunit && host != "127.0.0.1" {
		http.Error(w, "forbidden", 403)
		return
	}
	if r.Method != "POST" {
		http.Error(w, "POST required", 405)
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Content-Type", "application/json")
	if r.URL.Query().Has("security") {
		// A headunit must never supply vehicle position or complete a camera job.
		http.Error(w, "unsupported", http.StatusNotFound)
		return
	}
	var req struct {
		Action, Name, ID string
		RemoteAccess     bool
		Expires          int64
	}
	if json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&req) != nil {
		http.Error(w, "bad request", 400)
		return
	}
	s.store.Mu.Lock()
	defer s.store.Mu.Unlock()
	st := &s.store.State
	if req.Action == "entitlement" {
		st.RemoteAccess = req.RemoteAccess
		if !req.RemoteAccess {
			s.security.revoke("")
		}
		st.Expires = req.Expires
		if e := s.store.Save(); e != nil {
			http.Error(w, "save failed", 500)
			return
		}
		json.NewEncoder(w).Encode(map[string]bool{"ok": true})
		return
	}
	if !s.store.Allowed(time.Now()) {
		http.Error(w, "subscription_expired", 403)
		return
	}
	switch req.Action {
	case "list":
		out := []t.Device{}
		for _, d := range st.Devices {
			d.Key = ""
			out = append(out, d)
		}
		json.NewEncoder(w).Encode(out)
	case "invite":
		if len([]rune(req.Name)) < 1 || len([]rune(req.Name)) > 40 {
			http.Error(w, "invalid name", 400)
			return
		}
		i, e := s.store.Invite(req.Name, time.Now())
		if e != nil {
			http.Error(w, e.Error(), 409)
			return
		}
		json.NewEncoder(w).Encode(map[string]any{"v": 2, "vehicle": st.Vehicle, "invitation": i.ID, "secret": i.Secret, "public": st.Public, "expires": i.Expires, "relay": s.relay})
	case "rename", "delete":
		found := false
		for n, d := range st.Devices {
			if d.ID == req.ID {
				found = true
				if req.Action == "delete" {
					s.security.revoke(req.ID)
					st.Devices = append(st.Devices[:n], st.Devices[n+1:]...)
				} else {
					if len([]rune(req.Name)) < 1 || len([]rune(req.Name)) > 40 {
						http.Error(w, "invalid name", 400)
						return
					}
					st.Devices[n].Name = req.Name
				}
				break
			}
		}
		if !found {
			http.Error(w, "not found", 404)
			return
		}
		if e := s.store.Save(); e != nil {
			http.Error(w, "save failed", 500)
			return
		}
		json.NewEncoder(w).Encode(map[string]bool{"ok": true})
	default:
		http.Error(w, "unsupported", 400)
	}
}
func (s *service) post(path string, v any, out any) error {
	b, e := json.Marshal(v)
	if e != nil {
		return e
	}
	req, e := http.NewRequest("POST", s.relay+path, bytes.NewReader(b))
	if e != nil {
		return e
	}
	req.Header.Set("Content-Type", "application/json")
	s.store.Mu.Lock()
	token := s.store.State.RelayToken
	s.store.Mu.Unlock()
	req.Header.Set("Authorization", "Bearer "+token)
	res, e := s.client.Do(req)
	if e != nil {
		return e
	}
	defer res.Body.Close()
	if res.StatusCode >= 300 {
		return fmt.Errorf("relay HTTP %d", res.StatusCode)
	}
	if out != nil {
		return json.NewDecoder(io.LimitReader(res.Body, 65536)).Decode(out)
	}
	return nil
}
func main() {
	state := flag.String("state", "/data/vi-remote/state.json", "persistent state")
	listen := flag.String("listen", "127.0.0.1:18782", "bind vehicle LAN only")
	headunit := flag.String("headunit", "", "trusted headunit IP")
	relay := flag.String("relay", "https://remote.voyah-iran.ir", "HTTPS relay")
	snapshot := flag.String("snapshot", "/data/vi-remote/snapshot.json", "verified collector output")
	flag.Parse()
	if !strings.HasPrefix(*relay, "https://") {
		log.Fatal("HTTPS required")
	}
	st, e := t.Load(*state)
	if e != nil {
		log.Fatal(e)
	}
	s := &service{store: st, snapshotPath: *snapshot, relay: strings.TrimRight(*relay, "/"), headunit: *headunit, client: &http.Client{Timeout: 7 * time.Second}}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	s.ctx = ctx
	go s.security.runLocation(ctx)
	inbox, err := sms.Load(filepath.Join(filepath.Dir(*state), "messages.json"))
	if err != nil {
		// Preserve the damaged file and keep vehicle/battery telemetry available.
		log.Print("SMS storage unavailable: ", err)
	} else {
		s.messages = inbox
		smsBus := sms.NewBus(inbox)
		s.smsSend = smsBus.Send
		go smsBus.Run(ctx)
	}
	go s.runStreams(ctx)
	mux := http.NewServeMux()
	mux.HandleFunc("/local", s.local)
	log.Print("VI Remote telemetry started")
	server := &http.Server{Addr: *listen, Handler: mux, ReadHeaderTimeout: 3 * time.Second, ReadTimeout: 5 * time.Second, WriteTimeout: 8 * time.Second}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 6*time.Second)
		defer cancel()
		server.Shutdown(shutdown)
	}()
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}

// OEM power-manager wake lease expires automatically; no ignition/actuation.
// Called only after authenticated, entitled snapshot requests, not idle relay polls.
var wakeGate = make(chan struct{}, 1)

func requestWake() {
	select {
	case wakeGate <- struct{}{}:
	default:
		return
	}
	defer func() { <-wakeGate }()
	for _, kind := range []string{"1", "2"} {
		ctx, cancel := context.WithTimeout(context.Background(), 2500*time.Millisecond)
		err := exec.CommandContext(ctx, "busctl", "--address=unix:path=/tmp/.hirain-dbus", "--no-pager", "--timeout=2", "call", "gdbus.hcommon.powermanager", "/gdbus/hcommon/powermanager", "gdbus.hcommon.powermanager", "apply_wakelock", "iisi", kind, strconv.Itoa(os.Getpid()), "vi-remote-telemetry", "20").Run()
		cancel()
		if err != nil {
			log.Print("Telemetry wake request unavailable")
		}
	}
}
