// Package oem resolves the installed factory bus without assuming the X layout.
package oem

import (
	"os"
	"strings"
)

func Address() string {
	if v := os.Getenv("VI_OEM_BUS_ADDRESS"); strings.HasPrefix(v, "unix:abstract=") || strings.HasPrefix(v, "unix:path=") {
		return v
	}
	return "unix:path=/tmp/.hirain-dbus"
}
func Socket() string {
	s := strings.Split(Address(), ",")[0]
	if strings.HasPrefix(s, "unix:abstract=") {
		return "@" + strings.TrimPrefix(s, "unix:abstract=")
	}
	return strings.TrimPrefix(s, "unix:path=")
}
