RAHA TESTER SERVER — separate from the owner's personal trainer

Prepare on the laptop:
  ../experiments/raha-parsbert-web/.venv/bin/python prepare_bundle.py
The ignored bundle is a read-only snapshot of the verified Android model, vocabulary,
Vosk model and editor. It contains no training checkpoint and cannot train a model.
Never mount the personal experiment or Android source into this service.

Deploy (pending server credentials/configuration inspection):
  Transfer this directory and bundle to the server.
  Create ./data owned by uid 10001, mode 0700.
  Put SESSION_SECRET=<at least 32 random characters> in .env, mode 0600.
  Keep this secret across restarts; rotating it invalidates tester cookies.
  docker compose up -d --build
  Add nginx-location.conf to the existing HTTPS virtual host, nginx -t then reload.
  Do not replace the site's current server block or subscription service.
  Public URL: https://voyah-iran.ir/raha-train/
  Origin must match exactly; HTTP cannot access microphones remotely.

Privacy and isolation:
  Only explicitly submitted confirmation/correction text is persisted.
  Audio streams only during listening and is not saved. Microphone stays warm
  between turns and can be released with the close-microphone button.
  HttpOnly/SameSite cookie identifies one browser profile, not hardware identity.
  Clearing cookies creates another device record. Name is self-reported, not login.
  Names become sanitized folders with a hash suffix; each device has a JSONL file.
  The cookie holds no training data; device-specific exact-sentence overrides are
  derived from that device's file. They are labelled personal, not a new model score.
  Corrections do NOT generalize to other sentences or users and expire on model change.
  Four simultaneous microphone sessions, one per cookie, 30 seconds per utterance.
  No vehicle actions, global training endpoint or public data-download endpoint.
  Back up data securely; delete a tester folder and its sessions record on request.
  Public testing needs HTTPS and monitoring for abuse/disk use; names are not authentication.

Review/import on the laptop:
  Copy selected JSONL files over SSH; inspect records (all start as pending).
  python import_reviewed.py FILE --ids ID1 ID2
  Only after review, repeat with --apply-reviewed-sha256 HASH printed by the dry run.
  This calls only the personal trainer at 127.0.0.1:18766. Its existing automatic
  project sync applies approved training. The public service never does this.
  Import is explicit and not idempotent; keep a record of imported IDs.

Tests:
  ../experiments/raha-parsbert-web/.venv/bin/python -m unittest test_server
No production deployment or vehicle validation is implied by host tests.
