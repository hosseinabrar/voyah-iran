package main

import "testing"

func TestOEMProtobufResult(t *testing.T) {
	b := append(field(1, 123), field(2, 10)...)
	b = append(b, field(3, 0)...)
	b = append(b, 0)
	m, e := parse(b)
	if e != nil || m[1] != 123 || m[2] != 10 {
		t.Fatal(m, e)
	}
	if _, ok := m[3]; !ok {
		t.Fatal("zero result lost")
	}
	if _, e = parse([]byte{255}); e == nil {
		t.Fatal("malformed accepted")
	}
}
