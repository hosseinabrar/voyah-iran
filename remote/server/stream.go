package main

import (
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	t "voyah/remote/internal/telemetry"
)

const streamIdle = 20 * time.Second

var streamUpgrade = websocket.Upgrader{HandshakeTimeout: 5 * time.Second, ReadBufferSize: 1024, WriteBufferSize: 1024}

type relayPeer struct {
	conn *websocket.Conn
	out  chan t.Envelope
	done chan struct{}
	once sync.Once
}

func (p *relayPeer) close() { p.once.Do(func() { close(p.done); p.conn.Close() }) }
func (p *relayPeer) writeLoop() {
	defer p.close()
	for {
		select {
		case <-p.done:
			return
		case packet := <-p.out:
			p.conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
			if p.conn.WriteJSON(packet) != nil {
				return
			}
		}
	}
}
func validEnvelope(p t.Envelope) bool {
	return len(p.Vehicle) == 64 && len(p.ID) == 43 && len(p.Device) > 0 && len(p.Device) <= 64 && len(p.Data) > 0 && len(p.Data) <= 24000
}

// Called with relay mutex held; a full queue disconnects a stalled consumer.
func (r *remoteRelay) dispatch(j *remoteJob) {
	if p := r.agents[j.packet.Vehicle]; p != nil && !j.delivered {
		select {
		case p.out <- j.packet:
			j.delivered = true
		default:
			p.close()
		}
	}
}
func (r *remoteRelay) addJob(p t.Envelope) *remoteJob {
	r.mu.Lock()
	defer r.mu.Unlock()
	key := p.Vehicle + "/" + p.ID
	count := 0
	for _, j := range r.jobs {
		if j.packet.Vehicle == p.Vehicle {
			count++
		}
	}
	if count >= 12 || len(r.jobs) >= 2048 || r.jobs[key] != nil {
		return nil
	}
	j := &remoteJob{packet: p, reply: make(chan t.Envelope, 1)}
	r.jobs[key] = j
	r.dispatch(j)
	return j
}
func (r *remoteRelay) removeJob(j *remoteJob) {
	r.mu.Lock()
	defer r.mu.Unlock()
	key := j.packet.Vehicle + "/" + j.packet.ID
	if r.jobs[key] == j {
		delete(r.jobs, key)
	}
}
func (r *remoteRelay) deliver(p t.Envelope) {
	r.mu.Lock()
	defer r.mu.Unlock()
	j := r.jobs[p.Vehicle+"/"+p.ID]
	if j != nil && p.Device == j.packet.Device {
		select {
		case j.reply <- p:
		default:
		}
	}
}
func (r *remoteRelay) stream(w http.ResponseWriter, q *http.Request) {
	agent := q.URL.Path == "/v2/stream/agent"
	vehicle := ""
	if agent {
		vehicle = routeOwner(q)
		if vehicle == "" {
			http.Error(w, "unauthorized", 401)
			return
		}
	}
	r.mu.Lock()
	if r.connections >= 1024 {
		r.mu.Unlock()
		http.Error(w, "busy", 429)
		return
	}
	r.connections++
	r.mu.Unlock()
	defer func() { r.mu.Lock(); r.connections--; r.mu.Unlock() }()
	c, err := streamUpgrade.Upgrade(w, q, nil)
	if err != nil {
		return
	}
	p := &relayPeer{conn: c, out: make(chan t.Envelope, 12), done: make(chan struct{})}
	defer p.close()
	c.SetReadLimit(32768)
	go p.writeLoop()
	if agent {
		r.mu.Lock()
		if r.agents == nil {
			r.agents = map[string]*relayPeer{}
		}
		old := r.agents[vehicle]
		r.agents[vehicle] = p
		for _, j := range r.jobs {
			if j.packet.Vehicle == vehicle {
				r.dispatch(j)
			}
		}
		r.mu.Unlock()
		if old != nil {
			old.close()
		}
		defer func() {
			r.mu.Lock()
			if r.agents[vehicle] == p {
				delete(r.agents, vehicle)
			}
			r.mu.Unlock()
		}()
	}
	device := ""
	for {
		idle := r.idle
		if idle == 0 {
			idle = streamIdle
		}
		c.SetReadDeadline(time.Now().Add(idle))
		var packet t.Envelope
		if c.ReadJSON(&packet) != nil || !validEnvelope(packet) {
			return
		}
		if agent {
			if packet.Vehicle != vehicle {
				return
			}
			r.deliver(packet)
			continue
		}
		if vehicle == "" {
			vehicle = packet.Vehicle
			device = packet.Device
		}
		if packet.Vehicle != vehicle || packet.Device != device {
			return
		}
		j := r.addJob(packet)
		if j == nil {
			return
		}
		go func() {
			defer r.removeJob(j)
			timer := time.NewTimer(8 * time.Second)
			defer timer.Stop()
			select {
			case reply := <-j.reply:
				select {
				case p.out <- reply:
				case <-p.done:
				default:
					p.close()
				}
			case <-timer.C:
			case <-p.done:
			}
		}()
	}
}
