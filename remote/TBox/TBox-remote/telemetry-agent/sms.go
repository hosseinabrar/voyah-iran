package main

import (
	"context"
	"errors"
	"os"
	"time"
	"voyah/remote/internal/sms"
	t "voyah/remote/internal/telemetry"
)

func (s *service) executeSMS(q t.Request) (string, *sms.Result) {
	if s.messages == nil {
		return "sms_unavailable", nil
	}
	if q.Action == "send" {
		if s.smsSend == nil {
			return "sms_unavailable", nil
		}
		ctx := s.ctx
		if ctx == nil {
			ctx = context.Background()
		}
		err := s.smsSend(ctx, q.Recipient, q.Body)
		switch {
		case err == nil:
			return "ok", &sms.Result{ObservedAt: time.Now().UnixMilli()}
		case errors.Is(err, sms.ErrInvalid):
			return "invalid_sms", nil
		case errors.Is(err, sms.ErrUnknown):
			return "send_unknown", nil
		case errors.Is(err, sms.ErrRejected):
			return "send_failed", nil
		default:
			return "sms_unavailable", nil
		}
	}
	result, err := s.messages.Query(q.Action, q.MessageID, q.Recipient, q.Cursor)
	if errors.Is(err, os.ErrNotExist) {
		return "message_missing", nil
	}
	if errors.Is(err, sms.ErrInvalid) {
		return "invalid_sms", nil
	}
	if err != nil {
		return "storage_failed", nil
	}
	return "ok", result
}
