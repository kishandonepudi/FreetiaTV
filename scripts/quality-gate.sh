#!/usr/bin/env bash
# Deterministic release-gate checks for CI (standards/RELEASE_GATE.md, C2-C7). Mirrors scripts/quality-gate.ps1.
# CI builds/lints separately; this script covers tests plus the static brand/security/manifest checks.
set -euo pipefail
cd "$(dirname "$0")/.."
GRADLE="${GRADLE:-gradle}"
SRC=app/src/main
PKG=com.freetia.tv
fail=0
pass() { echo "[$1] $2 ... PASS${3:+ ($3)}"; }
bad()  { echo "[$1] $2 ... FAIL: $3"; fail=1; }

# C2: results are read only from this task's directory so stale results from other variants can't count.
if $GRADLE testFossDebugUnitTest -q; then
  results=app/build/test-results/testFossDebugUnitTest
  tests=0
  for x in "$results"/TEST-*.xml; do
    [ -f "$x" ] || continue
    n=$(grep -o '<testsuite [^>]*' "$x" | grep -o ' tests="[0-9]*"' | head -1 | tr -dc '0-9')
    tests=$((tests + ${n:-0}))
  done
  if [ "$tests" -gt 0 ]; then pass C2 "Unit tests" "$tests tests"; else bad C2 "Unit tests" "no unit tests ran ($results is empty)"; fi
else bad C2 "Unit tests" "gradle failed"; fi

