package sms

import (
	"bufio"
	"context"
	"encoding/json"
	"os/exec"
	"time"
)

// OEM SDK raw PDU bypasses the VT modem's lossy UCS-2 text decoder. The
// isolated helper verifies its SDK ABI before readiness; other firmware keeps
// the existing OEM D-Bus receive path. Sending always stays on the OEM bus.
func (b *Bus) runPDU(ctx context.Context) {
	for ctx.Err() == nil {
		b.pduSession(ctx)
		select {
		case <-ctx.Done():
			return
		case <-time.After(5 * time.Second):
		}
	}
}
func (b *Bus) pduSession(ctx context.Context) {
	child, cancel := context.WithCancel(ctx)
	defer cancel()
	cmd := exec.CommandContext(child, "sh", "/data/vi-remote/bin/python-runtime", "/data/vi-remote/bin/sms-pdu.py")
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return
	}
	if cmd.Start() != nil {
		return
	}
	defer func() { cancel(); cmd.Wait(); b.mu.Lock(); b.pduReady = false; b.mu.Unlock() }()
	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 4096), 65536)
	for scanner.Scan() {
		var event struct {
			Ready  bool   `json:"ready"`
			Sender string `json:"sender"`
			Body   string `json:"body"`
			Stamp  string `json:"stamp"`
		}
		if json.Unmarshal(scanner.Bytes(), &event) != nil {
			continue
		}
		if event.Ready {
			b.mu.Lock()
			b.pduReady = true
			b.mu.Unlock()
			continue
		}
		if event.Sender != "" {
			_ = b.store.Receive(event.Sender, event.Body, event.Stamp)
		}
	}
}
