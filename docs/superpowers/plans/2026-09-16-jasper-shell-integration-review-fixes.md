# Shell integration review fixes — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every finding from the whole-branch review of `claude/shell-integration`, so the shipped integration survives hostile user environments, cannot be silently disabled, and never loses a command's exit status.

**Architecture:** Three independent slices with disjoint file sets — the shell scripts, the launch/extraction Java, and the receiving side plus UI — each fixed with a failing test first. Documentation lands last, once the behaviour it describes is settled.

**Tech Stack:** zsh 5.x, bash 3.2 and 5.x, fish 3.x/4.x, Java 25 on the JetBrains Runtime, JUnit 6 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-16-jasper-shell-integration-design.md`

## Global Constraints

- Java 25 on the **JetBrains Runtime**. Use `./gradlew`, never a system `gradle`.
- `jasper-terminal` (package `dev.jasper.terminal`) never depends on `jasper-app`. **No public method in `jasper-terminal` takes or returns a JediTerm type.**
- Never put raw control, private-use or unpaired surrogate characters in source. Write Java escapes (`"\033[1;3D"`) and shell `$'\033'` forms.
- Threading: JediTerm runs on the session's reader thread; the view runs on the Event Dispatch Thread. Every read of buffer state takes `TerminalTextBuffer`'s lock; keep work under the lock small and bounded.
- Child processes get `TERM=xterm-256color` and `COLORTERM=truecolor`; both are reserved against `[terminal.env]`.
- Do not launch the GUI. `./gradlew check` only.
- Each task ends with a commit whose message ends with the repository's attribution trailers.

## Decisions taken without the user (they asked not to be interrupted)

1. **fish prompt re-wrap** moves into the `fish_prompt` event handler with a marker check, matching zsh and bash. The invented fallback prompt (`printf '%s> ' (prompt_pwd)`) is **deleted** rather than fixed: fish always autoloads a default `fish_prompt`, so the branch is dead code that silently rewrites the user's prompt if it ever fires.
2. **`HISTCONTROL=ignorespace` is honoured.** When bash declined to record the line, Jasper emits **neither `cmd` nor `C`**, so the screen-read fallback cannot recapture the secret either. `D` still fires, so the prompt cycle stays intact; the command simply never reaches History. A deliberate amendment to the spec, recorded in the spec and STATUS.
3. **bash injection is skipped entirely** when the user passes `--norc`, `--rcfile`, `--init-file`, or any form of `-c`. Jasper's rcfile would be ignored in those cases, so stripping `-l` would leave a login shell with no login emulation. Variables are still exported.
4. **`JASPER_SHELL_INTEGRATION` moves before the `[terminal.env]` overlay**, so the documented rule "a `[terminal.env]` override wins" becomes uniformly true, and `inject` uses the effective value.
5. **The two adjacent status-bar dots are NOT rearranged.** The running/stopped dot and the integration dot sit side by side, and `Segment.doLayout` clips the integration dot first at narrow widths. Both are visual judgements that cannot be verified headlessly; changing them would churn the design render fixtures on a guess. Recorded as a user-run item instead.

---

### Task 1: zsh and bash script hardening

**Files:**
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.zsh`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.bash`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellRun.java`

**Interfaces:**
- Consumes: the existing `ShellRun` helpers and mark constants.
- Produces: `ShellRun.interactive(String shell, String rcBody, String input)` returning a record with `output()` and `errors()` — Tasks 2 and 3 reuse it.

- [ ] **Step 1: Write the failing tests**

Add a helper to `ShellRun` that writes `rcBody` into the temporary `HOME`'s rc file, launches the shell interactively through the same environment builder the class already uses, feeds `input` on stdin, and captures stdout and stderr separately. Then add to `ShellIntegrationScriptTest`:

