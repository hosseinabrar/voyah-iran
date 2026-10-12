# Launcher map in the cluster media panel

The VIP launcher child setting is off by default. Saved settings survive entitlement loss.
The selected default map must remain on the launcher's existing virtual display. Card and
cluster share a single SurfaceTexture through an EGL relay, with no second map instance,
PixelCopy loop, CPU image conversion, or second map virtual display. The existing one-off
PixelCopy readiness check for card transfers is unchanged.

The card receives the full frame. The cluster output samples the same frame with independent
map translation/scale, then multiplies its alpha by the grayscale mask. RGB is premultiplied
by the same alpha. Outside the mask is transparent. The asset `cluster_map_mask_11.png` is
an unchanged copy of test asset `cluster_mask_11.png` (the penultimate user mask).

`ClusterMapCalibration.kt` contains independent Speedy Dash and Minimal presets. Initial
values match the test app: panel 490x506 at (90,154), offsets 0, scales 1 (100%). Offsets are
pixels in the 1920x720 output; positive X/Y means right/down. Map scale affects only the
map, mask scale only the mask, both around the panel center. The source dimensions are
the actual launcher card dimensions. Calibrate the two theme presets using the test app.

Starting a card-to-fullscreen transfer or a passenger transfer notification immediately
releases the cluster presentation. Existing task status monitoring catches external moves.
When HOME is covered, a relay already in use continues draining the same source display;
a background ownership check releases it if the map leaves. Disabling the option, disabling
the launcher, or losing VIP also releases it. The bridge expires missing app heartbeats
after 3.5 seconds. The cluster window rejects default/passenger displays and stale sessions.

The OEM MEDIA/ MEDIA_SMALL presentation API matches the cluster test app. While leased,
the custom music adapter is suspended; on release it resumes its latest valid music frame.
Factory music handling and the music checkbox retain their existing behavior. No persistent
cluster page configuration is changed.

Vehicle verification: simultaneous card/cluster orientation and mask, every theme,
switching cluster pages, fullscreen transfers to either display, return to card, setting
off/on, and music restoration. Shader/EGL and OEM firmware behavior require a vehicle test.
