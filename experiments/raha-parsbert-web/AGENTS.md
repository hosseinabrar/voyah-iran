# Raha practice workflow

For the owner's requested practice/test rounds, complete the whole cycle: freeze the pre-training results, review expected commands and parameters, teach mistakes (including UNKNOWN for invalid inputs), record semantic feedback, rerun the same cases, and check earlier regression sets. Do not stop at an untrained evaluation unless the owner explicitly asks to keep that set untrained. Preserve original baselines and distinguish post-training in-sample accuracy from generalization.

Check schema/executor equivalences before teaching. Never lower the execution confidence threshold or count a rejected valid command as success. Report fixed parser/guard blockers separately from model failures. Semantic feedback currently consists of exact-case web reviews, not a learned semantic model or an Android guard update; state that distinction accurately. Sync learned model data through the established export/parity workflow, without building, committing, or publishing unless requested.

For subsequent owner-requested practice rounds, also review reusable validator false positives/negatives. Fix demonstrated rule bugs with positive and negative regression cases, maintain Python/Android parity, and preserve the execution confidence threshold. Do not substitute exact-text semantic approvals for general rule fixes.
