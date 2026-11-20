package main

import (
	"bytes"
	"context"
	"errors"
	"log"
	"strings"
	"testing"
	"time"
)

func TestFiniteWakeLeases(t *testing.T) {
	var calls [][]string
	w := wakeLeaser{run: func(ctx context.Context, args ...string) ([]byte, error) {
		if _, ok := ctx.Deadline(); !ok {
			t.Fatal("unbounded")
		}
		calls = append(calls, append([]string(nil), args...))
		return []byte("b true\n"), nil
	}}
	now := time.Now()
	w.renew(now)
	if len(calls) != 3 {
		t.Fatal(calls)
	}
	for i, k := range []string{"0", "1", "2"} {
		a := calls[i]
		if a[len(a)-4] != k || a[len(a)-1] != "30" {
			t.Fatal(a)
		}
	}
	w.renew(now.Add(5 * time.Second))
	if len(calls) != 3 {
		t.Fatal("early renewal")
	}
	w.renew(now.Add(10 * time.Second))
	if len(calls) != 6 {
		t.Fatal("missing renewal")
	}
	if calls[0][len(calls[0])-2] == calls[3][len(calls[3])-2] {
		t.Fatal("duplicate lease")
	}
}
func TestWakeFailureIsVisibleAndBounded(t *testing.T) {
	var buf bytes.Buffer
	old := log.Writer()
	log.SetOutput(&buf)
	defer log.SetOutput(old)
	calls := 0
	w := wakeLeaser{run: func(context.Context, ...string) ([]byte, error) {
		calls++
		if calls == 1 {
			return nil, errors.New("offline")
		}
		return []byte("b false\n"), nil
	}}
	now := time.Now()
	w.renew(now)
	w.renew(now.Add(time.Second))
	if calls != 3 {
		t.Fatal(calls)
	}
	if !strings.Contains(buf.String(), "rejected") || !strings.Contains(buf.String(), "unavailable") {
		t.Fatal(buf.String())
	}
	w.renew(now.Add(10 * time.Second))
	if calls != 6 {
		t.Fatal(calls)
	}
}
