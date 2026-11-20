package telemetry

import (
	"strings"
	"testing"
	"voyah/remote/internal/sms"
)

func TestSMSRepliesFitRelayEnvelope(t *testing.T) {
	cases := []*sms.Result{{ObservedAt: 1800000000000}, {ObservedAt: 1800000000000}, {ObservedAt: 1800000000000, Message: &sms.Message{ID: strings.Repeat("a", 64), Sender: strings.Repeat(`"`, 64), Body: strings.Repeat("\u2028", 2000)}}}
	for i := 0; i < 128; i++ {
		cases[0].Blocked = append(cases[0].Blocked, strings.Repeat(`"`, 64))
	}
	for i := 0; i < 20; i++ {
		cases[1].Messages = append(cases[1].Messages, sms.Message{ID: strings.Repeat("a", 64), Sender: strings.Repeat(`"`, 64), Body: strings.Repeat("\u2028", 80) + "…", At: 1800000000000, ReceivedAt: 1800000000000})
	}
	for _, data := range cases {
		q, e := Seal(make([]byte, 32), Envelope{ID: Random()}, "reply", Reply{ID: Random(), Status: "ok", SMS: data})
		if e != nil {
			t.Fatal(e)
		}
		if len(q.Data) > 24000 {
			t.Fatal("relay would reject SMS", len(q.Data))
		}
	}
}
