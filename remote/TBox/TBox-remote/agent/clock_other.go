//go:build !linux

package main

import "time"

func leaseNow() time.Time { return time.Now() }
