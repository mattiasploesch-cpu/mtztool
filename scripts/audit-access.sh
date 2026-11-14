#!/usr/bin/env bash
# Read-only audit of repository access, rulesets and settings against
# .github/access-policy.json. Never changes anything.
#
#   GH_TOKEN=... ./scripts/audit-access.sh
#
# Exit codes: 0 = OK (or intentionally skipped), 1 = drift, 2 = audit error.
#
# The collaborators endpoint requires admin access. In CI, set the
# ACCESS_AUDIT_TOKEN secret; without it, ACCESS_AUDIT_ALLOW_SKIP=1 makes the
# job skip instead of failing every week.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="$ROOT/.github/access-policy.json"
RULESET="$ROOT/.github/rulesets/main.json"
ALLOW_SKIP="${ACCESS_AUDIT_ALLOW_SKIP:-0}"

for tool in gh jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "FEHLER: $tool fehlt." >&2; exit 2; }
done

REPO="$(jq -r '.repository' "$POLICY")"
OWNER="$(jq -r '.owner' "$POLICY")"
FAIL=0

if [[ -z "${GH_TOKEN:-}" && "$ALLOW_SKIP" == "1" ]]; then
  echo "SKIP    Kein ACCESS_AUDIT_TOKEN gesetzt; Audit wird übersprungen."
  exit 0
fi

ok()    { printf 'OK      %s\n' "$*"; }
warn()  { printf 'WARN    %s\n' "$*"; }
drift() { printf 'DRIFT   %s\n' "$*"; FAIL=1; }

echo "Access-Audit für $REPO"
echo "Policy: $POLICY"
echo

if ! gh api user --jq '.login' >/dev/null 2>&1; then
  echo "FEHLER: gh ist nicht authentifiziert oder das Token ist ungültig." >&2
  exit 2
fi
echo "Angemeldet als: $(gh api user --jq '.login')"
echo

# --- Collaborators ---------------------------------------------------------
echo "==> Rollen"
if collab_json="$(gh api --paginate --slurp "repos/$REPO/collaborators?affiliation=all&per_page=100" 2>/tmp/mtz-audit-collab.err)"; then
  declare -A actual=()
  while IFS=$'\t' read -r login role; do
    [[ -n "$login" ]] && actual["$login"]="$role"
  done < <(jq -r 'add[] | [.login, .role_name] | @tsv' <<<"$collab_json")

  declare -A expected=()
  while IFS=$'\t' read -r login role; do
    [[ -n "$login" ]] && expected["$login"]="$role"
  done < <(jq -r '.roles | to_entries[] | .key as $role | .value[] | [. , $role] | @tsv' "$POLICY")

  # The owner must always be listed as admin in the policy itself.
  if [[ "${expected[$OWNER]:-}" != "admin" ]]; then
    drift "Owner @$OWNER ist in der Policy nicht als admin gelistet"
  fi

  for login in "${!actual[@]}"; do
    role="${actual[$login]}"
    want="${expected[$login]:-}"
    if [[ -z "$want" ]]; then
      drift "@$login ist Collaborator ($role), steht aber nicht in der Policy"
    elif [[ "$want" != "$role" ]]; then
      drift "@$login hat $role, Policy verlangt $want"
    else
      ok "@$login = $role"
    fi
  done

  for login in "${!expected[@]}"; do
    if [[ -z "${actual[$login]:-}" ]]; then
      drift "@$login fehlt (Policy: ${expected[$login]})"
    fi
  done

  if [[ "${actual[$OWNER]:-}" != "admin" ]]; then
    drift "Owner @$OWNER ist nicht admin"
  fi
else
  if [[ "$ALLOW_SKIP" == "1" ]] && grep -qiE '403|resource not accessible|not found' /tmp/mtz-audit-collab.err; then
    warn "Rollenprüfung übersprungen: ACCESS_AUDIT_TOKEN fehlt oder hat keine Administration-Leserechte."
    warn "Siehe docs/GITHUB_SETUP.md. Der Rest des Audits läuft weiter."
  else
    echo "FEHLER: Collaborators konnten nicht gelesen werden:" >&2
    cat /tmp/mtz-audit-collab.err >&2
    exit 2
  fi
fi
rm -f /tmp/mtz-audit-collab.err

