package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"github.com/gorilla/websocket"
	"net/http"
	"os"
	"testing"
	"time"
	t "voyah/remote/internal/telemetry"
)

// Opt-in deployed relay check. Uses an ephemeral route, never a vehicle's identity.
func TestLiveRelay(test *testing.T) {
	endpoint := os.Getenv("VI_LIVE_RELAY")
	if endpoint == "" {
		test.Skip("live relay not requested")
	}
	random := make([]byte, 32)
	if _, err := rand.Read(random); err != nil {
		test.Fatal(err)
	}
	token := base64.RawURLEncoding.EncodeToString(random)
	h := sha256.Sum256([]byte(token))
	agent, _, err := websocket.DefaultDialer.Dial(endpoint+"/v2/stream/agent", http.Header{"Authorization": []string{"Bearer " + token}})
	if err != nil {
		test.Fatal(err)
	}
	defer agent.Close()
	phone, _, err := websocket.DefaultDialer.Dial(endpoint+"/v2/stream/phone", nil)
	if err != nil {
		test.Fatal(err)
	}
	defer phone.Close()
	q := t.Envelope{Vehicle: hex.EncodeToString(h[:]), Device: "release-smoke", ID: token, Data: "synthetic-opaque-payload"}
	if err = phone.WriteJSON(q); err != nil {
		test.Fatal(err)
	}
	agent.SetReadDeadline(time.Now().Add(8 * time.Second))
	var got t.Envelope
	if err = agent.ReadJSON(&got); err != nil || got != q {
		test.Fatal(got, err)
	}
	got.Data = "synthetic-opaque-reply"
	if err = agent.WriteJSON(got); err != nil {
		test.Fatal(err)
	}
	phone.SetReadDeadline(time.Now().Add(8 * time.Second))
	if err = phone.ReadJSON(&q); err != nil || q != got {
		test.Fatal(q, err)
	}
}
