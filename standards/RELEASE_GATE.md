# Release gate and checkpoints

Version 1.0 (14 Nov 2026). No APK or AAB leaves a developer machine or CI unless every checkpoint below passes.

## When to run it
| Event | What runs |
|---|---|
| Every change you're about to hand back (AI agents included) | `scripts/quality-gate.ps1 -Fast` (unit tests and static checks) |
| Before any release APK or AAB, PR to `main`, or tag | Full gate: `/release-gate` in Claude Code, or `scripts/quality-gate.ps1` followed by the three reviewer agents |
| CI on every push | `scripts/quality-gate.sh` |

## Checkpoints
| ID | Checkpoint | Checked by |
|---|---|---|
| C1 | Debug build compiles for `foss` and `store` | script |
| C2 | Unit tests pass | script |
| C3 | Lint reports 0 errors | script |
| C4 | `store` flavor has no default playlist URL | script |
| C5 | Brand assets present; adaptive icon uses the brand background and foreground; in-app header uses `ic_brand_mark` | script, then `brand-ux-reviewer` |
| C6 | No secrets, keystores or URL logging in source | script (grep), then `code-security-reviewer` |
| C7 | Manifest: `allowBackup=false`; exported components justified; no new permissions without a reason | script, then `code-security-reviewer` |
| C8 | Brand kit checklist B1–B8 | `brand-ux-reviewer` |
| C9 | Engineering checklist E1–E8 and security checklist S1–S12 | `code-security-reviewer` |
| C10 | Memory rules M1–M9 and performance targets not regressed | `performance-reviewer` |
| C11 | Release build (R8) compiles; release APK < 15 MB | script (full mode) |
| C12 | Manual D-pad pass on the TV emulator for UI changes | human or agent with emulator screenshots |

## Agentic workflow (what `/release-gate` does)
1. Collect the change set: `git diff --name-only main...HEAD`, or every file changed since the last checkpoint if git isn't set up yet.
2. Run `scripts/quality-gate.ps1` (C1–C7, C11). Stop on failure and fix it first.
3. Start `brand-ux-reviewer`, `code-security-reviewer` and `performance-reviewer` **in parallel**, each with the change set. Each returns `PASS` or `FAIL` with findings tied to checkpoint IDs.
4. Any `FAIL`: fix the issue, then repeat from step 2. Never mark a finding "won't fix" without the user's approval.
5. All `PASS`: write `.claude/checkpoints/release-gate.json` with the time, commit, checkpoint results and reviewer verdicts.
6. Release builds are now allowed. The `apk-gate` hook checks that the checkpoint is newer than every file in `app/src`, `app/build.gradle.kts` and `standards/`.

## Escalation
- A failing checkpoint that you believe is wrong: change the standard in the same PR, explain why, and get human approval.
- A security finding of high severity: stop. No release until it's fixed, whatever the deadline.