```java
    @Test void scriptsStaySilentUnderNounset() throws Exception {
        for (String shell : List.of("/bin/zsh", "/bin/bash")) {
            if (!Files.isExecutable(Path.of(shell))) continue;
            String rc = shell.endsWith("zsh") ? "setopt nounset\n" : "set -u\n";
            ShellRun.Result result = ShellRun.interactive(shell, rc, "printf 'done\\n'\n");
            assertThat(result.errors()).doesNotContain("unbound variable").doesNotContain("parameter not set");
        }
    }

    @Test void aReadonlyPromptDoesNotBreakTheShellOrTheMarks() throws Exception {
        if (!Files.isExecutable(Path.of("/bin/bash"))) return;
        ShellRun.Result result = ShellRun.interactive("/bin/bash", "readonly PS1='P> '\n", "printf 'done\\n'\n");
        assertThat(result.errors()).doesNotContain("readonly variable");
        assertThat(result.output()).contains(ShellRun.C);
    }

    @Test void aCommandNamingJasperOwnFunctionsIsStillReported() throws Exception {
        if (!Files.isExecutable(Path.of("/bin/bash"))) return;
        ShellRun.Result result = ShellRun.interactive("/bin/bash", "", "echo __jasper_probe\n");
        assertThat(result.output()).contains(ShellRun.CMD("echo __jasper_probe")).contains(ShellRun.C);
    }

    @Test void ignorespaceKeepsAHiddenCommandOutOfJasperToo() throws Exception {
        if (!Files.isExecutable(Path.of("/bin/bash"))) return;
        ShellRun.Result result = ShellRun.interactive("/bin/bash", "HISTCONTROL=ignorespace\n", " echo SECRET\n");
        assertThat(result.output()).doesNotContain("U0VDUkVU").doesNotContain(ShellRun.C);
        assertThat(result.output()).contains(ShellRun.A);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: FAIL — `unbound variable` present, `readonly variable` present, no `CMD` for the `__jasper_probe` line, `U0VDUkVU` present.

- [ ] **Step 3: Harden `jasper.zsh`**

Lines 4–5 become unset-safe:

```zsh
[[ "${TERM_PROGRAM-}" == "Jasper" ]] || return 0
[[ -n "${JASPER_INTEGRATION_LOADED-}" ]] && return 0
```

`__jasper_osc` and both `printf` calls inside `__jasper_encode` use `builtin printf`:

```zsh
__jasper_osc() { builtin printf '\033]%s\007' "$1"; }
```

The `PROMPT` assignment in `__jasper_precmd` checks writability, because assigning to a readonly parameter aborts the hook:

```zsh
    if [[ "$PROMPT" != *"$__jasper_mark_a"* && "${(t)PROMPT}" != *readonly* ]]; then
        PROMPT="%{$__jasper_mark_a%}$PROMPT%{$__jasper_mark_b%}"
    fi
```

In `__jasper_preexec`, `base64` becomes `command base64` and `tr` becomes `command tr`.

- [ ] **Step 4: Harden `jasper.bash`**

Lines 4–5 become unset-safe the same way. `__jasper_osc` uses `builtin printf`, as do both `printf` calls in `__jasper_encode`. Add a writability helper that reads only the flag word, so a value containing the letter `r` is not mistaken for a readonly flag:

```bash
# Assigning to a readonly PS1 or PROMPT_COMMAND aborts the function and prints an error every prompt.
__jasper_writable() {
    local spec
    spec="$(declare -p "$1" 2>/dev/null)"
    spec="${spec#declare }"
    spec="${spec%% *}"
    [[ "$spec" != *r* ]]
}
```

Use it for `PS1` in `__jasper_prompt_command`:

```bash
    if [[ "$PS1" != *"$__jasper_mark_a"* ]] && __jasper_writable PS1; then
        PS1="\[$__jasper_mark_a\]$PS1\[$__jasper_mark_b\]"
    fi
```

Anchor the self-recognition guard in the DEBUG trap so only Jasper's own calls are skipped, not any command containing the substring:

```bash
    case "$BASH_COMMAND" in
        __jasper_*|'eval "$__jasper_install_debug"') return 0 ;;
    esac
```

Honour `ignorespace` in the history fallback:

```bash
    if [[ -z "$number" || "$number" == "$__jasper_prompt_history" ]]; then
        case ":${HISTCONTROL-}:" in
            *:ignorespace:*|*:ignoreboth:*)
                # bash was told to forget this line, so Jasper forgets it too: emitting C would let
                # the screen read recapture it. D still fires, so the prompt cycle stays intact.
                return 0 ;;
        esac
        line="$BASH_COMMAND"
    else