# --- Rulesets --------------------------------------------------------------
echo
echo "==> Rulesets"
if rulesets_json="$(gh api --paginate --slurp "repos/$REPO/rulesets" 2>/dev/null)"; then
  main_id="$(jq -r 'add[] | select(.name == "main-protection") | .id' <<<"$rulesets_json" | head -1)"
  tags_id="$(jq -r 'add[] | select(.name == "release-tags") | .id' <<<"$rulesets_json" | head -1)"

  if [[ -z "$main_id" ]]; then
    drift "Ruleset 'main-protection' fehlt"
  else
    main_json="$(gh api "repos/$REPO/rulesets/$main_id")"
    [[ "$(jq -r '.enforcement' <<<"$main_json")" == "active" ]] \
      && ok "main-protection ist aktiv" \
      || drift "main-protection ist nicht aktiv"

    mapfile -t required_expected < <(jq -r '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' "$RULESET")
    mapfile -t required_actual < <(jq -r '.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks[].context' <<<"$main_json")

    for check in "${required_expected[@]}"; do
      found=false
      for have in "${required_actual[@]}"; do
        [[ "$check" == "$have" ]] && found=true && break
      done
      $found && ok "Pflicht-Check: $check" || drift "Pflicht-Check fehlt: $check"
    done

    pr_count="$(jq -r '[.rules[] | select(.type == "pull_request") | .parameters.required_approving_review_count] | max // 0' <<<"$main_json")"
    (( pr_count >= 1 )) && ok "PR-Review erforderlich ($pr_count)" || drift "PR-Review fehlt"

    bypass="$(jq -r '.bypass_actors | length' <<<"$main_json")"
    (( bypass == 0 )) && ok "Keine Bypass-Actors" || drift "$bypass Bypass-Actor(s) definiert"

    for rule in deletion non_fast_forward required_linear_history; do
      if jq -e --arg r "$rule" '.rules[] | select(.type == $r)' <<<"$main_json" >/dev/null; then
        ok "Regel: $rule"
      else
        drift "Regel fehlt: $rule"
      fi
    done
  fi

  if [[ -z "$tags_id" ]]; then
    drift "Ruleset 'release-tags' fehlt"
  else
    tags_json="$(gh api "repos/$REPO/rulesets/$tags_id")"
    [[ "$(jq -r '.enforcement' <<<"$tags_json")" == "active" ]] \
      && ok "release-tags ist aktiv" \
      || drift "release-tags ist nicht aktiv"
  fi
else
  warn "Rulesets konnten nicht gelesen werden (fehlende Rechte)."
fi

# --- Repository settings ---------------------------------------------------
echo
echo "==> Repository-Einstellungen"
repo_json="$(gh api "repos/$REPO")"
while IFS=$'\t' read -r key want; do
  [[ "$key" == "default_workflow_permissions" || "$key" == "can_approve_pull_request_reviews" ]] && continue
  got="$(jq -r --arg k "$key" '.[$k]' <<<"$repo_json")"
  if [[ "$got" == "$want" ]]; then
    ok "$key = $want"
  else
    drift "$key = $got, Policy verlangt $want"
  fi
done < <(jq -r '.repository_settings | to_entries[] | [.key, (.value | tostring)] | @tsv' "$POLICY")

if workflow_json="$(gh api "repos/$REPO/actions/permissions/workflow" 2>/dev/null)"; then
  [[ "$(jq -r '.default_workflow_permissions' <<<"$workflow_json")" == "read" ]] \
    && ok "Workflow-Token: read" \
    || drift "Workflow-Token ist nicht read-only"
  [[ "$(jq -r '.can_approve_pull_request_reviews' <<<"$workflow_json")" == "false" ]] \
    && ok "Workflows dürfen PRs nicht freigeben" \
    || drift "Workflows dürfen PRs freigeben"
else
  warn "Workflow-Berechtigungen konnten nicht gelesen werden."
fi

if gh api "repos/$REPO/vulnerability-alerts" >/dev/null 2>&1; then
  ok "Vulnerability alerts aktiv"
else
  drift "Vulnerability alerts nicht aktiv"
fi

# --- Release environment ---------------------------------------------------
echo
echo "==> Environment 'release'"
if env_json="$(gh api "repos/$REPO/environments/release" 2>/dev/null)"; then
  reviewers="$(jq -r '.protection_rules | map(select(.type == "required_reviewers")) | length' <<<"$env_json")"
  (( reviewers >= 1 )) && ok "Release benötigt Freigabe" || drift "Release hat keinen Required Reviewer"
else
  drift "Environment 'release' fehlt"
fi

echo
if (( FAIL )); then
  echo "Ergebnis: DRIFT – siehe ACCESS_CONTROL.md und GOVERNANCE.md."
  exit 1
fi
echo "Ergebnis: OK – Policy und Repository stimmen überein."
