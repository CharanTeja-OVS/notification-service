#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: .github/orchestrator.sh --scenario greenfield|brownfield|ambiguous (--requirement TEXT | --requirement-file FILE) [--run-id ID] [--dry-run]

AGENT_COMMAND must contain {prompt} and {output} when not using --dry-run.
USAGE
}

scenario=''
requirement=''
requirement_file=''
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
dry_run=false
while (($#)); do
  case "$1" in
    --scenario) scenario="$2"; shift 2 ;;
    --requirement) requirement="$2"; shift 2 ;;
    --requirement-file) requirement_file="$2"; shift 2 ;;
    --run-id) run_id="$2"; shift 2 ;;
    --dry-run) dry_run=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done
case "$scenario" in greenfield|brownfield|ambiguous) ;; *) echo 'A valid --scenario is required.' >&2; exit 2 ;; esac
if [[ -n "$requirement" && -n "$requirement_file" ]] || [[ -z "$requirement" && -z "$requirement_file" ]]; then
  echo 'Provide exactly one of --requirement or --requirement-file.' >&2; exit 2
fi
if [[ -n "$requirement_file" ]]; then
  [[ -f "$requirement_file" ]] || { echo "Requirement file not found: $requirement_file" >&2; exit 2; }
  requirement="$(<"$requirement_file")"
fi

root=".agent-work/$run_id"
mkdir -p "$root/context" "$root/prompts" "$root/outputs" "$root/evidence"
cp ".github/skills/state-and-handoffs/state-template.md" "$root/state.md"
printf '%s\n' "$requirement" > "$root/requirement.md"
python3 - "$root/state.md" "$run_id" "$scenario" <<'PY'
from datetime import datetime, timezone
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
text = text.replace('<run-id>', sys.argv[2]).replace('greenfield | brownfield | ambiguous', sys.argv[3])
text = text.replace('<timestamp>', datetime.now(timezone.utc).isoformat())
path.write_text(text)
PY

agents=(requirements architecture developer unit-tests tester documentation prod-readiness)
for index in "${!agents[@]}"; do
  agent="${agents[$index]}"
  number="$(printf '%02d' "$index")"
  prompt="$root/prompts/$number-$agent.md"
  output="$root/outputs/$number-$agent.md"
  previous=''
  if (( index > 0 )); then previous="$root/context/$(printf '%02d' "$((index-1))")-${agents[$((index-1))]}.md"; fi
  {
    printf '# Orchestration Task\n\nYou are the %s specialist agent.\n\n' "$agent"
    printf 'Scenario: %s\nRequirement: %s\n\n' "$scenario" "$requirement"
    printf 'Read the role contract: .github/agents/%s.agent.md\n' "$agent"
    printf 'Read the applicable skill under .github/skills/ and %s\n' "$root/state.md"
    [[ -n "$previous" ]] && printf 'Read the previous handoff: %s\n' "$previous"
    printf '\nWrite the required evidence handoff to %s. Classify claims as PLANNED, MOCK_VERIFIED, LIVE_VERIFIED, or GAP.\n' "$output"
  } > "$prompt"
  if $dry_run; then
    cp "$prompt" "$output"
  else
    [[ -n "${AGENT_COMMAND:-}" ]] || { echo 'AGENT_COMMAND is required unless --dry-run is used.' >&2; exit 2; }
    command_line="${AGENT_COMMAND//\{prompt\}/$prompt}"
    command_line="${command_line//\{output\}/$output}"
    eval "$command_line"
  fi
  [[ -s "$output" ]] || { echo "Agent produced no output: $agent" >&2; exit 1; }
  cp "$output" "$root/context/$number-$agent.md"
  python3 - "$root/state.md" "$((index + 1))" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
step = sys.argv[2]
lines = path.read_text().splitlines()
path.write_text('\n'.join(
    line.replace('| NOT_STARTED |', '| COMPLETE |', 1) if line.startswith(f'| {step} |') else line
    for line in lines
) + '\n')
PY
done
printf 'Orchestration complete. State: %s/state.md\n' "$root"