```

`base64` and `tr` become `command base64` and `command tr`. Guard the `PROMPT_COMMAND` install and make the flatten path unset-safe — insert immediately before the `if [[ "$(declare -p PROMPT_COMMAND ...` block:

```bash
__jasper_writable PROMPT_COMMAND || return 0
```

and in the `else` arm:

```bash
    __jasper_existing="$(IFS=';'; builtin printf '%s' "${PROMPT_COMMAND[*]-}")"
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: PASS, including every pre-existing test in the class.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.zsh \
        jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.bash \
        jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java \
        jasper-app/src/test/java/dev/jasper/app/ShellRun.java
git commit -m "fix: keep the shell scripts quiet under set -u, readonly prompts and ignorespace"
```

---

### Task 2: fish prompt re-wrapping

**Files:**
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.fish`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java`

The spec requires every script to check "for its own marker first so themes that rebuild the prompt are re-wrapped and never double-wrapped". fish wraps once at load with no marker check, and fish sources `vendor_conf.d` **before** `config.fish`, so any prompt defined there (starship, Tide, oh-my-fish, a hand-written `fish_prompt`) overwrites Jasper's wrapper for the life of the session.

- [ ] **Step 1: Write the failing test**

```java
    @Test void fishReWrapsAPromptDefinedAfterTheIntegrationLoaded() throws Exception {
        Path fish = Path.of("/opt/homebrew/bin/fish");
        Assumptions.assumeTrue(Files.isExecutable(fish), "fish is not installed");
        ShellRun.Result result = ShellRun.interactiveFish(fish.toString(),
            "function fish_prompt\n    printf 'mine> '\nend\n", "printf 'done\\n'\n");
        assertThat(result.output()).contains(ShellRun.B).contains("mine> ");
    }
```

`ShellRun.interactiveFish` writes the body into `$HOME/.config/fish/config.fish` and launches fish with the `XDG_DATA_DIRS` mechanism `LaunchSettings` uses.

- [ ] **Step 2: Run it and confirm the skip is reported**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: SKIPPED on this Mac (no fish). Read `jasper-app/build/test-results/test/TEST-dev.jasper.app.ShellIntegrationScriptTest.xml` and confirm `skipped="1"` — the branch currently has no fish test at all, so a reported skip is itself the deliverable.

- [ ] **Step 3: Move the wrap into the prompt event**

Replace the load-time block (`if functions -q fish_prompt … end` through the `function fish_prompt … end` that follows it) with:

```fish
    # fish loads vendor_conf.d before config.fish, so a prompt defined there replaces this wrapper.
    # Re-check every prompt, the way the zsh and bash scripts re-check PROMPT and PS1.
    function __jasper_wrap_prompt
        if functions -q fish_prompt
            and not functions fish_prompt | string match -q '*__jasper_osc "133;B"*'
            functions -c fish_prompt __jasper_original_prompt
            function fish_prompt
                __jasper_original_prompt
                __jasper_osc "133;B"
            end
        end
    end
```

and add `__jasper_wrap_prompt` as the last statement of `__jasper_precmd`, which runs on the `fish_prompt` event before the prompt function itself is called. The invented `printf '%s> ' (prompt_pwd)` fallback is deleted: fish autoloads its own default `fish_prompt`, so the branch was dead code that would silently rewrite the user's prompt.

- [ ] **Step 4: Verify**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: PASS or SKIP. Then confirm by inspection that `jasper.fish` contains no `prompt_pwd` and that `__jasper_wrap_prompt` is called from `__jasper_precmd`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.fish \
        jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java \
        jasper-app/src/test/java/dev/jasper/app/ShellRun.java
git commit -m "fix: re-wrap the fish prompt every cycle like zsh and bash"
```

---

### Task 3: wrapper files

**Files:**
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/bash/rc.bash`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/zsh/.zshenv`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java`

- [ ] **Step 1: Write the failing test**

```java
    @Test void aNonInteractiveZshLeavesZdotdirAlone() throws Exception {
        if (!Files.isExecutable(Path.of("/bin/zsh"))) return;
        assertThat(ShellRun.nonInteractiveZdotdir("/bin/zsh")).isEmpty();
    }
```

`ShellRun.nonInteractiveZdotdir` runs `zsh -c 'printf %s "$ZDOTDIR"'` with the wrapper `ZDOTDIR` exactly as `LaunchSettings` sets it, and returns stdout.

- [ ] **Step 2: Run it to verify it fails**

Expected: FAIL — returns the Jasper wrapper directory rather than an empty string.

- [ ] **Step 3: Fix `.zshenv`**

Only hand `ZDOTDIR` back to the wrapper when more startup files actually follow:

```zsh
if [[ -o interactive || -o login ]]; then
    if [[ -n "$ZDOTDIR" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
    export ZDOTDIR="$__jasper_zdotdir"
else
    unset JASPER_ORIGINAL_ZDOTDIR
fi
```

- [ ] **Step 4: Fix `rc.bash`**

The spec says "`/etc/bashrc` (or `/etc/bash.bashrc`)", but two unconditional `source` lines read both:

```bash
    if [[ -r /etc/bash.bashrc ]]; then source /etc/bash.bashrc
    elif [[ -r /etc/bashrc ]]; then source /etc/bashrc
    fi
```

- [ ] **Step 5: Verify and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: PASS.

```bash
git add jasper-app/src/main/resources/dev/jasper/app/shell-integration/bash/rc.bash \
        jasper-app/src/main/resources/dev/jasper/app/shell-integration/zsh/.zshenv \
        jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java \
        jasper-app/src/test/java/dev/jasper/app/ShellRun.java
git commit -m "fix: source one system bashrc and leave ZDOTDIR alone for non-interactive zsh"
```

---

### Task 4: LaunchSettings — environment scrub, bash options, fish path, precedence

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/LaunchSettings.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/LaunchSettingsTest.java`

**Interfaces:**
- Produces: `LaunchSettings.resolve(ConfigSnapshot, String osName, Map<String,String> inherited, int columns, int lines, Path integrationDir)` — signature unchanged.

- [ ] **Step 1: Write the failing tests**

Add `resolveBash(List<String> args)` and `snapshotWith(String program, ShellIntegrationMode mode)` (plus an overload taking a `Map<String,String>` env overlay) helpers following the class's existing construction style, then:

```java
    @Test void jasperOwnMarkersNeverReachTheChildFromTheParentEnvironment() {
        var inherited = Map.of("JASPER_INTEGRATION_LOADED", "1", "JASPER_LOGIN_SHELL", "1",
            "JASPER_ORIGINAL_ZDOTDIR", "/stale", "JASPER_SHELL_INTEGRATION", "/stale");
        var settings = LaunchSettings.resolve(snapshotWith("zsh", ShellIntegrationMode.AUTO),
            "Mac OS X", inherited, 80, 24, Path.of("/opt/jasper/si"));
        assertThat(settings.environment()).doesNotContainKey("JASPER_INTEGRATION_LOADED");
        assertThat(settings.environment()).doesNotContainKey("JASPER_LOGIN_SHELL");
        assertThat(settings.environment()).doesNotContainKey("JASPER_ORIGINAL_ZDOTDIR");
        assertThat(settings.environment()).containsEntry("JASPER_SHELL_INTEGRATION", "/opt/jasper/si");
    }

    @Test void bashKeepsItsOwnStartupWhenTheUserAlreadyChoseAnRcFile() {
        for (List<String> args : List.of(List.of("--norc"), List.of("--rcfile", "/my/rc"),
                List.of("--init-file", "/my/rc"), List.of("-c", "echo hi"), List.of("-lc", "echo hi"))) {
            var settings = resolveBash(args);
            assertThat(settings.command()).doesNotContain("--rcfile");
            assertThat(settings.command()).containsSequence(args);
            assertThat(settings.environment()).doesNotContainKey("JASPER_LOGIN_SHELL");
        }
    }

    @Test void bashLoginFlagsAreRecognisedInsideAShortCluster() {
        var settings = resolveBash(List.of("-il"));
        assertThat(settings.command()).containsExactly("/bin/bash", "--rcfile", "/opt/jasper/si/bash/rc.bash", "-i");
        assertThat(settings.environment()).containsEntry("JASPER_LOGIN_SHELL", "1");
    }

    @Test void noprofileIsNotContradictedByLoginEmulation() {
        assertThat(resolveBash(List.of("-l", "--noprofile")).environment()).doesNotContainKey("JASPER_LOGIN_SHELL");
    }

    @Test void repeatedLaunchesDoNotAccumulateTheFishDataDirectory() {
        var settings = LaunchSettings.resolve(snapshotWith("fish", ShellIntegrationMode.AUTO), "Mac OS X",
            Map.of("XDG_DATA_DIRS", "/opt/jasper/si/fish:/x"), 80, 24, Path.of("/opt/jasper/si"));
        assertThat(settings.environment()).containsEntry("XDG_DATA_DIRS", "/opt/jasper/si/fish:/x");
    }

    @Test void userEnvOverlayCanStillOverrideTheIntegrationDirectory() {
        var settings = LaunchSettings.resolve(
            snapshotWith("zsh", ShellIntegrationMode.AUTO, Map.of("JASPER_SHELL_INTEGRATION", "/my/own")),
            "Mac OS X", Map.of(), 80, 24, Path.of("/opt/jasper/si"));
        assertThat(settings.environment()).containsEntry("JASPER_SHELL_INTEGRATION", "/my/own");
        assertThat(settings.environment()).containsEntry("ZDOTDIR", "/my/own/zsh");
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.LaunchSettingsTest' --rerun-tasks`
Expected: FAIL on all six.

- [ ] **Step 3: Scrub Jasper's markers and move the export before the overlay**

```java
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_") || name.startsWith("JASPER_"));
        environment.put("TERM_PROGRAM", "Jasper");
        if (integrationDir != null && terminal.shellIntegration() != ShellIntegrationMode.OFF) {
            environment.put("JASPER_SHELL_INTEGRATION", integrationDir.toString());
        }
        environment.putAll(terminal.env());
```

The scrub matters because the scripts export `JASPER_INTEGRATION_LOADED=1` and guard on it: without this, a Jasper launched from an integrated pane gets no marks in any pane. Replace the old injection block after the `TERM`/`COLORTERM` puts with one that honours a user override:

```java
        if (integrationDir != null && terminal.shellIntegration() == ShellIntegrationMode.AUTO) {
            String effective = environment.get("JASPER_SHELL_INTEGRATION");
            try {
                if (effective != null && !effective.isBlank()) inject(command, environment, Path.of(effective));
            } catch (InvalidPathException ignored) {
                // A user-supplied value that is not a path: variables only, like any other program.
            }
        }
```

- [ ] **Step 4: Make the bash arm option-aware**

```java
            case "bash" -> {
                // bash honours only the last --rcfile and ignores it under --norc or -c, so a user who
                // chose either keeps their command exactly as written and gets the variables only.
                int options = command.size();
                boolean login = false;
                boolean noProfile = false;
                for (int i = 1; i < command.size(); i++) {
                    String argument = command.get(i);
                    boolean cluster = argument.length() > 1 && argument.charAt(0) == '-' && argument.charAt(1) != '-';
                    if (argument.equals("--") || argument.equals("-") || !argument.startsWith("-")) { options = i; break; }
                    if (argument.equals("--norc") || argument.equals("--rcfile") || argument.equals("--init-file")
                        || argument.equals("-c") || (cluster && argument.indexOf('c') > 0)) return;
                    noProfile |= argument.equals("--noprofile");
                    login |= argument.equals("--login") || (cluster && argument.indexOf('l') > 0);
                }
                for (int i = options - 1; i >= 1; i--) {
                    String argument = command.get(i);
                    if (argument.equals("-l") || argument.equals("--login")) {
                        command.remove(i);
                    } else if (argument.length() > 1 && argument.charAt(0) == '-' && argument.charAt(1) != '-'
                        && argument.indexOf('l') > 0) {
                        String stripped = argument.replace("l", "");
                        if (stripped.equals("-")) command.remove(i); else command.set(i, stripped);
                    }
                }
                command.add(1, "--rcfile");
                command.add(2, dir.resolve("bash/rc.bash").toString());
                if (login && !noProfile) environment.put("JASPER_LOGIN_SHELL", "1");
            }
```

- [ ] **Step 5: Stop the fish data directory accumulating**

```java
            case "fish" -> {
                String jasper = dir.resolve("fish").toString();
                String existing = environment.get("XDG_DATA_DIRS");
                String rest = existing == null || existing.isBlank() ? "/usr/local/share:/usr/share" : existing;
                environment.put("XDG_DATA_DIRS",
                    rest.equals(jasper) || rest.startsWith(jasper + ":") ? rest : jasper + ":" + rest);
            }
```

Add to `inject`'s javadoc that it mutates `command` in place and so needs a mutable list, and add a one-line comment in the `default ->` arm noting that Windows basenames (`bash.exe`) never match, which is intended.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.LaunchSettingsTest' --rerun-tasks`
Expected: PASS, including every pre-existing test.

```bash
git add jasper-app/src/main/java/dev/jasper/app/LaunchSettings.java \
        jasper-app/src/test/java/dev/jasper/app/LaunchSettingsTest.java
git commit -m "fix: scrub Jasper markers and respect the user's own bash startup options"
```

---

### Task 5: extraction hardening

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellIntegrationScripts.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptsTest.java`

- [ ] **Step 1: Write the failing tests**

```java
    @Test void installReplacesASymlinkRatherThanWritingThroughIt() throws Exception {
        Path dir = directory.resolve("si");
        ShellIntegrationScripts.install(dir);
        Path outside = directory.resolve("outside.txt");
        Files.writeString(outside, "untouched");
        Files.delete(dir.resolve("jasper.zsh"));
        Files.createSymbolicLink(dir.resolve("jasper.zsh"), outside);

        ShellIntegrationScripts.install(dir);

        assertThat(Files.readString(outside)).isEqualTo("untouched");
        assertThat(Files.isSymbolicLink(dir.resolve("jasper.zsh"))).isFalse();
        assertThat(Files.readAllBytes(dir.resolve("jasper.zsh")))
            .isEqualTo(ShellIntegrationScripts.bundled("jasper.zsh"));
    }

    @Test void extractedFilesAreReadableOnlyByTheirOwner() throws Exception {
        Path dir = directory.resolve("si");
        ShellIntegrationScripts.install(dir);
        assertThat(Files.getPosixFilePermissions(dir.resolve("jasper.zsh")))
            .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test void aFailedInstallReportsRatherThanLeavingAPartialTree() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        assertThatThrownBy(() -> ShellIntegrationScripts.install(blocked.resolve("si")))
            .isInstanceOf(IOException.class);
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptsTest' --rerun-tasks`
Expected: FAIL — the symlink test finds `outside.txt` overwritten; the permission test finds `rw-r--r--`.

- [ ] **Step 3: Write atomically, never through a link, owner-only**

```java
    /** Writes each bundled file whose on-disk content differs; unchanged files keep their timestamps. */
    static Path install(Path dir) throws IOException {
        Files.createDirectories(dir);
        restrict(dir, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
        for (String name : FILES) {
            byte[] content = bundled(name);
            Path target = dir.resolve(name);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Arrays.equals(Files.readAllBytes(target), content)) continue;
            Files.createDirectories(target.getParent());
            // The user's login shell sources these, so replace rather than truncate: a crash mid-write
            // must never leave a half-written script, and a symlink must never redirect the write.
            Path staged = target.resolveSibling(target.getFileName() + ".jasper-new");
            try {
                Files.write(staged, content);
                restrict(staged, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staged);
            }
        }
        return dir;
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException windowsOrOtherFilesystem) {
            // Windows has no POSIX modes; the application directory's ACL already restricts it.
        }
    }
```

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptsTest' --rerun-tasks`
Expected: PASS.

```bash
git add jasper-app/src/main/java/dev/jasper/app/ShellIntegrationScripts.java \
        jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptsTest.java
git commit -m "fix: extract the integration scripts atomically, owner-only, never through a link"
```

---

### Task 6: TerminalSession receiving side

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/ShellIntegrationSessionTest.java`

**Interfaces:**
- Produces: `TerminalSession.shellIntegrationDetected()` — `boolean`, signature unchanged. `recordPrompt()` changes from `void` to `boolean` and is called only from the `A` arm.

- [ ] **Step 1: Write the failing tests**

```java
    @Test void aRepeatedPromptMarkDoesNotStealTheExitStatus() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007\033]1341;jasper;cmd;ZmFsc2U=\007\033]133;C\007");
        connector.feed("\033]133;A\007\033]133;A\007\033]133;D;1\007");

        Await.until(() -> !captured.isEmpty(), "command reported");
        assertThat(captured).containsExactly("false");
        assertThat(statuses).containsExactly(OptionalInt.of(1));
    }

    @Test void aCommandTextThatWasNeverUsedDoesNotLeakIntoTheNextCommand() throws Exception {
        listenForCommands();
        connector.feed("\033]1341;jasper;cmd;U1RBTEU=\007\033]133;D;0\007");
        connector.feed("\033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> !captured.isEmpty(), "command reported");
        assertThat(captured).containsExactly("typed");
    }

    @Test void aResetDiscardsAnUnusedCommandText() throws Exception {
        listenForCommands();
        connector.feed("\033]1341;jasper;cmd;QkVGT1JF\007\033c");
        connector.feed("\033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> !captured.isEmpty(), "command reported");
        assertThat(captured).containsExactly("typed");
    }

    @Test void anOverlongCommandPayloadFallsBackToTheScreen() throws Exception {
        listenForCommands();
        String huge = Base64.getEncoder().encodeToString("x".repeat(64 * 1024).getBytes(StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ \033]133;B\007typed\033]1341;jasper;cmd;" + huge
            + "\007\033]133;C\007\033]133;D;0\007");

        Await.until(() -> !captured.isEmpty(), "command reported");
        assertThat(captured).containsExactly("typed");
    }

    @Test void aPayloadThatIsNotUtf8FallsBackToTheScreen() throws Exception {
        listenForCommands();
        String invalid = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xC3, (byte) 0x28});
        connector.feed("\033]133;A\007$ \033]133;B\007typed\033]1341;jasper;cmd;" + invalid
            + "\007\033]133;C\007\033]133;D;0\007");

        Await.until(() -> !captured.isEmpty(), "command reported");
        assertThat(captured).containsExactly("typed");
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.ShellIntegrationSessionTest' --rerun-tasks`
Expected: FAIL — the status is `OptionalInt.empty`, `STALE` and `BEFORE` are captured, and the huge and invalid payloads are delivered instead of the screen text.

