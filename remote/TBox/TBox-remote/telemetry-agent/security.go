package main

// Small authenticated envelopes carry bounded chunks; photos never enter the
// relay's disk or a gallery. Jobs are device-bound and single-use at the worker.
import (
	"encoding/base64"
	"math"
	"sync"
	"time"
	t "voyah/remote/internal/telemetry"
)

const photoMax = 1500000
const photoChunk = 8192

type vehicleLocation struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	At        int64   `json:"at"`
	Accuracy  float64 `json:"accuracy,omitempty"`
}
type photoJob struct {
	ID, Owner string
	Camera    int
	Created   time.Time
	Claimed   bool
	State     string
	Data      []byte
	Hash      string
}
type securityService struct {
	mu       sync.Mutex
	location *vehicleLocation
	dvr      *bool
	at       int64
	lease    time.Time
	job      *photoJob
}

func (s *securityService) expire(now time.Time) {
	if s.job != nil && now.Sub(s.job.Created) > 90*time.Second {
		s.job = nil
	}
	if s.job != nil && s.job.State == "pending" && now.Sub(s.job.Created) > 35*time.Second {
		s.job.State = "failed"
	}
}
func (s *securityService) execute(owner string, q t.Request) (string, map[string]any) {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	s.expire(now)
	r := map[string]any{"observedAt": now.UnixMilli()}
	switch q.Action {
	case "state":
		s.lease = now.Add(15 * time.Second)
		r["location"] = s.location
		r["dvr"] = s.dvr
	case "capture":
		if q.Camera < 1 || q.Camera > 3 {
			return "invalid_camera", nil
		}
		if s.dvr == nil || !*s.dvr || now.UnixMilli()-s.at > 15000 {
			return "camera_unavailable", nil
		}
		if s.job != nil {
			return "camera_busy", nil
		}
		s.lease = now.Add(45 * time.Second)
		s.job = &photoJob{ID: t.Random(), Owner: owner, Camera: q.Camera, Created: now, State: "pending"}
		r["job"] = s.job.ID
		id := s.job.ID
		time.AfterFunc(90*time.Second, func() {
			s.mu.Lock()
			defer s.mu.Unlock()
			if s.job != nil && s.job.ID == id {
				s.job = nil
			}
		})
	case "result", "chunk", "release":
		j := s.job
		if j == nil || j.Owner != owner || j.ID != q.Job {
			return "job_missing", nil
		}
		if q.Action == "release" {
			s.job = nil
			return "ok", r
		}
		r["state"] = j.State
		if j.State == "ready" {
			r["size"] = len(j.Data)
			r["sha256"] = j.Hash
		}
		if q.Action == "chunk" {
			if j.State != "ready" || q.Offset < 0 || q.Offset >= len(j.Data) || q.Offset%photoChunk != 0 {
				return "invalid_chunk", nil
			}
			end := q.Offset + photoChunk
			if end > len(j.Data) {
				end = len(j.Data)
			}
			r["data"] = base64.StdEncoding.EncodeToString(j.Data[q.Offset:end])
		}
	default:
		return "unsupported", nil
	}
	return "ok", r
}
func validLocation(p *vehicleLocation, now int64) bool {
	return p != nil && !math.IsNaN(p.Latitude) && !math.IsInf(p.Latitude, 0) && !math.IsNaN(p.Longitude) && !math.IsInf(p.Longitude, 0) && math.Abs(p.Latitude) <= 90 && math.Abs(p.Longitude) <= 180 && p.At > 0 && p.At <= now+5000 && p.Accuracy >= 0 && p.Accuracy <= 10000
}

func (s *securityService) revoke(owner string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if owner == "" || s.job != nil && s.job.Owner == owner {
		s.job = nil
	}
	if owner == "" {
		s.lease = time.Time{}
	}
}
