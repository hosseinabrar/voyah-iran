# Old Raha recognition workflow

For reported command failures, fix reusable parsing rules where possible; preserve explicit
object, target, direction, and amount rather than relying only on exact sentences. Add varied,
reviewed everyday examples (including harmless filler words and word-order variants) to the
shared lesson database. Record the pre-change result, test the rule fix before importing its
examples, and retest afterward. Keep separate rule-only tests with an empty lesson database.

Recognition order is exact normalized database lookup first (including explicit UNKNOWN
examples), then deterministic logical parsing/repairs when there is no exact entry. Never bypass
an exact correction with a rule; repair stale annotations as data when needed. The web and Android
must use the same Kotlin recognizer and exported lesson resource.
