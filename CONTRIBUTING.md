# Contributing

## Branches

Every change starts from an issue and lives on a branch named after it:

```
<type>/<issue>-<slug>
```

- `<type>` is the issue's `type:*` label: `feat | fix | test | chore | refactor`. An issue has exactly one.
- `<issue>` is the issue number.
- `<slug>` comes from the issue title: lowercase words joined by `-`, at most ~40 characters.

Examples: `fix/19-validation-gaps-return-a-misleading-409`, `chore/24-ci-doesnt-build-feature-branches`, `feat/25-outbox-metrics-and-alerts-actuator`.

The regex (identical in the CI check, the script and the ruleset):

```
^(feat|fix|test|chore|refactor)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$
```

The ruleset matches full refs, so its copy also allows an optional `refs/heads/` prefix.

### Creating a branch

From the terminal (needs an authenticated `gh`):

```bash
scripts/new-branch.sh 19 --dry-run   # prints fix/19-validation-gaps-return-a-misleading-409
scripts/new-branch.sh 19             # creates it, links it to #19, checks it out
```

The script reads the issue's single `type:*` label (and fails on zero or more than one), builds the slug from the title, checks the name against the regex, and then runs `gh issue develop <issue> --name <branch> --checkout`, so the branch shows up under the issue's *Development* section.

How the slug is built from the title:

1. Strip the `GW-NNN —` prefix.
2. Cut at the first `;` or `:` (`Retry: backoff with jitter` → `Retry`).
3. Drop apostrophes, replace every run of characters other than `A-Z a-z 0-9` with `-`, and lowercase it. Accented letters count as separators, so write titles in plain English.
4. Trim to 40 characters at a word boundary.

From the GitHub UI: issue sidebar → *Development* → *Create a branch*, then **edit the suggested name** (GitHub proposes `19-gw-210-…`) to `fix/19-…`.

### Enforcement

| Layer | What it checks | Where |
|---|---|---|
| `branch-name` status check | Name matches the regex **and** `<type>` matches the issue's `type:*` label | [.github/workflows/branch-name.yml](.github/workflows/branch-name.yml), required on `main` |
| Ruleset "Branch naming matches type labels" | Name matches the regex, at push time | [docs/rulesets/branch-naming.json](docs/rulesets/branch-naming.json) |

The ruleset's *Restrict branch names* rule may not be available on GitHub Free. Where it isn't, the `branch-name` check is the only enforcement layer, and a wrongly named branch can still be pushed but can't be merged.

Dependabot branches (`dependabot/**`) are exempt: the check is skipped for them, which counts as passing.

## Pull requests and CI

- `build-and-test` ([ci.yml](.github/workflows/ci.yml)) runs `./mvnw clean verify` (unit + integration tests, Spotless, ArchUnit) on every push to any branch and on every PR to `main`. Push early to get feedback before opening a PR.
- A newer push to the same branch cancels the older run. Runs on `main` are never cancelled.
- `main` accepts changes only through a PR, with `build-and-test` and `branch-name` green. No approval is required (solo maintainer), and the branch doesn't have to be up to date with `main`.
- Run `./mvnw spotless:apply` before committing; `verify` fails on unformatted code.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) with the issue key, e.g. `fix(api): return 400 for validation errors (GW-210)`. A PR body with `Closes #<issue>` closes the issue on merge.