- [ ] **Step 3: Make `recordPrompt` report whether the prompt is new**

```java
    /** Records the prompt row; false when this A repeats the row Jasper already marked. */
    private boolean recordPrompt() {
        buffer.lock();
        try {
            long row = absoluteRow(terminal.getCursorY() - 1);
            if (!promptRows.isEmpty() && promptRows.getLast() == row) return false;
            promptRows.add(row);
            return true;
        } finally {
            buffer.unlock();
        }
    }
```

Rewrite the `A` arm so a shell that emits its own `A` (fish 4) cannot flush the cycle early and lose the exit status:

```java
                    case "A" -> {
                        shellIntegrationDetected = true;
                        if (recordPrompt()) {
                            flushPendingCommand(OptionalInt.empty());
                            commandStartRow = -1;
                            pendingCommandText = null;
                        }
                    }
```

If `shellIntegrationDetected = true` currently lives inside `recordPrompt`, move it to the `A` arm as shown, so a repeated `A` still counts as detection.

- [ ] **Step 4: Stop the command text outliving its cycle**

Add `pendingCommandText = null;` as the first statement of `flushPendingCommand`, and to both reset paths beside the existing `absoluteRowEpoch` / `promptRows` handling — the `historyCleared()` callback and the alternate-buffer consumer passed to `SessionDisplay`.

