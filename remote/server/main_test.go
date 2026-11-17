package main

import (
	"context"
	"fmt"
	"net/http/httptest"
	"testing"
	"time"
	p "voyah/remote/TBox/TBox-remote/protocol"
)

func TestRelayOpaqueRoundTripAndAuth(t *testing.T) {
	g := &gateway{jobs: map[string]*job{}, client: "client-token-012345678901234567890", agent: "agent-token-0123456789012345678901"}
	s := httptest.NewServer(g.handler())
	defer s.Close()
	c := p.Pairing{URL: s.URL, Token: g.client}
	a := p.Pairing{URL: s.URL, Token: g.agent}
	q := p.Packet{ID: p.ID(), Data: "opaque ciphertext"}
	done := make(chan error, 1)
	go func() {
		var reply p.Packet
		e := p.Call(context.Background(), c, "/v1/exchange", q, &reply)
		if e == nil && reply.Data != "opaque result" {
			e = fmt.Errorf("wrong result")
		}
		done <- e
	}()
	var jobs []p.Packet
	for i := 0; i < 20; i++ {
		p.Call(context.Background(), a, "/v1/poll", nil, &jobs)
		if len(jobs) > 0 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if len(jobs) != 1 || jobs[0].Data != q.Data {
		t.Fatal("packet lost")
	}
	if e := p.Call(context.Background(), a, "/v1/reply", p.Packet{ID: q.ID, Data: "opaque result"}, nil); e != nil {
		t.Fatal(e)
	}
	if e := <-done; e != nil {
		t.Fatal(e)
	}
	a.Token = "invalid"
	if e := p.Call(context.Background(), a, "/v1/poll", nil, &jobs); e == nil {
		t.Fatal("unauthorized")
	}
}
