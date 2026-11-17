package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	t "voyah/remote/internal/telemetry"
)

func TestStreamRoutingAndCleanup(test *testing.T) {
	r := &remoteRelay{jobs: map[string]*remoteJob{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/v2/stream/phone", r.stream)
	mux.HandleFunc("/v2/stream/agent", r.stream)
	mux.HandleFunc("/v2/exchange", r.exchange)
	srv := httptest.NewServer(mux)
	defer srv.Close()
	url := "ws" + strings.TrimPrefix(srv.URL, "http")
	token := strings.Repeat("a", 43)
	h := sha256.Sum256([]byte(token))
	vehicle := hex.EncodeToString(h[:])
	agent, _, err := websocket.DefaultDialer.Dial(url+"/v2/stream/agent", http.Header{"Authorization": []string{"Bearer " + token}})
	if err != nil {
		test.Fatal(err)
	}
	defer agent.Close()
	phone, _, err := websocket.DefaultDialer.Dial(url+"/v2/stream/phone", nil)
	if err != nil {
		test.Fatal(err)
	}
	q := t.Envelope{Vehicle: vehicle, Device: "phone", ID: strings.Repeat("b", 43), Data: "opaque"}
	phone.WriteJSON(q)
	agent.SetReadDeadline(time.Now().Add(time.Second))
	var got t.Envelope
	if err = agent.ReadJSON(&got); err != nil || got != q {
		test.Fatal(got, err)
	}
	got.Data = "encrypted reply"
	agent.WriteJSON(got)
	phone.SetReadDeadline(time.Now().Add(time.Second))
	if err = phone.ReadJSON(&q); err != nil || q != got {
		test.Fatal(q, err)
	}
	// A replacement connection closes the previous agent; no duplicate writers.
	replacement, _, err := websocket.DefaultDialer.Dial(url+"/v2/stream/agent", http.Header{"Authorization": []string{"Bearer " + token}})
	if err != nil {
		test.Fatal(err)
	}
	defer replacement.Close()
	agent.SetReadDeadline(time.Now().Add(time.Second))
	if agent.ReadJSON(&q) == nil {
		test.Fatal("old socket retained")
	}
	phone.Close()
	replacement.Close()
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		r.mu.Lock()
		clean := len(r.jobs) == 0 && len(r.agents) == 0 && r.connections == 0
		r.mu.Unlock()
		if clean {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	test.Fatal("socket state leaked")
}
func TestStreamRejectsForeignOrigin(test *testing.T) {
	mux := http.NewServeMux()
	remoteRoutes(mux)
	s := httptest.NewServer(mux)
	defer s.Close()
	c, resp, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(s.URL, "http")+"/v2/stream/phone", http.Header{"Origin": []string{"https://evil.example"}})
	if c != nil {
		c.Close()
	}
	if err == nil || resp.StatusCode != 403 {
		test.Fatal("foreign origin accepted")
	}
}

func TestIdleStreamClosed(test *testing.T) {
	r := &remoteRelay{jobs: map[string]*remoteJob{}, idle: 40 * time.Millisecond}
	s := httptest.NewServer(http.HandlerFunc(r.stream))
	defer s.Close()
	c, _, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(s.URL, "http")+"/v2/stream/phone", nil)
	if err != nil {
		test.Fatal(err)
	}
	defer c.Close()
	c.SetReadDeadline(time.Now().Add(time.Second))
	if _, _, err = c.ReadMessage(); err == nil {
		test.Fatal("idle socket retained")
	}
}

func TestHTTPPairExchangeUsesAgentSocket(test *testing.T) {
	r := &remoteRelay{jobs: map[string]*remoteJob{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/v2/stream/agent", r.stream)
	mux.HandleFunc("/v2/exchange", r.exchange)
	srv := httptest.NewServer(mux)
	defer srv.Close()
	token := strings.Repeat("c", 43)
	hash := sha256.Sum256([]byte(token))
	vehicle := hex.EncodeToString(hash[:])
	agent, _, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(srv.URL, "http")+"/v2/stream/agent", http.Header{"Authorization": []string{"Bearer " + token}})
	if err != nil {
		test.Fatal(err)
	}
	defer agent.Close()
	done := make(chan error, 1)
	go func() {
		agent.SetReadDeadline(time.Now().Add(time.Second))
		var q t.Envelope
		e := agent.ReadJSON(&q)
		if e == nil {
			q.Data = "encrypted pairing response"
			e = agent.WriteJSON(q)
		}
		done <- e
	}()
	body := fmt.Sprintf(`{"vehicle":%q,"device":"invitation","id":%q,"data":"encrypted pairing request"}`, vehicle, strings.Repeat("d", 43))
	response, err := http.Post(srv.URL+"/v2/exchange", "application/json", strings.NewReader(body))
	if err != nil {
		test.Fatal(err)
	}
	defer response.Body.Close()
	var reply t.Envelope
	if response.StatusCode != 200 || json.NewDecoder(response.Body).Decode(&reply) != nil || reply.Data != "encrypted pairing response" {
		test.Fatal("pairing response missing")
	}
	if err = <-done; err != nil {
		test.Fatal(err)
	}
}
