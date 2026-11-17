package main

import (
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"flag"
	"log"
	"os"
	"path/filepath"
	p "voyah/remote/TBox/TBox-remote/protocol"
)

func main() {
	root := flag.String("root", ".", "remote module directory")
	phone := flag.String("phone", "", "allowed SMS sender")
	url := flag.String("url", "https://remote.voyah-iran.ir", "gateway")
	flag.Parse()
	if p.NormalizePhone(*phone) == "" {
		log.Fatal("valid phone required")
	}
	c, e := ecdh.X25519().GenerateKey(rand.Reader)
	if e != nil {
		log.Fatal(e)
	}
	a, e := ecdh.X25519().GenerateKey(rand.Reader)
	if e != nil {
		log.Fatal(e)
	}
	enc := base64.StdEncoding.EncodeToString
	ct, at := p.ID()+p.ID(), p.ID()+p.ID()
	files := map[string]any{"client/web-test/pairing.local.json": p.Pairing{Private: enc(c.Bytes()), Peer: enc(a.PublicKey().Bytes()), Token: ct, Role: "client", URL: *url}, "TBox/TBox-remote/agent/pairing.local.json": p.Pairing{Private: enc(a.Bytes()), Peer: enc(c.PublicKey().Bytes()), Token: at, Role: "agent", URL: *url, Phone: p.NormalizePhone(*phone)}, "server/credentials.local.json": map[string]string{"client": ct, "agent": at}}
	for name := range files {
		if _, err := os.Stat(filepath.Join(*root, name)); !os.IsNotExist(err) {
			log.Fatal("Refusing to replace existing pairing: ", name)
		}
	}
	for name, v := range files {
		b, e := json.MarshalIndent(v, "", "  ")
		if e != nil {
			log.Fatal(e)
		}
		if e = os.WriteFile(filepath.Join(*root, name), b, 0600); e != nil {
			log.Fatal(e)
		}
	}
	log.Print("Local pairing generated; private keys stay on their respective endpoints")
}
