package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	"time"
	t "voyah/remote/internal/telemetry"
)

type remoteJob struct {
	packet    t.Envelope
	reply     chan t.Envelope
	delivered bool
}
type remoteRelay struct {
	mu          sync.Mutex
	jobs        map[string]*remoteJob
	agents      map[string]*relayPeer
	connections int
	idle        time.Duration
}

func remoteRoutes(mux *http.ServeMux) {
	r := &remoteRelay{jobs: map[string]*remoteJob{}}
	mux.HandleFunc("/v2/stream/phone", r.stream)
	mux.HandleFunc("/v2/stream/agent", r.stream)
	mux.HandleFunc("/v2/exchange", r.exchange)
	mux.HandleFunc("/v2/poll", r.poll)
	mux.HandleFunc("/v2/reply", r.reply)
}
func decodeRemote(w http.ResponseWriter, r *http.Request, v any) bool {
	if r.Method != "POST" {
		http.Error(w, "POST required", 405)
		return false
	}
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Content-Type", "application/json")
	return json.NewDecoder(http.MaxBytesReader(w, r.Body, 32768)).Decode(v) == nil
}
func routeOwner(r *http.Request) string {
	if !strings.HasPrefix(r.Header.Get("Authorization"), "Bearer ") {
		return ""
	}
	token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if len(token) != 43 {
		return ""
	}
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}
func (r *remoteRelay) exchange(w http.ResponseWriter, q *http.Request) {
	var p t.Envelope
	if !decodeRemote(w, q, &p) || len(p.Vehicle) != 64 || len(p.ID) != 43 || len(p.Device) > 64 || len(p.Data) > 24000 {
		http.Error(w, "invalid packet", 400)
		return
	}
	j := r.addJob(p)
	if j == nil {
		http.Error(w, "busy", 429)
		return
	}
	defer r.removeJob(j)
	timer := time.NewTimer(8 * time.Second)
	defer timer.Stop()
	select {
	case p := <-j.reply:
		json.NewEncoder(w).Encode(p)
	case <-timer.C:
		http.Error(w, "timeout", 504)
	case <-q.Context().Done():
	}
}
func (r *remoteRelay) poll(w http.ResponseWriter, q *http.Request) {
	var unused struct{}
	if !decodeRemote(w, q, &unused) {
		http.Error(w, "invalid", 400)
		return
	}
	vehicle := routeOwner(q)
	if vehicle == "" {
		http.Error(w, "unauthorized", 401)
		return
	}
	deadline := time.NewTimer(5 * time.Second)
	defer deadline.Stop()
	tick := time.NewTicker(100 * time.Millisecond)
	defer tick.Stop()
	for {
		out := []t.Envelope{}
		r.mu.Lock()
		for _, j := range r.jobs {
			if j.packet.Vehicle == vehicle && !j.delivered {
				j.delivered = true
				out = append(out, j.packet)
			}
		}
		r.mu.Unlock()
		if len(out) > 0 {
			json.NewEncoder(w).Encode(out)
			return
		}
		select {
		case <-q.Context().Done():
			return
		case <-deadline.C:
			json.NewEncoder(w).Encode(out)
			return
		case <-tick.C:
		}
	}

}
func (r *remoteRelay) reply(w http.ResponseWriter, q *http.Request) {
	var p t.Envelope
	if !decodeRemote(w, q, &p) || p.Vehicle != routeOwner(q) {
		http.Error(w, "unauthorized", 401)
		return
	}
	r.mu.Lock()
	j := r.jobs[p.Vehicle+"/"+p.ID]
	if j != nil && p.Device == j.packet.Device {
		select {
		case j.reply <- p:
		default:
		}
	}
	r.mu.Unlock()
	w.WriteHeader(204)
}
