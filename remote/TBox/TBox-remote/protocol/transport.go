package protocol

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

var HTTP = &http.Client{Timeout: 12 * time.Second, CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return errors.New("redirect refused") }}

func Call(ctx context.Context, p Pairing, path string, in, out any) error {
	if !strings.HasPrefix(p.URL, "https://") && !strings.HasPrefix(p.URL, "http://127.0.0.1:") {
		return errors.New("HTTPS required")
	}
	b, e := json.Marshal(in)
	if e != nil {
		return e
	}
	r, e := http.NewRequestWithContext(ctx, "POST", strings.TrimRight(p.URL, "/")+path, bytes.NewReader(b))
	if e != nil {
		return e
	}
	r.Header.Set("Authorization", "Bearer "+p.Token)
	r.Header.Set("Content-Type", "application/json")
	client := *HTTP
	if p.RootCA != "" {
		roots := x509.NewCertPool()
		if !roots.AppendCertsFromPEM([]byte(p.RootCA)) {
			return errors.New("invalid pinned gateway certificate")
		}
		tr := http.DefaultTransport.(*http.Transport).Clone()
		tr.TLSClientConfig = &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12}
		if p.RelayIP != "" {
			u, _ := url.Parse(p.URL)
			tr.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
				host, port, err := net.SplitHostPort(addr)
				if err != nil {
					return nil, err
				}
				if host == u.Hostname() {
					addr = net.JoinHostPort(p.RelayIP, port)
				}
				return (&net.Dialer{Timeout: 5 * time.Second}).DialContext(ctx, network, addr)
			}
		}
		client.Transport = tr
		defer tr.CloseIdleConnections()
	}
	resp, e := client.Do(r)
	if e != nil {
		return e
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("gateway HTTP %d", resp.StatusCode)
	}
	if out == nil {
		return nil
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 65536)).Decode(out)
}
