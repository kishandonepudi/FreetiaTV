---
name: release-gate
description: Run the Freetia release gate (standards/RELEASE_GATE.md) — deterministic checks plus brand/UX, code/security and performance reviewer agents in parallel — and write the checkpoint that unlocks release APK/AAB builds. Use before building any release APK/AAB, opening a PR to main, or tagging; also when the user asks to "check everything" or "run the gate".
---

# Release gate

Follow `standards/RELEASE_GATE.md` exactly.

1. **Change set.** If git is initialised, use `git diff --name-only main...HEAD` plus uncommitted files. Otherwise, use files under `app/`, `standards/` and `scripts/` modified since `.claude/checkpoints/release-gate.json` (or all of `app/src` if there's no checkpoint).
2. **Deterministic checks.** Run `powershell -ExecutionPolicy Bypass -File scripts/quality-gate.ps1` (add `-Fast` only if the user asked for a quick pass). On failure, fix the cause and rerun. Don't continue while it's red.
3. **Agent reviews, in parallel.** In a single message, start three Agent calls with these `subagent_type` values:
   - `brand-ux-reviewer`
   - `code-security-reviewer`
   - `performance-reviewer`

   Give each one the change set and a one-line summary of the change. If the change touches UI and the TV emulator is running, take screenshots (`adb exec-out screencap -p`) and pass their paths to `brand-ux-reviewer`.
4. **Resolve.** For every FAIL finding, verify it against the code, fix it, then repeat steps 2–3 for the affected reviewers. Ask the user before dismissing any finding.
5. **Checkpoint.** When every check passes, write `.claude/checkpoints/release-gate.json`:
   ```json
   {"time": "<ISO-8601>", "mode": "full|fast", "commit": "<git sha or 'no-git'>",
    "script": "<copy of build/quality-gate.json pass/checks>",
    "reviews": {"brand-ux": "PASS", "code-security": "PASS", "performance": "PASS"},
    "changeSet": ["..."]}
   ```
6. **Report** a short table of checkpoints C1–C12 and their status, plus anything that needs a human: a manual D-pad pass, trademark search, or testing on a real device.

The `apk-gate` hook only allows release/bundle Gradle tasks when this checkpoint is newer than every source file, so any later edit means running the gate again.
