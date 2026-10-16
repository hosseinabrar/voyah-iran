# Feature subscription policy

## End-user interface copy

Keep titles and descriptions focused on what a control does for the end user. Do not expose implementation details, CAN/state values, protocol names, experimental/tester notes, or developer-facing explanations in ordinary UI copy. The project owner provides tester instructions separately. Preserve actionable user guidance and necessary safety warnings, and keep technical details in logs/developer documentation. When asked to audit copy, propose replacements and wait for approval before applying them.

## Release branch preparation

When creating a new release branch, first align Android versionName and installer Version/InformationalVersion with the release number, and increment Android versionCode. Verify macOS bundle metadata and Windows output naming use the matching version sources. Do not build or commit unless requested.

Before implementing any newly requested feature, ask the user whether it belongs to the ordinary or VIP subscription unless they already specified its tier. Do not assume a tier for new features. Existing feature fixes retain their assigned tier.

VIP restrictions must preserve saved user preferences: mask unavailable controls without clearing stored settings, and resume those settings after a valid VIP entitlement returns. Enforce entitlement at runtime as well as in the UI.
