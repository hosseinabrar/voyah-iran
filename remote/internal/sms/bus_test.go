package sms

import (
	"strings"
	"testing"
)

func TestCAndXDecoderRoundtrip(t *testing.T) {
	for _, body := range []string{"Your code: 482731", "کد ورود شما 482731 است.\nسلام", "€: فارسی"} {
		wire, e := Encode(body)
		if e != nil {
			t.Fatal(e)
		}
		for _, profile := range []string{"C", "X"} {
			out := make([]byte, len(wire)/2)
			for i := range out {
				out[i] = ((wire[2*i] & 15) << 4) + (wire[2*i+1] - '0')
			}
			if string(out) != body {
				t.Fatal(profile, string(out))
			}
		}
	}
	wire, _ := Encode("\u06cc")
	if !strings.ContainsAny(wire, ":;<=>?") {
		t.Fatal("wrong hexadecimal encoding")
	}
}
func TestValidation(t *testing.T) {
	for _, v := range []string{"", " ", "😀", strings.Repeat("الف", 501), "a\x00b"} {
		if _, e := Encode(v); e == nil {
			t.Fatalf("accepted invalid body")
		}
	}
	for _, v := range []string{"+", "x", "12;reboot", "+98912\n123", "123+456"} {
		if _, e := Recipient(v); e == nil {
			t.Fatal(v)
		}
	}
	if to, e := Recipient("۰۹۱۲۱۲۳۴۵۶۷"); e != nil || to != "+989121234567" {
		t.Fatal(to, e)
	}
}
func TestRuntimeABIGuard(t *testing.T) {
	doc := `<node><interface name="gdbus.hcommon.rild.sms"><method name="sendSms"><arg type="s" direction="in"/><arg type="s" direction="in"/><arg type="i" direction="in"/><arg type="b" direction="out"/></method><signal name="receiveSms"><arg type="s"/><arg type="s"/><arg type="i"/><arg type="s"/></signal></interface></node>`
	if !compatible(doc) {
		t.Fatal("verified C/X ABI rejected")
	}
	if compatible(strings.Replace(doc, `type="i"`, `type="u"`, 1)) {
		t.Fatal("unknown ABI accepted")
	}
}
