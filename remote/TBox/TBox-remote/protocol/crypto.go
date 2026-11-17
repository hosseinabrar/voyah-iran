package protocol

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdh"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	_ "embed"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
)

type Pairing struct {
	RootCA  string `json:"root_ca,omitempty"`
	RelayIP string `json:"relay_ip,omitempty"`
	Private string `json:"private"`
	Peer    string `json:"peer"`
	Token   string `json:"token"`
	URL     string `json:"url"`
	Role    string `json:"role"`
	Phone   string `json:"phone,omitempty"`
}
type Packet struct {
	ID   string `json:"id"`
	Data string `json:"data"`
}
type Message struct {
	ID        string `json:"id"`
	Kind      string `json:"kind"`
	Lease     string `json:"lease,omitempty"`
	Grant     string `json:"grant,omitempty"`
	Command   string `json:"command,omitempty"`
	Remaining int64  `json:"remaining_ms,omitempty"`
	Status    string `json:"status,omitempty"`
	Detail    string `json:"detail,omitempty"`
}
type Command struct {
	ID        string   `json:"id"`
	Type      int      `json:"type"`
	Parameter int      `json:"parameter"`
	Label     string   `json:"label"`
	Atoms     []string `json:"atoms"`
}

//go:embed catalog.json
var CatalogJSON []byte

func Catalog() []Command {
	var c []Command
	if err := json.Unmarshal(CatalogJSON, &c); err != nil {
		panic(err)
	}
	return c
}
func Known(id string) bool {
	for _, c := range Catalog() {
		if c.ID == id {
			return true
		}
	}
	return false
}
func ID() string {
	b := make([]byte, 16)
	if _, e := rand.Read(b); e != nil {
		panic(e)
	}
	b[6] = (b[6] & 15) | 64
	b[8] = (b[8] & 63) | 128
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[:4], b[4:6], b[6:8], b[8:10], b[10:])
}
func ValidID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for _, i := range []int{8, 13, 18, 23} {
		if s[i] != '-' {
			return false
		}
	}
	_, e := hex.DecodeString(s[:8] + s[9:13] + s[14:18] + s[19:23] + s[24:])
	return e == nil
}
func Load(data []byte) (Pairing, error) {
	var p Pairing
	err := json.Unmarshal(data, &p)
	if err != nil {
		return p, err
	}
	if p.Role != "client" && p.Role != "agent" {
		return p, errors.New("invalid role")
	}
	return p, nil
}
func Read(path string) (Pairing, error) {
	b, e := os.ReadFile(path)
	if e != nil {
		return Pairing{}, e
	}
	return Load(b)
}
func aead(p Pairing, direction string) (cipher.AEAD, error) {
	priv, e := base64.StdEncoding.DecodeString(p.Private)
	if e != nil {
		return nil, e
	}
	pub, e := base64.StdEncoding.DecodeString(p.Peer)
	if e != nil {
		return nil, e
	}
	sk, e := ecdh.X25519().NewPrivateKey(priv)
	if e != nil {
		return nil, e
	}
	pk, e := ecdh.X25519().NewPublicKey(pub)
	if e != nil {
		return nil, e
	}
	shared, e := sk.ECDH(pk)
	if e != nil {
		return nil, e
	}
	// HKDF-SHA256 extract/expand, disjoint direction keys and protocol domain.
	h := hmac.New(sha256.New, []byte("Voyah-TBox-remote-v1"))
	h.Write(shared)
	prk := h.Sum(nil)
	h = hmac.New(sha256.New, prk)
	h.Write([]byte(direction))
	h.Write([]byte{1})
	key := h.Sum(nil)
	block, e := aes.NewCipher(key)
	if e != nil {
		return nil, e
	}
	return cipher.NewGCM(block)
}
func Seal(p Pairing, m Message) (Packet, error) {
	a, e := aead(p, p.Role)
	if e != nil {
		return Packet{}, e
	}
	n := make([]byte, a.NonceSize())
	if _, e = rand.Read(n); e != nil {
		return Packet{}, e
	}
	b, e := json.Marshal(m)
	if e != nil {
		return Packet{}, e
	}
	out := a.Seal(n, n, b, []byte("v1|"+m.ID))
	return Packet{m.ID, base64.StdEncoding.EncodeToString(out)}, nil
}
func Open(p Pairing, q Packet) (Message, error) {
	var m Message
	if !ValidID(q.ID) {
		return m, errors.New("invalid UUID")
	}
	role := "agent"
	if p.Role == "agent" {
		role = "client"
	}
	a, e := aead(p, role)
	if e != nil {
		return m, e
	}
	b, e := base64.StdEncoding.DecodeString(q.Data)
	if e != nil || len(b) < a.NonceSize() {
		return m, errors.New("invalid envelope")
	}
	b, e = a.Open(nil, b[:a.NonceSize()], b[a.NonceSize():], []byte("v1|"+q.ID))
	if e != nil {
		return m, e
	}
	e = json.Unmarshal(b, &m)
	if e == nil && m.ID != q.ID {
		e = errors.New("UUID mismatch")
	}
	return m, e
}
