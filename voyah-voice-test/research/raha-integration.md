# Raha integration contract

## Current modules

- ../voyah-iran/raha-core (Gradle :command-core): shared pure Kotlin/JVM recognition.
- com.abrar.raha.RahaCommandRecognizer: final transcript to Recognition.Matched / Recognition.Unknown.
- RahaWakeGate: selected wake phrases, one-command admission, timeout and reset. Caller supplies monotonic milliseconds.
- RahaSpeechVocabulary: manual domain grammar and joined wake-command grammar.
- ../voyah-iran/raha-runtime: shared Android recording, Vosk and experimental comparison engine code.
- MainActivity and FeedbackStore: test UI and feedback collection; not part of the portable recognition API.

The legacy com.abrar.voicecommands types are still used by the public result contract.
Keep them during the first migration to avoid breaking callers. Phase recognizers are internal.

## Execution boundary

The test app produces previews only. The main app now has a vehicle adapter (see ../../voyah-iran/RAHA.md). Adapters must accept
the action and its named parameters, execute supported operations, and report actual
success/failure separately. A successful recognition is not proof of vehicle execution.

Preserve enum names and existing parameter keys. Missing optional parameters mean
leave the corresponding setting unchanged. Relative position deltas are percentage
points from the current opening; read current state and clamp to 0..100. Headlight mode,
rear fog, child lock and central door lock are separate intents.

SET_FRAGRANCE_* uses confirmed channel numbers. CONFIGURE_FRAGRANCE can include
enabled, channel, intensity and durationMinutes together; 0 duration means unlimited.
Window singular defaults to driver and plural to all. Climate defaults to both front
zones. Seat memory is only driver / Driver profile.

Numeric sensitivity levels and model-dependent features require mapping against the
actual vehicle interface. Do not infer hardware ranges from recognition alone.

The parser deliberately follows the keyword semantics requested for the test app,
including normalization of نکن to کن. This behavior has not been redesigned in cleanup.
Review the intended production interaction policy explicitly during integration.

## Speech lifecycle

Serialize model preparation, recording, cancellation and native resource cleanup.
Never open a second microphone while an earlier session is still stopping.
Use Vosk wake grammar for continuous wake listening. Only feed admitted final command
text to the command recognizer; partial text is display-only.
MainActivity currently stops capture when leaving the foreground. Continuous service
behavior and the steering-wheel trigger belong to the main-app integration step.
Wake state is separate from microphone-open state.

No TTS or vehicle execution has been added. Keep the experimental comparison engines
and test feedback screen out of the initial product module unless needed.

## Validation and remaining device checks

Pure Kotlin regression tests cover phases 1–5, reported phrases, and wake isolation/
timeout. Native Vosk grammar load and Android speech-source compilation are checked
separately. These do not measure real microphone accuracy, UI behavior, audio focus,
car suspension/resume, or device-specific execution.

Before release, test background/foreground transitions, changing wake selections,
permission denial, manual recording during wake listening, and command timing on the
target hardware. The application has not been packaged during this cleanup.