- [ ] **Step 5: Bound and strictly decode the payload**

```java
    /** Longer than this is not a command line; the history index caps its own lines the same way. */
    private static final int MAX_COMMAND_BYTES = 16 * 1024;

    /** Decodes the base64 UTF-8 {@code cmd} payload from Jasper's shell-integration scripts, or null if malformed. */
    private static String decodeCommand(String encoded) {
        String trimmed = encoded.trim();
        if (trimmed.length() > (MAX_COMMAND_BYTES / 3 + 1) * 4) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            if (bytes.length > MAX_COMMAND_BYTES) return null;
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.isEmpty() ? null : text;
        } catch (IllegalArgumentException | CharacterCodingException malformed) {
            return null;
        }
    }
```

`.strip()` is deliberately gone: the payload is the exact command line and the scripts already strip what needs stripping. Import `java.util.Base64`, `java.nio.ByteBuffer` and `java.nio.charset.CharacterCodingException` rather than keeping the file's inline fully-qualified `java.util.Base64`. Make the `cmd` arm join its remaining arguments the way `cwd` does, so both arms treat the OSC split identically:

```java
            case "cmd" -> pendingCommandText = args.size() > 2
                ? decodeCommand(String.join(";", args.subList(2, args.size()))) : null;
```

Move the `shellIntegrationDetected` field declaration above the `// Reader thread only:` comment, beside the other `volatile` fields — it is read from the EDT.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew :jasper-terminal:test --rerun-tasks`
Expected: PASS, every class.

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java \
        jasper-terminal/src/test/java/dev/jasper/terminal/ShellIntegrationSessionTest.java
git commit -m "fix: keep the exit status through a repeated prompt mark and bound the cmd payload"
```

