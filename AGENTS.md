# AGENTS.md

Repository conventions for AI agents and humans working in **whos-after-me**.

`CLAUDE.md` is a symlink to this file. Edit `AGENTS.md` only; never replace the symlink with a copy.

## 1. Language policy

**Every written artifact in this repository MUST be in English.** This applies to, without limitation:

- Source code, code comments, and docstrings
- All documentation (`*.md` files, including `CONTEXT.md` and ADRs)
- GitHub issues, issue comments, and labels
- Pull request titles, bodies, and review comments
- Commit messages and branch names

Do not write repository artifacts in any other language, even if the surrounding conversation is in another language.

## 2. Git workflow

`main` is a protected branch: direct pushes to it are blocked for everyone, including administrators. Every change must arrive via pull request.

1. **Branch first.** Never commit directly to `main`. Create a new branch for every unit of work:

   ```
   <type>/<short-slug>
   ```

   where `<type>` is one of `feat`, `fix`, `docs`, `chore`, `refactor`, `test`.
   Examples: `feat/login-flow`, `docs/agents-setup`.

2. **One concern per branch.** Keep each branch focused on a single issue or ticket.
3. **Keep it mergeable.** If `main` moves, rebase or merge it into your branch so the PR stays conflict-free.

### Finishing work

1. Push the branch and open a PR against `main` (`gh pr create`).
2. **Merge it yourself** (`gh pr merge --squash --delete-branch`); no external approval is required.
3. Verify the branch is deleted both locally and on the remote; delete it manually if it survived.
4. **Clean up stray processes.** Stop any dev servers, watchers, test runners, or background jobs started during the work. Confirm nothing is still listening on the ports you used and no test processes remain running.
5. Make sure linked issues close automatically (`Closes #<n>` in the PR body), or close them manually.

## 3. `/implement` format

Work produced through the `implement` skill must follow this format:

- **One ticket → one branch → one PR.**
- Branch name: `<type>/<issue-number>-<short-slug>`, e.g. `feat/12-login-endpoint`.
- PR body starts with `Implements #<issue-number>` (or `Closes #<issue-number>` when the PR fully resolves the issue), followed by a short summary of the change and how it was verified.
- Commit messages: `<type>: <imperative summary>`, in English — e.g. `feat: add login endpoint`.
- Before merging: all tests pass, and no test processes, dev servers, or watchers are left running.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues; use the `gh` CLI for all issue operations. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role triage vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` at the repo root plus `docs/adr/`. See `docs/agents/domain.md`.
