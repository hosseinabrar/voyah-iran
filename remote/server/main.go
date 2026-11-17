package main

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
	p "voyah/remote/TBox/TBox-remote/protocol"
)

type job struct {
	packet    p.Packet
	until     time.Time
	result    chan p.Packet
	delivered bool
}
type gateway struct {
	mu            sync.Mutex
	jobs          map[string]*job
	client, agent string
	lastPoll      time.Time
}

func (g *gateway) auth(w http.ResponseWriter, r *http.Request, token string) bool {
	if r.Method != "POST" {
		http.Error(w, "POST required", 405)
		return false
	}
	got := r.Header.Get("Authorization")
	if len(token) < 32 || subtle.ConstantTimeCompare([]byte(got), []byte("Bearer "+token)) != 1 {
		http.Error(w, "unauthorized", 401)
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, 65536)
	w.Header().Set("Cache-Control", "no-store")
	return true
}
func (g *gateway) exchange(w http.ResponseWriter, r *http.Request) {
	if !g.auth(w, r, g.client) {
		return
	}
	var q p.Packet
	if json.NewDecoder(r.Body).Decode(&q) != nil || !p.ValidID(q.ID) || len(q.Data) > 60000 {
		http.Error(w, "bad packet", 400)
		return
	}
	j := &job{packet: q, until: time.Now().Add(10 * time.Second), result: make(chan p.Packet, 1)}
	g.mu.Lock()
	if len(g.jobs) >= 64 || g.jobs[q.ID] != nil {
		g.mu.Unlock()
		http.Error(w, "busy/duplicate", 409)
		return
	}
	g.jobs[q.ID] = j
	g.mu.Unlock()
	defer func() { g.mu.Lock(); delete(g.jobs, q.ID); g.mu.Unlock() }()
	ctx, cancel := context.WithDeadline(r.Context(), j.until)
	defer cancel()
	select {
	case v := <-j.result:
		json.NewEncoder(w).Encode(v)
	case <-ctx.Done():
		http.Error(w, "timeout", 504)
	}
}
func (g *gateway) poll(w http.ResponseWriter, r *http.Request) {
	if !g.auth(w, r, g.agent) {
		return
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	g.lastPoll = time.Now()
	out := []p.Packet{}
	for id, j := range g.jobs {
		if time.Now().After(j.until) {
			delete(g.jobs, id)
			continue
		}
		if !j.delivered {
			out = append(out, j.packet)
			j.delivered = true
		}
	}
	json.NewEncoder(w).Encode(out)
}
func (g *gateway) reply(w http.ResponseWriter, r *http.Request) {
	if !g.auth(w, r, g.agent) {
		return
	}
	var q p.Packet
	if json.NewDecoder(r.Body).Decode(&q) != nil {
		http.Error(w, "bad packet", 400)
		return
	}
	g.mu.Lock()
	j := g.jobs[q.ID]
	g.mu.Unlock()
	if j != nil && time.Now().Before(j.until) {
		select {
		case j.result <- q:
		default:
		}
	}
	json.NewEncoder(w).Encode(map[string]bool{"accepted": j != nil})
}
func (g *gateway) handler() http.Handler {
	m := http.NewServeMux()
	remoteRoutes(m)
	m.HandleFunc("/v1/exchange", g.exchange)
	m.HandleFunc("/v1/poll", g.poll)
	m.HandleFunc("/v1/reply", g.reply)
	m.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok\n")) })
	return m
}
func main() {
	addr := flag.String("listen", "127.0.0.1:18780", "bind address")
	config := flag.String("credentials", "credentials.local.json", "tokens only; no endpoint keys")
	flag.Parse()
	b, e := os.ReadFile(*config)
	if e != nil {
		log.Fatal(e)
	}
	var c map[string]string
	if e = json.Unmarshal(b, &c); e != nil {
		log.Fatal(e)
	}
	if len(c["client"]) < 32 || len(c["agent"]) < 32 {
		log.Fatal("missing tokens")
	}
	g := &gateway{jobs: map[string]*job{}, client: c["client"], agent: c["agent"]}
	s := &http.Server{Addr: *addr, Handler: g.handler(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 5 * time.Second, WriteTimeout: 15 * time.Second, IdleTimeout: 30 * time.Second, MaxHeaderBytes: 8192}
	log.Print("Gateway listening on ", *addr)
	log.Fatal(s.ListenAndServe())
}
