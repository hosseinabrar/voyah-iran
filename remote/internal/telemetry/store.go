package telemetry

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type Device struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Key  string `json:"key,omitempty"`
}
type Invitation struct {
	ID      string `json:"id"`
	Secret  string `json:"secret"`
	Expires int64  `json:"expires"`
	Name    string `json:"name"`
}
type State struct {
	RelayToken   string           `json:"relayToken"`
	Vehicle      string           `json:"vehicle"`
	Private      string           `json:"private"`
	Public       string           `json:"public"`
	RemoteAccess bool             `json:"remoteAccess"`
	Expires      int64            `json:"expires"`
	Devices      []Device         `json:"devices"`
	Invitations  []Invitation     `json:"invitations"`
	Seen         map[string]int64 `json:"seen"`
}
type Store struct {
	Mu    sync.Mutex
	State State
	Path  string
}

func Load(path string) (*Store, error) {
	s := &Store{Path: path}
	b, e := os.ReadFile(path)
	if e == nil {
		e = json.Unmarshal(b, &s.State)
		if e != nil {
			return nil, e
		}
	} else if os.IsNotExist(e) {
		k, e := GenerateKey()
		if e != nil {
			return nil, e
		}
		token := Random()
		hash := sha256.Sum256([]byte(token))
		s.State = State{RelayToken: token, Vehicle: hex.EncodeToString(hash[:]), Private: Encoding.EncodeToString(k.Bytes()), Public: Encoding.EncodeToString(k.PublicKey().Bytes()), Devices: []Device{}, Seen: map[string]int64{}}
		if e = s.Save(); e != nil {
			return nil, e
		}
	} else {
		return nil, e
	}
	if s.State.Seen == nil {
		s.State.Seen = map[string]int64{}
	}
	return s, nil
}

// Caller holds Mu. Atomic durable replacement precedes acceptance/revocation.
func (s *Store) Save() (err error) {
	defer func() {
		if err != nil {
			if b, e := os.ReadFile(s.Path); e == nil {
				var saved State
				if json.Unmarshal(b, &saved) == nil {
					s.State = saved
				}
			}
		}
	}()

	if e := os.MkdirAll(filepath.Dir(s.Path), 0700); e != nil {
		return e
	}
	b, e := json.Marshal(s.State)
	if e != nil {
		return e
	}
	f, e := os.OpenFile(s.Path+".new", os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if e != nil {
		return e
	}
	_, e = f.Write(b)
	if e == nil {
		e = f.Sync()
	}
	ce := f.Close()
	if e == nil {
		e = ce
	}
	if e != nil {
		return e
	}
	if e = os.Rename(s.Path+".new", s.Path); e != nil {
		return e
	}
	d, e := os.Open(filepath.Dir(s.Path))
	if e != nil {
		return e
	}
	defer d.Close()
	return d.Sync()
}
func (s *Store) Allowed(now time.Time) bool {
	return s.State.RemoteAccess && now.Unix() < s.State.Expires
}
func (s *Store) Invite(name string, now time.Time) (Invitation, error) {
	active := []Invitation{}
	for _, i := range s.State.Invitations {
		if i.Expires > now.Unix() {
			active = append(active, i)
		}
	}
	if len(s.State.Devices)+len(active) >= 3 {
		return Invitation{}, errors.New("three device limit")
	}
	i := Invitation{Random(), Random(), now.Add(3 * time.Minute).Unix(), name}
	s.State.Invitations = append(active, i)
	return i, s.Save()
}
func (s *Store) Pair(id, secret, public string, now time.Time) (Device, error) {
	if !s.Allowed(now) {
		return Device{}, errors.New("subscription_expired")
	}
	for n, i := range s.State.Invitations {
		if i.ID == id && i.Secret == secret && i.Expires > now.Unix() {
			if len(s.State.Devices) >= 3 {
				return Device{}, errors.New("device limit")
			}
			sk, e := Encoding.DecodeString(s.State.Private)
			if e != nil {
				return Device{}, e
			}
			pk, e := Encoding.DecodeString(public)
			if e != nil {
				return Device{}, e
			}
			key, e := Shared(sk, pk)
			if e != nil {
				return Device{}, e
			}
			d := Device{Random(), i.Name, Encoding.EncodeToString(key)}
			s.State.Devices = append(s.State.Devices, d)
			s.State.Invitations = append(s.State.Invitations[:n], s.State.Invitations[n+1:]...)
			return d, s.Save()
		}
	}
	return Device{}, errors.New("invalid invitation")
}
