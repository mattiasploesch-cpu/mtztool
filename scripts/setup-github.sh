#!/usr/bin/env bash
# Applies the access policy and rulesets to the GitHub repository.
#
#   ./scripts/setup-github.sh            # dry-run, only prints the plan
#   ./scripts/setup-github.sh --apply    # applies collaborators/settings/rulesets
#   ./scripts/setup-github.sh --apply --prune
#
# Requires: gh (authenticated as repository admin), jq.
# Without --prune, nobody is removed; removals are always reported as drift by
# scripts/audit-access.sh so they can be reviewed first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="$ROOT/.github/access-policy.json"
RULESET_DIR="$ROOT/.github/rulesets"

APPLY=false
PRUNE=false

usage() {
  cat <<'EOF'
Usage: scripts/setup-github.sh [--apply] [--prune]

  --apply   Apply the plan (default: dry-run)
  --prune   Remove collaborators that are not in the policy (destructive)
EOF
}

for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=true ;;
    --prune) PRUNE=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unbekannte Option: $arg" >&2; usage; exit 2 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "gh fehlt." >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "jq fehlt." >&2; exit 2; }

REPO="$(jq -r '.repository' "$POLICY")"
OWNER="$(jq -r '.owner' "$POLICY")"

if $APPLY; then
  admin="$(gh api "repos/$REPO" --jq '.permissions.admin' 2>/dev/null || echo false)"
  if [[ "$admin" != "true" ]]; then
    echo "Abbruch: angemeldeter Account ist kein Admin von $REPO." >&2
    exit 1
  fi
fi

run() {
  if $APPLY; then
    "$@"
  else
    printf 'dry-run: %s\n' "$*"
  fi
}

echo "Repository: $REPO"
echo "Modus:      $($APPLY && echo apply || echo dry-run)$($PRUNE && echo ' + prune' || true)"
echo

# --- Repository settings ---------------------------------------------------
echo "==> Repository-Einstellungen"
settings_patch() {
  run gh api --method PATCH "repos/$REPO" \
    -F allow_squash_merge=true \
    -F allow_merge_commit=false \
    -F allow_rebase_merge=false \
    -F allow_auto_merge=true \
    -F delete_branch_on_merge=true \
    -F allow_update_branch=true \
    -F allow_forking=true \
    -F has_issues=true \
    -F has_discussions=true \
    -f squash_merge_commit_title=PR_TITLE \
    -f squash_merge_commit_message=BLANK
}
settings_patch

run gh api --method PUT "repos/$REPO/vulnerability-alerts" >/dev/null

echo "==> Workflow-Token auf read-only"
run gh api --method PUT "repos/$REPO/actions/permissions/workflow" \
  -f default_workflow_permissions=read \
  -F can_approve_pull_request_reviews=false

# --- Release environment ---------------------------------------------------
echo "==> Environment 'release' mit Owner-Freigabe"
owner_id="$(gh api "users/$OWNER" --jq '.id')"
env_payload="$(jq -n --argjson id "$owner_id" '{
  wait_timer: 0,
  prevent_self_review: false,
  reviewers: [{ type: "User", id: $id }],
  deployment_branch_policy: { protected_branches: true, custom_branch_policies: false }
}')"
if $APPLY; then
  printf '%s' "$env_payload" | gh api --method PUT "repos/$REPO/environments/release" --input - >/dev/null
  echo "release: erforderlicher Reviewer @$OWNER"
else
  echo "dry-run: würde Environment 'release' mit Reviewer @$OWNER setzen"
fi

# --- Collaborators ---------------------------------------------------------
echo "==> Rollen"
apply_role() {
  local permission="$1"
  shift
  local users=("$@")
  ((${#users[@]})) || return 0
  for user in "${users[@]}"; do
    run gh api --method PUT "repos/$REPO/collaborators/$user" -f permission="$permission" >/dev/null
    echo "  @$user -> $permission"
  done
}

mapfile -t admins < <(jq -r '.roles.admin[]?' "$POLICY")
mapfile -t maintainers < <(jq -r '.roles.maintain[]?' "$POLICY")
mapfile -t writers < <(jq -r '.roles.write[]?' "$POLICY")
mapfile -t triage < <(jq -r '.roles.triage[]?' "$POLICY")
mapfile -t readers < <(jq -r '.roles.read[]?' "$POLICY")

apply_role admin "${admins[@]}"
apply_role maintain "${maintainers[@]}"
apply_role write "${writers[@]}"
apply_role triage "${triage[@]}"
apply_role read "${readers[@]}"

if $PRUNE; then
  echo "==> Entferne nicht gelistete Collaborators"
  mapfile -t expected < <(jq -r '.roles[][]' "$POLICY")
  while IFS=$'\t' read -r login role; do
    [[ "$login" == "$OWNER" ]] && continue
    found=false
    for user in "${expected[@]}"; do
      [[ "$user" == "$login" ]] && found=true && break
    done
    if ! $found; then
      run gh api --method DELETE "repos/$REPO/collaborators/$login"
      echo "  entfernt: @$login ($role)"
    fi
  done < <(gh api --paginate --slurp "repos/$REPO/collaborators?affiliation=direct&per_page=100" | jq -r 'add[] | [.login, .role_name] | @tsv')
fi

# --- Rulesets --------------------------------------------------------------
echo "==> Rulesets"
for file in "$RULESET_DIR"/*.json; do
  name="$(jq -r '.name' "$file")"
  existing="$(gh api --paginate --slurp "repos/$REPO/rulesets" 2>/dev/null \
    | jq -r --arg name "$name" 'add[] | select(.name == $name) | .id' \
    | head -1 || true)"
  if [[ -n "$existing" ]]; then
    if $APPLY; then
      gh api --method PUT "repos/$REPO/rulesets/$existing" --input "$file" >/dev/null
      echo "  aktualisiert: $name (#$existing)"
    else
      echo "dry-run: würde aktualisieren: $name (#$existing)"
    fi
  else
    if $APPLY; then
      gh api --method POST "repos/$REPO/rulesets" --input "$file" >/dev/null
      echo "  angelegt: $name"
    else
      echo "dry-run: würde anlegen: $name"
    fi
  fi
done

echo
if $APPLY; then
  echo "Fertig. Prüfe mit: ./scripts/audit-access.sh"
else
  echo "Dry-run beendet. Anwenden mit: ./scripts/setup-github.sh --apply"
fi
