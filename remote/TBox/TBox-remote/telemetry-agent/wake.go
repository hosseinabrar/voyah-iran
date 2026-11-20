package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"
	"voyah/remote/internal/oem"
)

const wakeLeaseSeconds = 30
const wakeRenewInterval = 10 * time.Second

type wakeLeaser struct {
	mu       sync.Mutex
	last     time.Time
	sequence uint64
	run      func(context.Context, ...string) ([]byte, error)
}

var activeWake = wakeLeaser{run: func(ctx context.Context, args ...string) ([]byte, error) {
	return exec.CommandContext(ctx, "busctl", args...).Output()
}}

func (s *service) keepAwake() {
	if s.wake != nil {
		s.wake()
		return
	}
	activeWake.renew(time.Now())
}

// Only authenticated, entitled page requests renew. No background renewal:
// OEM leases expire after the last request, including when our process crashes.
func (w *wakeLeaser) renew(now time.Time) {
	if !w.mu.TryLock() {
		return
	}
	defer w.mu.Unlock()
	if !w.last.IsZero() && now.Sub(w.last) < wakeRenewInterval {
		return
	}
	w.last = now // Rate-limit retries on failure too.
	w.sequence++
	// VT/C returns success for the same name/PID without extending its deadline.
	// Distinct overlapping finite leases avoid that trap without a release gap.
	name := fmt.Sprintf("vi-remote-%d-%d", now.UnixNano(), w.sequence)
	// OPENCPU keeps Linux alive. Retain existing MCU and VEHICLE_ONCE path.
	for _, kind := range []string{"0", "1", "2"} {
		ctx, cancel := context.WithTimeout(context.Background(), 2500*time.Millisecond)
		out, err := w.run(ctx, "--address="+oem.Address(), "--no-pager", "--timeout=2", "call", "gdbus.hcommon.powermanager", "/gdbus/hcommon/powermanager", "gdbus.hcommon.powermanager", "apply_wakelock", "iisi", kind, strconv.Itoa(os.Getpid()), name, strconv.Itoa(wakeLeaseSeconds))
		cancel()
		if err != nil {
			log.Printf("Telemetry wake kind=%s unavailable: %v", kind, err)
			continue
		}
		if strings.TrimSpace(string(out)) != "b true" {
			log.Printf("Telemetry wake kind=%s rejected or invalid reply", kind)
		}
	}
}
