# Neshan compatibility catalog

Public URL: `http://voyah-iran.ir/configs/neshan.json`

Only upload the signed `neshan.json`. Do not upload `secrets/` or the unsigned source.

One-time server setup: add the `/configs/neshan.json` location from
`../nginx.conf.example` to the existing `voyah-iran.ir` server block, then:

```sh
sudo mkdir -p /var/www/voyah-iran/configs
sudo nginx -t && sudo systemctl reload nginx
```

Upload the public file to `/tmp/neshan.json`, then publish atomically:

```sh
sudo install -m 644 /tmp/neshan.json /var/www/voyah-iran/configs/neshan.json.new
sudo mv /var/www/voyah-iran/configs/neshan.json.new /var/www/voyah-iran/configs/neshan.json
curl -i http://voyah-iran.ir/configs/neshan.json
```

No Docker image rebuild or database change is needed. Keep the nginx location for future updates.
Future releases: edit `configs/neshan.source.json`, retain earlier supported profiles,
increase `revision`, then run `python tools/sign-neshan-config.py` (requires `cryptography`
on the maintainer's computer only). Upload the newly signed file to the same URL.
The private key defaults to the existing ignored `secrets/private.pem`. Back it up securely.

The first client upgrade requires a complete Windows installer run to replace the bridge.
There is deliberately no embedded version profile. Until the first successful download,
Neshan integration waits; unrelated features continue. Thereafter a verified local copy
works offline. Logcat tags: `NeshanConfig`, `NeshanDirect`, `NeshanDriving`.

The engine supports the existing typed update/reset hook contract. Changes to identifiers,
UI labels and resource IDs can be delivered as configuration. A genuinely different data
contract still requires an engine update. Unknown APK hashes never fall back to a guessed profile.

### Version-specific UI contracts (revision 6)

The updated client consumes optional per-profile data:
- adSuppression: enabled, viewIds (Android application resource IDs), viewClasses (exact class names).
- androidTheme: preferences (basename without extension), key, value (integer).

Only the SHA-256-matched profile is used. Omit a section to leave that behavior untouched;
set adSuppression.enabled to false to stop ad suppression. The bridge reloads a newly
accepted signed catalog and reattaches the hook. No executable code is accepted in these fields.
70789, 70794 and 70799 have individually verified ad selectors and Android theme preferences.
Older clients ignore these optional fields: install the updated client once before using them.
Upload deployment/configs/neshan.json; the unsigned source and private key stay local.
