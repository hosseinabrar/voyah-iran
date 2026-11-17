package main

import (
	"context"
	"embed"
	"encoding/json"
	"flag"
	"io/fs"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
	p "voyah/remote/TBox/TBox-remote/protocol"
)

//go:embed pairing.local.json
var pairData []byte

//go:embed web/*
var web embed.FS

type app struct {
	mu           sync.Mutex
	pair         p.Pairing
	status       p.Message
	seen, expiry time.Time
	token        string
}

func (a *app) exchange(ctx context.Context, m p.Message) (p.Message, error) {
	q, e := p.Seal(a.pair, m)
	if e != nil {
		return p.Message{}, e
	}
	var res p.Packet
	if e = p.Call(ctx, a.pair, "/v1/exchange", q, &res); e != nil {
		return p.Message{}, e
	}
	v, e := p.Open(a.pair, res)
	if e == nil && v.ID != m.ID {
		return p.Message{}, http.ErrNotSupported
	}
	return v, e
}
func (a *app) heartbeat() {
	for {
		start := time.Now()
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		m, e := a.exchange(ctx, p.Message{ID: p.ID(), Kind: "ping"})
		cancel()
		a.mu.Lock()
		if e == nil && m.Kind == "status" && m.Remaining > 0 && m.Remaining <= 60000 {
			a.status = m
			a.seen = time.Now()
			a.expiry = start.Add(time.Duration(m.Remaining) * time.Millisecond)
		}
		a.mu.Unlock()
		time.Sleep(500 * time.Millisecond)
	}
}
func (a *app) live() bool { return time.Since(a.seen) < 3*time.Second && time.Now().Before(a.expiry) }
func main() {
	listen := flag.String("listen", "127.0.0.1:18781", "loopback only")
	url := flag.String("gateway", "", "override for local simulation")
	flag.Parse()
	if !strings.HasPrefix(*listen, "127.0.0.1:") {
		log.Fatal("web tester must bind loopback")
	}
	pair, e := p.Load(pairData)
	if e != nil {
		log.Fatal(e)
	}
	if *url != "" {
		pair.URL = *url
	}
	a := &app{pair: pair, token: p.ID()}
	go a.heartbeat()
	mux := http.NewServeMux()
	mux.HandleFunc("/api/state", func(w http.ResponseWriter, r *http.Request) {
		a.mu.Lock()
		defer a.mu.Unlock()
		remaining := int64(0)
		if a.live() {
			remaining = time.Until(a.expiry).Milliseconds()
		}
		w.Header().Set("Cache-Control", "no-store")
		json.NewEncoder(w).Encode(map[string]any{"connected": a.live(), "remaining_ms": remaining, "csrf": a.token, "mode": a.status.Status, "commands": p.Catalog()})
	})
	mux.HandleFunc("/api/command", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" || r.Header.Get("X-Test-Token") != a.token || r.Header.Get("Origin") != "http://"+*listen {
			http.Error(w, "forbidden", 403)
			return
		}
		var request struct {
			Command string `json:"command"`
		}
		r.Body = http.MaxBytesReader(w, r.Body, 2048)
		if json.NewDecoder(r.Body).Decode(&request) != nil || !p.Known(request.Command) {
			http.Error(w, "bad command", 400)
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
		defer cancel()
		a.mu.Lock()
		live, st := a.live(), a.status
		a.mu.Unlock()
		if !live {
			<-ctx.Done()
			json.NewEncoder(w).Encode(map[string]string{"status": "timeout", "detail": "No active vehicle connection; command was not sent"})
			return
		}
		m, e := a.exchange(ctx, p.Message{ID: p.ID(), Kind: "command", Lease: st.Lease, Grant: st.Grant, Command: request.Command})
		if e != nil {
			json.NewEncoder(w).Encode(map[string]string{"status": "timeout", "detail": "No confirmed result within 10 seconds; do not assume execution or retry automatically"})
			return
		}
		json.NewEncoder(w).Encode(m)
	})
	assets, _ := fs.Sub(web, "web")
	mux.Handle("/", http.FileServer(http.FS(assets)))
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Host != *listen {
			http.Error(w, "invalid host", 403)
			return
		}
		w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		mux.ServeHTTP(w, r)
	})
	log.Print("Open http://", *listen)
	log.Fatal((&http.Server{Addr: *listen, Handler: handler, ReadHeaderTimeout: 5 * time.Second}).ListenAndServe())
}