---

### Task 7: status-bar wiring and its tests

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowStatusBar.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/MockUiTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ConfiguredTerminalBehaviorTest.java`

The branch's only status-bar test calls `setMetadata` directly, so `WindowContent`'s call site and `TerminalPane.shellIntegrationDetected()` could both be deleted and the suite would stay green. Both tooltip strings the spec names are asserted nowhere.

- [ ] **Step 1: Write the failing tests**

```java
    @Test void theIntegrationDotCarriesItsMeaningForToolTipsAndScreenReaders() {
        WindowStatusBar bar = new WindowStatusBar();
        bar.setMetadata("zsh", "/tmp", "80x24", true, false);
        assertThat(bar.getAccessibleContext().getAccessibleDescription()).contains("shell integration not detected");

        bar.setMetadata("zsh", "/tmp", "80x24", true, true);
        assertThat(bar.getAccessibleContext().getAccessibleDescription()).contains("shell integration active");
    }
```

and, in `ConfiguredTerminalBehaviorTest`, the wiring assertion that fails if `WindowContent` passes a constant:

```java
    @Test void theStatusBarReportsShellIntegrationOnceTheShellMarksAPrompt() throws Exception {
        edt(() -> assertThat(owner.status().getText()).contains("○"));

        session.write("mark\n");   // the controlled child prints ESC ] 133 ; A BEL for this word

        until(() -> owner.status().getText().contains("●"));
    }
