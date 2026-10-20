# Handoff: execute File Server FS-3 (SFTP)

This branch, `claude/file-server-fs3-plan`, holds a written, reviewed but **not executed** plan for the third
delivery of Jasper's File Server plugin: SFTP. Everything an agent needs to pick it up is in the repository.

## What File Server is, and where it stands

File Server (`plugins/fileserver`, package `dev.jasper.fileserver`) is a bundled Jasper plugin. It hosts servers that
share a local folder, like MobaXterm's Servers window. It is a server, not a client for remote shares.

- **FS-1** (foundation and HTTP) and **FS-2** (FTP/FTPS, HTTPS and a shared TLS certificate; File Server 0.2.0) are
  done and on `main`. See their entries in `docs/STATUS.md`.
- **FS-3** (SFTP) is the plan on this branch.
- **FS-4** (TFTP) comes after it and is not planned yet.

## Read these first, in this order

1. `AGENTS.md`: the project's binding rules for agents (build, tests, what never to do without the user).
2. `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md`: the approved design spec. It is the binding
   authority. Its "SFTP" section and the FS-3 line under "Delivery" are what this plan implements.
3. `docs/superpowers/plans/2026-10-01-file-server-fs3.md`: **the plan to execute**. It has 8 tasks with complete code
   and tests, Global Constraints, the planning decisions, and a summary of verified sshd behaviour.
4. `docs/superpowers/research/2026-10-01-sshd-2.19-server-facts.md`: the full research behind the plan's "sshd facts".
   Use it when sshd behaves in a way the plan did not expect; do not re-derive these facts.
5. `docs/STATUS.md` (the File Server FS-1 and FS-2 entries) and `docs/file-server.md` (the user guide), for the
   current state and wording.
6. The FS-2 plan, `docs/superpowers/plans/2026-09-30-file-server-fs2.md`, only if you need the FTP code's background.
   FS-3 Task 1 moves FTP's path rules to a shared class.

## How to start

```bash
cd /path/to/jasper            # the repository root
git fetch origin
git worktree add -b claude/file-server-fs3 .worktrees/file-server-fs3 origin/claude/file-server-fs3-plan
cd .worktrees/file-server-fs3
git branch --show-current     # must print claude/file-server-fs3
```

- **Base.** The plan's Global Constraints say to branch from `main`. Branch from `claude/file-server-fs3-plan`
  instead, so the plan, these notes and the research travel with the work.
- **Updating from `main`.** `main` has moved since the plan was written (for example `claude/theme-system`). Merge
  `origin/main` into the new branch before Task 1. Then run `./gradlew :jasper-plugin-fileserver:check` once to start
  from green.
- **Line numbers.** The plan quotes exact code. If a file it modifies has changed on `main`, apply the plan's intent
  to the current code, and record the deviation.

## How to execute

- Use the `superpowers:subagent-driven-development` skill, as FS-1 and FS-2 did:
  - Each task gets one implementer and a task review (spec and quality), with fix rounds as needed.
  - Keep a ledger in `.superpowers/sdd/2026-10-01-file-server-fs3/progress.md`. It is git-ignored.
  - After all tasks, run one final whole-branch review on the most capable model.
  - Then use `superpowers:finishing-a-development-branch`.
- Use `superpowers:executing-plans` instead if you work inline.
- **Models.** Implementers can be a mid-tier model; the plan's code is complete, so most work is transcription and
  testing. Review Task 4 (the jail, security-critical) and the final review on the most capable model.
- **Where the plan's text is wrong, decide.** The spec is the authority. Record every decision as a ruling in the
  ledger, and list the deviations in the plan's status banner and in `docs/STATUS.md` (Task 8).

## Rules that bind this work

These are from `AGENTS.md` and the plan.

- **Never launch the GUI.** No `./gradlew :jasper-app:run`, and nothing else that opens a window. GUI checks go to the
  user; Task 8 lists them.
- **Branches.**
  - Never commit on `main`.
  - Run `git branch --show-current` before every commit: other sessions switch branches in the shared main checkout.
  - Never use `git stash` (the stash is shared between sessions).
- **Commits.** End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Make one
  commit per task.
- **Pushing and merging.** Do not push or merge without the user. Merge to `main` locally only when the user agrees.
- **Builds and tests.** Use `./gradlew` only; the plugin's project is `:jasper-plugin-fileserver`. Tests are headless
  and bind only to `127.0.0.1`.
- **Vendor types.** Types from sshd, BouncyCastle and the other bundled libraries stay inside `engine.*`
  sub-packages. The `check` task's `verifyPluginArchitecture` enforces this.
- **Panel placement is out of scope.** Do not change `PanelSpec`, `Anchor` or the app's panel code. The user is
  reworking panel placement in another session.
- **Source hygiene.** No raw control, private-use or unpaired-surrogate characters in Java source; run the hygiene
  script in `AGENTS.md`.

## Lessons from executing FS-2 (apply them here)

- **Results after a reply.** A server may finish its work (committing a file, logging an event) after it has sent the
  reply that the client sees. Tests must wait for the result with a bounded poll. They must not assert at once; the
  plan's tests already do this with `await(...)`.
- **Thread-count assertions** compare with `isLessThanOrEqualTo`, never `isEqualTo`. A thread from an earlier test
  can exit between the two reads.
- **Resources before a failure.** Get anything that can fail (certificates, keys) before creating a server, or clean
  up on every failure path. FS-2 leaked a thread because the plan created the server first.
- **Library facts.**
  - Check a library's real behaviour against its sources or bytecode when a test surprises you.
  - The FS-2 reviewers used `javap` on the cached jars, under `~/.gradle/caches/modules-2/files-2.1/`.
  - For sshd, the research notes above already hold most answers.
- **Never loosen the jail to make a test pass.** If an SFTP test fails because a path is refused, find out why first.
  `ShareRoot` and the accessor are the security boundary.

## What done looks like

- All 8 tasks are committed on `claude/file-server-fs3`. `./gradlew check` is green, with exact counts read from the
  XML reports in `*/build/test-results/test/` and `plugins/*/build/test-results/test/`. The hygiene script finds
  nothing.
- `docs/file-server.md`, `docs/README.md` and `docs/STATUS.md` are updated (Task 8). The plan's status banner lists
  the deviations.
- The user is told:
  - what was built
  - every ruling made on their behalf
  - the GUI checks to run: OpenSSH `sftp`/`scp`, FileZilla and Cyberduck with a password and with a key; the
    first-connection host key prompt matching the server card; a refused shell (`ssh -p 2222 host`); the editor's
    key list
  - the integration choice (merge locally, push and open a PR, or keep the branch)
