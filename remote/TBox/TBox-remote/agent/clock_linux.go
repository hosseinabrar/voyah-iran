//go:build linux

package main

import (
	"syscall"
	"time"
	"unsafe"
)

// CLOCK_BOOTTIME includes suspend, unlike Go's CLOCK_MONOTONIC. Sleep cannot
// extend a 60-second SMS lease. Wall-clock/NTP changes cannot extend it either.
func leaseNow() time.Time {
	var ts syscall.Timespec
	_, _, e := syscall.Syscall(syscall.SYS_CLOCK_GETTIME, 7, uintptr(unsafe.Pointer(&ts)), 0)
	if e != 0 {
		panic("CLOCK_BOOTTIME unavailable")
	}
	return time.Unix(ts.Sec, ts.Nsec)
}