```

Extend the controlled `/bin/sh` fixture in that class's `start()` with a `mark) printf '\033]133;A\007' ;;` case, matching the existing `bell)` case, rather than adding production API for the test.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.MockUiTest' --tests 'dev.jasper.app.ConfiguredTerminalBehaviorTest' --rerun-tasks`
Expected: FAIL — the accessible description holds the bare glyph, and the status text never gains the filled dot.

- [ ] **Step 3: Delete the dead overload and give the glyph its words**

Remove the 4-argument `setMetadata` entirely and update its four test call sites to pass the integration flag explicitly — it silently defaulted `integration` to `false` and would mask a wiring regression. Extract the expression duplicated between `setMetadata` and `updateText`:

```java
    private String shellLabel() {
        return shell.isEmpty() ? "" : shell + (integration ? " ●" : " ○");
    }
```

and make the accessible description name the state rather than read out the glyph:

```java
        String spoken = shell.isEmpty() ? ""
            : shell + (integration ? ", shell integration active" : ", shell integration not detected");
        text = shell.isEmpty() ? configText : shellLabel() + "  |  " + directory + "  |  " + dimensions + "  |  " + configText;
        getAccessibleContext().setAccessibleDescription(shell.isEmpty() ? text
            : spoken + "  |  " + directory + "  |  " + dimensions + "  |  " + configText);
```

`getText()` keeps returning the glyph form, so the existing render fixtures are unchanged.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :jasper-app:test --rerun-tasks`
Expected: PASS.

```bash
git add jasper-app/src/main/java/dev/jasper/app/WindowStatusBar.java \
        jasper-app/src/test/java/dev/jasper/app/MockUiTest.java \
        jasper-app/src/test/java/dev/jasper/app/ConfiguredTerminalBehaviorTest.java
git commit -m "test: prove the status bar reports integration from a real prompt mark"
```

---

### Task 8: a real shell through a real session

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationEndToEndTest.java`

The spec's Testing section specifies real shells driven "through `TerminalSession.start`" with assertions "through the listener". The branch tests the scripts on pipes and the receiving side on hand-written feeds, so the two halves never meet; prompt rows and the empty-Enter case have no real-shell coverage at all.

- [ ] **Step 1: Write the test**

```java
@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationEndToEndTest {
    @TempDir Path home;

    @Test void zshReportsEachCommandItsStatusAndItsPromptRowsThroughTheSession() throws Exception {
        Path zsh = Path.of("/bin/zsh");
        Assumptions.assumeTrue(Files.isExecutable(zsh), "zsh is not installed");
        Path scripts = ShellIntegrationScripts.install(home.resolve("si"));
        Files.writeString(home.resolve(".zshrc"), "PS1='rc%% '\n");

        var commands = new CopyOnWriteArrayList<String>();
        var statuses = new CopyOnWriteArrayList<OptionalInt>();
        LaunchSettings settings = LaunchSettings.resolve(snapshotFor(zsh), "Mac OS X",
            Map.of("HOME", home.toString(), "PATH", System.getenv("PATH")), 80, 24, scripts);
        try (TerminalSession session = TerminalSession.start(settings.command(), settings.environment(),
                home, 80, 24, 1000)) {
            session.addListener(new TerminalSession.Listener() {
                @Override public void commandExecuted(String command, OptionalInt status, Optional<Path> directory) {
                    commands.add(command);
                    statuses.add(status);
                }
            });
            until(session::shellIntegrationDetected);
            session.write("printf 'hello\\n'\n");
            until(() -> commands.size() == 1);
            session.write("\n");                       // an empty Enter reports nothing
            session.write("false\n");
            until(() -> commands.size() == 2);

            assertThat(commands).containsExactly("printf 'hello\\n'", "false");
            assertThat(statuses).containsExactly(OptionalInt.of(0), OptionalInt.of(1));
            assertThat(session.promptRows()).hasSizeGreaterThanOrEqualTo(3);
        }
    }
}
```