# C4: tag each line of app/build.gradle.kts as store block (S), foss block (F) or outside both (O), honouring nesting.
tagged=$(awk '
  depth == 0 && /create\("store"\)[[:space:]]*\{/ { tag = "S" }
  depth == 0 && /create\("foss"\)[[:space:]]*\{/  { tag = "F" }
  {
    print (tag ? tag : "O") ":" $0
    if (tag) {
      n = split($0, ch, "")
      for (i = 1; i <= n; i++) { if (ch[i] == "{") depth++; else if (ch[i] == "}") depth-- }
      if (depth <= 0) { depth = 0; tag = "" }
    }
  }' app/build.gradle.kts)
store=$(printf '%s\n' "$tagged" | sed -n 's/^S://p' | tr '\n' ' ')
outside=$(printf '%s\n' "$tagged" | sed -n 's/^O://p')
fields=$(printf '%s' "$store" | grep -oE 'buildConfigField\([[:space:]]*"[^"]*"[[:space:]]*,[[:space:]]*"DEFAULT_PLAYLIST_[A-Za-z0-9_]+"[[:space:]]*,[[:space:]]*"([^"\\]|\\.)*"[[:space:]]*\)' || true)
c4=""
if [ -z "$store" ]; then c4="store flavor block not found"
elif [ -z "$fields" ]; then c4="store flavor defines no DEFAULT_PLAYLIST_* fields"
elif ! printf '%s\n' "$fields" | grep -q '"DEFAULT_PLAYLIST_URL"'; then c4="store flavor does not define DEFAULT_PLAYLIST_URL"
else
  # The Kotlin literal for an empty Java string is "\"\"", i.e. the raw text \"\".
  nonempty=$(printf '%s\n' "$fields" | grep -vE ',[[:space:]]*"\\"\\""[[:space:]]*\)$' | grep -oE 'DEFAULT_PLAYLIST_[A-Za-z0-9_]+' | tr '\n' ' ' || true)
  [ -n "$nonempty" ] && c4="store flavor sets non-empty $nonempty"
  inherited=$(printf '%s\n' "$outside" | grep -oE 'DEFAULT_PLAYLIST_[A-Za-z0-9_]+' | head -1 || true)
  [ -n "$inherited" ] && c4="$inherited is defined outside the flavor blocks (store would inherit it)"
fi
if [ -z "$c4" ]; then pass C4 "Store flavor has no default playlist"; else bad C4 "Store flavor has no default playlist" "$c4"; fi

c5=""
for f in drawable/ic_brand_mark.xml drawable/ic_launcher_foreground.xml drawable/ic_launcher_background.xml drawable/tv_banner.xml; do
  [ -f "$SRC/res/$f" ] || c5="missing res/$f"
done
grep -q ic_launcher_background "$SRC/res/mipmap-anydpi-v26/ic_launcher.xml" || c5="adaptive icon lacks brand background"
grep -rq 'R\.drawable\.ic_brand_mark' "$SRC/kotlin" || c5="no screen shows ic_brand_mark"
if [ -z "$c5" ]; then pass C5 "Brand assets in place"; else bad C5 "Brand assets in place" "$c5"; fi

c6=""
if find . -path ./build -prune -o -path ./.gradle -prune -o -path '*/build' -prune -o \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name google-services.json \) -print | grep -q .; then
  c6="secret files in tree"
fi
# Whole-file match so calls spanning several lines are caught; one level of nested parentheses is allowed.
leaks=$(find "$SRC/kotlin" -name '*.kt' -print0 | xargs -0 perl -0777 -ne '
  my $a = qr/\((?:[^()]|\([^()]*\))*?(?:\([^()]*?)?(?:url|token|pin)\b/is;
  while (/\bLog\.(?:[dewiv]|wtf)\s*$a|\b(?:println|print|Timber\.\w+)\s*$a/gis) {
    my $line = 1 + (substr($_, 0, $-[0]) =~ tr/\n//);
    print "$ARGV:$line\n";
  }') || leaks="perl failed"
if [ -n "$leaks" ]; then echo "$leaks"; c6="possible URL/PIN/token logging: $(echo $leaks)"; fi
grep -rniE '(password|secret|api[_-]?key|token)\s*=\s*"[^"]{6,}"' "$SRC/kotlin" && c6="hard-coded secret?"
if [ -z "$c6" ]; then pass C6 "No secrets or URL logging"; else bad C6 "No secrets or URL logging" "$c6"; fi

# C7: walk the manifest tag by tag. Keep the allowlist in sync with standards/SECURITY_STANDARDS.md S5.
c7=$(tr '\n\r\t' '   ' < "$SRC/AndroidManifest.xml" | awk -v pkg="$PKG" '
  function attr(tag, name,   m) {
    if (match(tag, "android:" name "[[:space:]]*=[[:space:]]*\"[^\"]*\"")) {
      m = substr(tag, RSTART, RLENGTH); sub(/^[^"]*"/, "", m); sub(/"$/, "", m); return m
    }
    return ""
  }
  function short(n) {
    if (index(n, pkg ".") == 1) return substr(n, length(pkg) + 1)
    if (n !~ /^\./ && n !~ /\./) return "." n
    return n
  }
  function finish() {
    if (cur != "" && (ex == "true" || (ex == "" && filter))) out = out " " short(cur)
    cur = ""
  }
  BEGIN { RS = "<"; allowed = " .ui.MainActivity .playback.PlaybackService .receiver.BootReceiver " }
  {
    tag = $0; sub(/>.*/, "", tag)
    split(tag, w, /[[:space:]\/]+/); el = w[1]
    if (el == "application") backup = attr(tag, "allowBackup")
    else if (el ~ /^(activity|activity-alias|service|receiver|provider)$/) {
      finish(); cur = attr(tag, "name"); ex = attr(tag, "exported"); filter = 0
      if (tag ~ /\/[[:space:]]*$/) finish()
    }
    else if (el == "intent-filter") filter = 1
    else if (tag ~ /^\/(activity|activity-alias|service|receiver|provider)[[:space:]]*$/) finish()
  }
  END {
    if (backup != "false") { print "allowBackup must be \"false\""; exit }
    n = split(out, e, " ")
    for (i = 1; i <= n; i++) if (index(allowed, " " e[i] " ") == 0) bad = bad " " e[i]
    if (bad != "") print "unreviewed exported component(s):" bad
  }')
if [ -z "$c7" ]; then pass C7 "Manifest hygiene"; else bad C7 "Manifest hygiene" "$c7"; fi

exit $fail
