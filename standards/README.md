# Freetia standards

These documents are the rules for every app released under the Freetia brand. They apply to people and to AI coding agents alike. If a rule here conflicts with code, the rule wins: fix the code or change the rule in the same change.

| Document | Read it before you… |
|---|---|
| [BRAND_KIT.md](BRAND_KIT.md) | touch any screen, icon, colour, text style, store asset or copy |
| [ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md) | write or change Kotlin, Gradle, resources or tests |
| [SECURITY_STANDARDS.md](SECURITY_STANDARDS.md) | handle URLs, credentials, storage, network, logging, permissions or exports |
| [RELEASE_GATE.md](RELEASE_GATE.md) | build an APK/AAB, open a PR or tag a release |

## How the gate works
1. **Deterministic checks:** `scripts/quality-gate.ps1` (Windows) or `scripts/quality-gate.sh` (CI and Linux/macOS). These run unit tests, lint, the brand asset checks and the security greps.
2. **Agent review:** three reviewer agents in `.claude/agents/`:
   - `brand-ux-reviewer`
   - `code-security-reviewer`
   - `performance-reviewer`

   The `/release-gate` skill (`.claude/skills/release-gate/`) runs them in parallel on the changed files and records their verdicts.
3. **Checkpoint file:** a passing gate writes `.claude/checkpoints/release-gate.json`.
4. **Hook:** `.claude/hooks/apk-gate.js` blocks release and bundle builds in Claude Code unless the checkpoint is newer than every source file.

CI runs step 1 on every push. Steps 2 and 3 run locally before any release build.

## Changing a standard
Edit the document and bump its "Version" line in the same change. Then rerun the gate, because a stricter rule can make existing code fail.