`snapshotFor(zsh)` builds a `ConfigSnapshot` whose shell program is the zsh path with `ShellIntegrationMode.AUTO`, so the test exercises the real injection rather than a hand-built copy of it. `until(...)` polls with the 5-second deadline the other tests in this package use.

- [ ] **Step 2: Run it**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationEndToEndTest' --rerun-tasks`
Expected: PASS once Tasks 1–6 are in. A failure here is a real defect in the branch, not in the test — diagnose before touching the assertions.

- [ ] **Step 3: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/ShellIntegrationEndToEndTest.java
git commit -m "test: drive a real zsh through TerminalSession and assert through the listener"
```

---

### Task 9: documentation, spec amendments and STATUS

**Files:**
- Modify: `docs/configuration.md`
- Modify: `docs/superpowers/specs/2026-09-16-jasper-shell-integration-design.md`
- Modify: `docs/STATUS.md`

- [ ] **Step 1: Disclose the limitations where users hit them**

In `docs/configuration.md`, the bash bullet gains: a `DEBUG` trap installed after the first prompt displaces Jasper's, and a chained user trap sees `$?`, `$_` and `BASH_COMMAND` from Jasper's wrapper rather than from the user's own command — the case a `bash-preexec` or `direnv` user lands in. A new sentence records that a command hidden from history by `HISTCONTROL=ignorespace` is hidden from Jasper too: no History entry and no `C` mark. The fish bullet records that the scripts are unverified against a real fish. A sentence records that `--norc`, `--rcfile`, `--init-file` or `-c` turns injection off and leaves the user's startup exactly as written.

- [ ] **Step 2: Amend the spec**

Under "The scripts", record that bash honours `ignorespace`. Under "Injection at launch", record that `--norc`/`--rcfile`/`--init-file`/`-c` skip injection, that `-l` inside a short cluster counts, and that `JASPER_*` markers are scrubbed from the inherited environment. Under "Jasper's receiving side", record the 16 KiB payload bound and that a repeated `A` does not flush the cycle. Under "Testing", record that the script tests run on pipes while `ShellIntegrationEndToEndTest` covers the session path.

- [ ] **Step 3: Rewrite the STATUS entry**

Describe the code as it now stands, list the fixes, keep the two original deviations, correct "main thread" to "on the EDT before the first window", and record the remaining user-run items: each shell on the real desktop, fish if installed, and a look at the two adjacent status-bar dots and the status bar at a narrow window width.

- [ ] **Step 4: Run the whole suite and record real numbers**

Run: `./gradlew check --rerun-tasks`
Read exact counts from `*/build/test-results/test/TEST-*.xml` and put those numbers — not estimates — into STATUS.

- [ ] **Step 5: Commit**

```bash
git add docs/configuration.md docs/STATUS.md \
        docs/superpowers/specs/2026-09-16-jasper-shell-integration-design.md \
        docs/superpowers/plans/2026-09-16-jasper-shell-integration-review-fixes.md
git commit -m "docs: record the review fixes, the honoured ignorespace and the remaining limits"
```

---

## Self-review

**Finding coverage.** fish re-wrap → Task 2. `set -u`, readonly, `printf`/`base64`, the `__jasper_` anchor, `ignorespace` → Task 1. `rc.bash` double-source and `.zshenv` → Task 3. Environment scrub, bash options, `--noprofile`, `XDG_DATA_DIRS`, precedence, the `inject` contract, the Windows comment → Task 4. Symlink, permissions, atomicity, failure reporting → Task 5. Duplicate `A`, stale text, payload cap, strict UTF-8, `.strip()`, the argument join, field placement → Task 6. Dead overload, duplicated expression, accessibility, tooltips, wiring → Task 7. Session-path and prompt-row coverage → Task 8. Docs, spec, STATUS, the "main thread" wording → Task 9.

**Deliberately not done.** The status-bar dot arrangement and its clipping order: visual, unverifiable headlessly, recorded as a user-run item (decision 5). Nushell and PowerShell scripts, which the spec puts out of scope.

**Type consistency.** `recordPrompt()` becomes `boolean` in Task 6, called only from the `A` arm. `setMetadata` loses its 4-argument overload in Task 7, with all four test call sites updated in the same task. `ShellRun.interactive`, `ShellRun.interactiveFish` and `ShellRun.nonInteractiveZdotdir` are introduced in Task 1 and reused in Tasks 2 and 3. `ShellIntegrationScripts.install` keeps `Path install(Path)` throughout.
