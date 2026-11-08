# Feature subscription policy

## Neshan version compatibility

Keep every Neshan-specific class, method, field, resource selector, and other version-dependent identifier in the signed, APK-matched Neshan configuration, never hardcoded in the client hook. Verify selectors against each supported APK; omit unsupported contracts rather than guessing. Version/obfuscation-only changes must be deliverable through configuration without a Voyah Iran app update. Generic Android APIs and our own display identifiers are not Neshan selectors.

## Launcher artwork performance

Bake fixed lighting, shadows, hue/saturation variants, theme tints, and decorative gradients into project assets offline. Do not reconstruct these in onDraw or at launcher startup. Load immutable artwork asynchronously and cache it. Keep runtime drawing for actual state, text, progress, and transitions; preserve approved geometry and verify both themes with render tests. Offline asset authoring tests must be disabled by default and never packaged in the app.

## End-user interface copy

Keep titles and descriptions focused on what a control does for the end user. Do not expose implementation details, CAN/state values, protocol names, experimental/tester notes, or developer-facing explanations in ordinary UI copy. The project owner provides tester instructions separately. Preserve actionable user guidance and necessary safety warnings, and keep technical details in logs/developer documentation. When asked to audit copy, propose replacements and wait for approval before applying them.

## Release branch preparation

When creating a new release branch, first align Android versionName and installer Version/InformationalVersion with the release number, and increment Android versionCode. Verify macOS bundle metadata and Windows output naming use the matching version sources. Do not build or commit unless requested.

Before implementing a genuinely new user-facing feature/control (such as a new option in Voyah Iran), ask whether it belongs to the ordinary or VIP subscription unless already specified. Extending an existing feature, including adding commands to Raha, inherits that feature's established subscription policy; do not ask again. Existing feature fixes retain their assigned tier.

VIP restrictions must preserve saved user preferences: mask unavailable controls without clearing stored settings, and resume those settings after a valid VIP entitlement returns. Enforce entitlement at runtime as well as in the UI.

# Installer platform parity

For every installer change, inspect both the Windows and macOS installer paths. Apply shared behavior, fixes, payload updates, and validation to both platforms, preferably through their shared core and patch sources. Platform-specific features (such as macOS Universal packaging) remain platform-specific; explicitly verify that they do not break the other installer. Distinguish source coverage from rebuilt/tested release artifacts; never claim a Windows runtime test based only on macOS validation.

## Raha web / Android parity

Keep recognition acceptance, numeric grounding, and semantic guards equivalent between the owner's web test and Android. Every guard change requires matching implementation and shared positive/negative parity tests. Human semantic reviews are pending evidence for shared rule changes, never web-only execution overrides. Sync model changes through the existing export workflow; do not confuse model confidence, guard acceptance, or exact-case training accuracy.

## T-box enablement research handoff

Before continuing T-box radio/network/signal integration, read `docs/tbox-enablement-handoff-20261108.txt` and its linked local runbook. The successful H97X experiment is firmware-hash-specific; product integration is deferred. Keep private dumps/binaries outside the repository and do not assume hard-brick recovery or cross-model compatibility.
