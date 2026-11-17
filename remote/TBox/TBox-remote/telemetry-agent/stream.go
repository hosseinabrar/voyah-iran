package main

import (
	"context"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	t "voyah/remote/internal/telemetry"
)

func (s *service) runStreams(ctx context.Context) {
	for ctx.Err() == nil {
		s.store.Mu.Lock()
		token := s.store.State.RelayToken
		s.store.Mu.Unlock()
		url := "wss://" + strings.TrimPrefix(s.relay, "https://") + "/v2/stream/agent"
		dialer := websocket.Dialer{HandshakeTimeout: 8 * time.Second}
		c, _, err := dialer.DialContext(ctx, url, http.Header{"Authorization": []string{"Bearer " + token}})
		if err == nil {
			s.serveStream(ctx, c)
		} else {
			log.Print("Remote stream unavailable")
		}
		timer := time.NewTimer(2 * time.Second)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}
func (s *service) serveStream(ctx context.Context, c *websocket.Conn) {
	defer c.Close()
	done := make(chan struct{})
	defer close(done)
	go func() {
		select {
		case <-ctx.Done():
			c.Close()
		case <-done:
		}
	}()
	c.SetReadLimit(32768)
	for ctx.Err() == nil {
		c.SetReadDeadline(time.Now().Add(20 * time.Second))
		var q t.Envelope
		if c.ReadJSON(&q) != nil {
			return
		}
		reply, err := s.handle(q)
		if err != nil {
			continue
		}
		c.SetWriteDeadline(time.Now().Add(5 * time.Second))
		if c.WriteJSON(reply) != nil {
			return
		}
	}
}
