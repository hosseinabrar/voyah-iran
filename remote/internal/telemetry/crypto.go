// Package telemetry implements the browser-compatible, end-to-end encrypted
// protocol. Relay identifiers are routing hints, never authentication evidence.
package telemetry

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdh"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
)

var Encoding = base64.RawURLEncoding

func Random() string {
	b := make([]byte, 32)
	if _, e := rand.Read(b); e != nil {
		panic(e)
	}
	return Encoding.EncodeToString(b)
}

type Envelope struct {
	Vehicle string `json:"vehicle"`
	Device  string `json:"device"`
	ID      string `json:"id"`
	Data    string `json:"data"`
}
type Request struct {
	Page    string `json:"page,omitempty"`
	Action  string `json:"action,omitempty"`
	Percent *int   `json:"percent,omitempty"`
	Enabled *bool  `json:"enabled,omitempty"`
	Start   string `json:"start,omitempty"`
	End     string `json:"end,omitempty"`
	ID      string `json:"id"`
	Kind    string `json:"kind"`
	SentAt  int64  `json:"sentAt"`
}
type Reply struct {
	ID       string    `json:"id"`
	Status   string    `json:"status"`
	Snapshot *Snapshot `json:"snapshot,omitempty"`
}
type Snapshot struct {
	LastCANAt   int64            `json:"lastCANAt,omitempty"`
	CANMessages uint64           `json:"canMessages,omitempty"`
	FieldTimes  map[string]int64 `json:"fieldTimes,omitempty"`
	ObservedAt  int64            `json:"observedAt"`
	Values      map[string]any   `json:"values"`
}

func GenerateKey() (*ecdh.PrivateKey, error) { return ecdh.P256().GenerateKey(rand.Reader) }
func Shared(private, public []byte) ([]byte, error) {
	sk, e := ecdh.P256().NewPrivateKey(private)
	if e != nil {
		return nil, e
	}
	pk, e := ecdh.P256().NewPublicKey(public)
	if e != nil {
		return nil, e
	}
	s, e := sk.ECDH(pk)
	if e != nil {
		return nil, e
	}
	h := sha256.Sum256(append([]byte("VI-Remote-v2|"), s...))
	return h[:], nil
}
func crypt(key []byte) (cipher.AEAD, error) {
	b, e := aes.NewCipher(key)
	if e != nil {
		return nil, e
	}
	return cipher.NewGCM(b)
}
func aad(q Envelope, direction string) []byte {
	return []byte("VI2|" + direction + "|" + q.Vehicle + "|" + q.Device + "|" + q.ID)
}
func Seal(key []byte, q Envelope, direction string, value any) (Envelope, error) {
	a, e := crypt(key)
	if e != nil {
		return q, e
	}
	n := make([]byte, a.NonceSize())
	if _, e = rand.Read(n); e != nil {
		return q, e
	}
	b, e := json.Marshal(value)
	if e != nil {
		return q, e
	}
	q.Data = Encoding.EncodeToString(a.Seal(n, n, b, aad(q, direction)))
	return q, nil
}
func Open(key []byte, q Envelope, direction string, value any) error {
	a, e := crypt(key)
	if e != nil {
		return e
	}
	b, e := Encoding.DecodeString(q.Data)
	if e != nil || len(b) < a.NonceSize() {
		return errors.New("invalid envelope")
	}
	p, e := a.Open(nil, b[:a.NonceSize()], b[a.NonceSize():], aad(q, direction))
	if e != nil {
		return e
	}
	return json.Unmarshal(p, value)
}
