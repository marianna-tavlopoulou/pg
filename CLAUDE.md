# CLAUDE.md

## Branches

- Never commit to `main`; it only accepts PRs with green `build-and-test` and `branch-name` checks.
- Branch names are `<type>/<issue>-<slug>`, where `<type>` is the issue's single `type:*` label (`feat | bug | test | chore | refactor`). Regex: `^(feat|bug|test|chore|refactor)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$`.
- Create branches with `scripts/new-branch.sh <issue>` (run with `--dry-run` first to see the name); it links the branch to the issue. Don't hand-craft names or use GitHub's suggested `<issue>-gw-nnn-…` name.
- If the issue has zero or several `type:*` labels, stop and ask; don't guess the type.
- The regex lives in three places that must stay in sync: [scripts/new-branch.sh](scripts/new-branch.sh), [.github/workflows/branch-name.yml](.github/workflows/branch-name.yml), [docs/rulesets/branch-naming.json](docs/rulesets/branch-naming.json).
- Don't rename the `build-and-test` or `branch-name` job ids: they are the required status check contexts on `main`.

Full convention: [CONTRIBUTING.md](CONTRIBUTING.md).

## Commits and PRs

- Conventional Commits with the issue key, e.g. `chore(ci): … (GW-215)`; PR body says `Closes #<issue>`.
- Run `./mvnw spotless:apply` before committing; `./mvnw verify` fails on unformatted code.
- Terminal is WSL bash: give bash commands, not PowerShell.
