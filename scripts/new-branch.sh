#!/usr/bin/env bash
# Create the branch for a GitHub issue as <type>/<issue>-<slug> and check it out,
# linked to the issue (gh issue develop). <type> comes from the issue's single
# type:* label; <slug> from its title. See CONTRIBUTING.md.
#
# Usage: scripts/new-branch.sh <issue> [--dry-run]
set -euo pipefail

# Keep in sync with .github/workflows/branch-name.yml and docs/rulesets/branch-naming.json.
BRANCH_RE='^(feat|bug|test|chore|refactor)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$'
SLUG_MAX=40

die() { echo "new-branch: $*" >&2; exit 1; }

issue="" dry_run=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=true ;;
    -h|--help) sed -n '2,7s/^# \{0,1\}//p' "$0"; exit 0 ;;
    *) [[ -z "$issue" ]] || die "unexpected argument '$arg'"; issue="$arg" ;;
  esac
done
[[ "$issue" =~ ^[0-9]+$ ]] || die "usage: scripts/new-branch.sh <issue> [--dry-run]"

title=$(gh issue view "$issue" --json title --jq .title) || die "can't read issue #$issue"
types=$(gh issue view "$issue" --json labels --jq '.labels[].name | select(startswith("type:")) | ltrimstr("type:")')

count=$(grep -c . <<<"$types" || true)
(( count == 1 )) || die "issue #$issue must have exactly one type:* label, found $count${types:+ ($(paste -sd, <<<"$types"))}"
type="$types"

# "GW-215 — CI doesn't build feature branches; main ..." -> "ci-doesnt-build-feature-branches"
slug=$(LC_ALL=C sed -E \
  -e 's/^GW-[0-9]+[[:space:]]*(—|–|-)[[:space:]]*//' \
  -e 's/[;:].*$//' \
  -e "s/'//g; s/’//g" \
  -e 's/[^A-Za-z0-9]+/-/g' \
  -e 's/^-+//; s/-+$//' \
  -e 's/^(.*)$/\L\1/' <<<"$title")

# Trim to SLUG_MAX at a word boundary; hard-cut only a single over-long word.
if (( ${#slug} > SLUG_MAX )); then
  cut="${slug:0:SLUG_MAX+1}"
  [[ "$cut" == *-* ]] && slug="${cut%-*}" || slug="${slug:0:SLUG_MAX}"
fi
[[ -n "$slug" ]] || die "issue #$issue title gives an empty slug"

branch="$type/$issue-$slug"
[[ "$branch" =~ $BRANCH_RE ]] || die "'$branch' doesn't match $BRANCH_RE (is type:$type a valid type?)"

if $dry_run; then
  echo "$branch"
else
  gh issue develop "$issue" --name "$branch" --checkout
fi
