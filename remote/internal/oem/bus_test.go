package oem

import "testing"

func TestFactoryBusTransports(t *testing.T) {
	for _, x := range []struct{ address, socket string }{{"", "/tmp/.hirain-dbus"}, {"unix:path=/tmp/.hirain-dbus", "/tmp/.hirain-dbus"}, {"unix:abstract=/tmp/dbus-cwrMdqoyqU,guid=old", "@/tmp/dbus-cwrMdqoyqU"}, {"tcp:host=example", "/tmp/.hirain-dbus"}} {
		t.Setenv("VI_OEM_BUS_ADDRESS", x.address)
		if Socket() != x.socket {
			t.Fatalf("%s: %s", x.address, Socket())
		}
	}
}
