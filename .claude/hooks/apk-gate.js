#!/usr/bin/env node
// PreToolUse hook (standards/RELEASE_GATE.md): blocks Gradle tasks that build release variants unless
// .claude/checkpoints/release-gate.json records a passing gate and is newer than every gated input.
// Fails closed: any error denies, because a non-zero exit would not block the tool call.
//
// Checkpoint schema (written by /release-gate, see .claude/skills/release-gate/SKILL.md):
//   { "time": ISO-8601, "mode": "full|fast", "commit": "...",
//     "script": { "pass": true, "checks": {...} },          // copy of build/quality-gate.json
//     "reviews": { "brand-ux": "PASS", "code-security": "PASS", "performance": "PASS" },
//     "changeSet": [...] }
// Allowed only when script.pass === true and reviews is a non-empty object whose values are all "PASS".
// scripts/quality-gate.* are the gate itself and are not blocked; only direct gradle/gradlew invocations are.
const fs = require('fs');
const path = require('path');

const root = process.env.CLAUDE_PROJECT_DIR || path.resolve(__dirname, '..', '..');

function deny(reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: reason },
  }));
  process.exit(0);
}
process.on('uncaughtException', (e) => deny(`apk-gate hook error (failing closed): ${e && e.message}`));

const GRADLE_EXE = /^gradlew(\.bat|\.cmd)?$|^gradle(\.bat|\.cmd|\.exe)?$/i;
const BARE_RELEASE = /^(assemble|build|install|bundle|publish|package)$/i;
// A trailing capital R/Re/Rel... (prefix of "Release") catches abbreviations such as aFR or assembleFRel.
const RELEASE_ABBREV = /R(e|el|ele|elea|eleas)?$/;

function releaseTasks(command) {
  const found = [];
  // Segment on shell separators so tokens after `&& ls build` are not mistaken for Gradle tasks.
  for (const segment of command.split(/[;&|\r\n]+/)) {
    const tokens = segment.replace(/["'`()]/g, ' ').split(/[\s,]+/).filter(Boolean);
    const idx = tokens.findIndex((t) => GRADLE_EXE.test(t.split(/[\\/]/).pop()));
    if (idx < 0) continue;
    for (const raw of tokens.slice(idx + 1)) {
      if (raw.startsWith('-')) continue;
      const task = raw.split(':').pop();
      if (!task) continue;
      if (BARE_RELEASE.test(task) || /release/i.test(task) || /^(bundle|publish)/i.test(task) || RELEASE_ABBREV.test(task)) {
        found.push(raw);
      }
    }
  }
  return found;
}

function newestMtime(paths) {
  let best = { mtime: 0, file: '' };
  const walk = (p) => {
    if (!fs.existsSync(p)) return;
    const st = fs.statSync(p);
    if (st.isDirectory()) {
      for (const e of fs.readdirSync(p)) if (e !== 'build') walk(path.join(p, e));
    } else if (st.mtimeMs > best.mtime) best = { mtime: st.mtimeMs, file: p };
  };
  paths.forEach(walk);
  return best;
}

function check(command) {
  const tasks = releaseTasks(command);
  if (tasks.length === 0) process.exit(0);

  const checkpoint = path.join(root, '.claude', 'checkpoints', 'release-gate.json');
  if (!fs.existsSync(checkpoint)) {
    deny(`No release-gate checkpoint (release task: ${tasks.join(' ')}). Run /release-gate (standards/RELEASE_GATE.md) before building a release APK/AAB.`);
  }
  let gate;
  try {
    gate = JSON.parse(fs.readFileSync(checkpoint, 'utf8').replace(/^\uFEFF/, ''));
  } catch (e) {
    deny(`Release-gate checkpoint is unreadable or corrupt (${e.message}). Rerun /release-gate.`);
  }
  const reviews = gate && gate.reviews;
  const reviewsOk = reviews && typeof reviews === 'object' && !Array.isArray(reviews) &&
    Object.keys(reviews).length > 0 && Object.values(reviews).every((v) => v === 'PASS');
  if (!reviewsOk) deny('Last release gate did not record a PASS from every reviewer. Fix findings and rerun /release-gate.');
  if (!gate.script || gate.script.pass !== true) {
    deny('Last release gate has no passing script result (script.pass must be true). Rerun scripts/quality-gate.ps1 and /release-gate.');
  }

  const newest = newestMtime([
    'app/src', 'app/build.gradle.kts', 'app/proguard-rules.pro', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle.properties', 'gradle/libs.versions.toml', 'standards', 'scripts',
  ].map((p) => path.join(root, p)));
  if (fs.statSync(checkpoint).mtimeMs < newest.mtime) {
    deny(`Release gate is stale: ${path.relative(root, newest.file)} changed after the last gate. Rerun /release-gate.`);
  }
  process.exit(0);
}

let input = '';
process.stdin.on('data', (c) => (input += c));
process.stdin.on('end', () => {
  let command;
  try {
    const parsed = JSON.parse(input);
    command = (parsed && parsed.tool_input && parsed.tool_input.command) || '';
  } catch (e) {
    deny(`apk-gate hook could not parse its input (failing closed): ${e.message}`);
  }
  check(String(command));
});
