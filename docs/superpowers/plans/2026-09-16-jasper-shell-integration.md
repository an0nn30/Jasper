# Shell Integration Scripts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship zsh, bash and fish integration scripts that emit OSC 7, OSC 133 A/B/C/D and the exact command line, load them automatically at launch, and show whether they are active.

**Architecture:** Scripts live as resources and are extracted once per start into `<app dir>/shell-integration/`. `LaunchSettings` injects them per shell (zsh `ZDOTDIR` wrappers, bash `--rcfile` with login emulation, fish `XDG_DATA_DIRS` vendor snippet) under a `terminal.shell_integration` setting. `TerminalSession` learns the `cmd` payload and a detected flag; the status bar shows a dot.

**Tech Stack:** Java 25 on JetBrains Runtime, Swing/FlatLaf, TomlJ, Gradle wrapper, JUnit 5, AssertJ; zsh 5.x, bash 3.2+, fish 3.x/4.x for the scripts.

**Spec:** `docs/superpowers/specs/2026-09-16-jasper-shell-integration-design.md`.

**Status:** Not started. Development branch `claude/shell-integration` in `.worktrees/shell-integration` from main `27999fe`.

**Recorded deviation from the spec text:** extraction runs on the main thread in `Main` before the application is created (a few small file writes), not on the configuration worker. Record any further deviation in this banner and in `docs/STATUS.md`.

## Global Constraints

- Java 25 on the JetBrains Runtime. Use `./gradlew`, never a system Gradle. Run everything from `/Users/dustin/projects/moray/.worktrees/shell-integration`.
- Modules: `jasper-terminal` (`dev.jasper.terminal`) gains only the `cmd` custom command and a `shellIntegrationDetected()` flag; everything else is in `jasper-app` (`dev.jasper.app`). No public method in `jasper-terminal` takes or returns a JediTerm type.
- The scripts: under about 80 lines each; guarded on interactive, `TERM_PROGRAM=Jasper`, and `JASPER_INTEGRATION_LOADED`; emit A/B by wrapping the prompt once per cycle with a marker check; OSC 7 only when the directory changed, path percent-encoded byte by byte except `A-Z a-z 0-9 / . _ ~ -`; `cmd` as `OSC 1341;jasper;cmd;<base64 without newlines>` then C from the pre-exec hook; D with the exit status only after a command ran; no aliases, prompt text or history changes. bash must work on 3.2.
- Extraction writes only files whose content differs. Layout: `jasper.zsh jasper.bash jasper.fish zsh/.zshenv zsh/.zprofile zsh/.zshrc zsh/.zlogin bash/rc.bash fish/fish/vendor_conf.d/jasper.fish`.
- `LaunchSettings`: always `TERM_PROGRAM=Jasper` (set before the user's `[terminal.env]` overlay so a user override wins); `JASPER_SHELL_INTEGRATION=<dir>` in `manual` and `auto`; injection only in `auto` and only for programs whose basename is `zsh`, `bash` or `fish`; user `ZDOTDIR` travels as `JASPER_ORIGINAL_ZDOTDIR`; bash `-l`/`--login` removed with `JASPER_LOGIN_SHELL=1`; fish keeps the previous `XDG_DATA_DIRS` (default `/usr/local/share:/usr/share`) after ours.
- Real-shell tests run only where the shell binary exists (`Assumptions.assumeTrue`), never touch the user's dotfiles (temporary `HOME`), and use pipes with `-i`, merging stderr into stdout so prompt output and marks keep their order.
- Never put raw control, private-use or unpaired surrogate characters in Java source (`\033`, `\007` escapes). Script files may of course contain `$'\033'`-style escapes as text, never raw bytes.
- No GUI, benchmark, merge or push during execution. Every commit ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## File structure

| File | Responsibility |
|---|---|
| `jasper-app/src/main/resources/dev/jasper/app/shell-integration/…` | The nine script and wrapper files |
| `ShellIntegrationScripts.java` | Bundled file list, content-compare extraction |
| `ShellIntegrationMode.java`, `TerminalConfig.java`, `ConfigLoader.java`, `ConfigTemplate.java`, `config.example.toml`, `docs/configuration.md` | The `terminal.shell_integration` setting |
| `LaunchSettings.java` | `TERM_PROGRAM`, exports and per-shell injection |
| `TerminalSession.java` (`jasper-terminal`) | `cmd` payload, `shellIntegrationDetected()` |
| `TerminalPane.java`, `WindowContent.java`, `WindowStatusBar.java` | The status-bar dot |
| `AppDirs.java`, `Main.java`, `JasperApplication.java` | Extraction at startup and passing the directory to launches |
| `ShellRun.java` (test) | Runs a real shell on pipes for the script tests |
| docs | Configuration guide section, palette/STATUS caveat removal, STATUS entry |

---

### Task 1: The scripts, the wrappers, and extraction

**Files:**
- Create the nine resources under `jasper-app/src/main/resources/dev/jasper/app/shell-integration/`
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellIntegrationScripts.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppDirs.java` (add `shellIntegration()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptsTest.java`

**Interfaces:**
- Produces: `ShellIntegrationScripts.FILES` (the nine relative names, in the layout order above), `RESOURCE_ROOT = "dev/jasper/app/shell-integration/"`, `static byte[] bundled(String name) throws IOException`, `static Path install(Path dir) throws IOException` (returns `dir`; writes only differing files; creates parents), `AppDirs.shellIntegration()` = `root/shell-integration`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellIntegrationScriptsTest {
    @TempDir Path dir;

    @Test void installWritesEveryFileOnceAndRewritesOnlyChangedContent() throws Exception {
        Path target = dir.resolve("shell-integration");
        assertThat(ShellIntegrationScripts.install(target)).isEqualTo(target);
        assertThat(ShellIntegrationScripts.FILES).containsExactly("jasper.zsh", "jasper.bash", "jasper.fish",
            "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin", "bash/rc.bash", "fish/fish/vendor_conf.d/jasper.fish");
        for (String name : ShellIntegrationScripts.FILES) {
            assertThat(target.resolve(name)).isRegularFile();
            assertThat(Files.readAllBytes(target.resolve(name))).isEqualTo(ShellIntegrationScripts.bundled(name));
        }
        Path zsh = target.resolve("jasper.zsh");
        Files.setLastModifiedTime(zsh, FileTime.fromMillis(1_000_000_000_000L));
        ShellIntegrationScripts.install(target);
        assertThat(Files.getLastModifiedTime(zsh)).isEqualTo(FileTime.fromMillis(1_000_000_000_000L));
        Files.writeString(zsh, "# stale\n");
        ShellIntegrationScripts.install(target);
        assertThat(Files.readAllBytes(zsh)).isEqualTo(ShellIntegrationScripts.bundled("jasper.zsh"));
        assertThat(AppDirs.resolve("Mac OS X", Map.of(), Path.of("/Users/j")).shellIntegration())
            .isEqualTo(Path.of("/Users/j/.config/jasper/shell-integration"));
    }

    @Test void everyScriptIsGuardedAndSyntacticallyValidWhereShellsExist() throws Exception {
        Path target = ShellIntegrationScripts.install(dir.resolve("shell-integration"));
        for (String name : List.of("jasper.zsh", "jasper.bash", "jasper.fish")) {
            String text = Files.readString(target.resolve(name), StandardCharsets.UTF_8);
            assertThat(text).contains("TERM_PROGRAM").contains("JASPER_INTEGRATION_LOADED").contains("133;C").contains("1341;jasper;cmd;");
            assertThat(text.lines().count()).isLessThanOrEqualTo(90);
        }
        if (Files.isExecutable(Path.of("/bin/zsh")))
            for (String name : List.of("jasper.zsh", "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin"))
                assertThat(exit(List.of("/bin/zsh", "-n", target.resolve(name).toString()))).as(name).isZero();
        if (Files.isExecutable(Path.of("/bin/bash")))
            for (String name : List.of("jasper.bash", "bash/rc.bash"))
                assertThat(exit(List.of("/bin/bash", "-n", target.resolve(name).toString()))).as(name).isZero();
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")) || Files.isExecutable(Path.of("/bin/bash")), "no shell to syntax-check");
    }

    private static int exit(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptsTest'`
Expected: compilation failure.

- [ ] **Step 3: Write the scripts**

`jasper.zsh`:

```zsh
# Jasper shell integration for zsh. Jasper loads this automatically when terminal.shell_integration = "auto";
# otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"` to your .zshrc.
[[ -o interactive ]] || return 0
[[ "$TERM_PROGRAM" == "Jasper" ]] || return 0
[[ -n "$JASPER_INTEGRATION_LOADED" ]] && return 0
export JASPER_INTEGRATION_LOADED=1

autoload -Uz add-zsh-hook

__jasper_osc() { printf '\033]%s\007' "$1"; }

# Percent-encodes a path byte by byte, keeping unreserved characters and slashes.
__jasper_encode() {
    emulate -L zsh
    setopt no_multibyte
    local input="$1" out="" i c
    for (( i = 1; i <= ${#input}; i++ )); do
        c="${input[i]}"
        case "$c" in
            [A-Za-z0-9/._~-]) out+="$c" ;;
            *) out+="$(printf '%%%02X' $(( #c & 255 )))" ;;
        esac
    done
    printf '%s' "$out"
}

__jasper_mark_a=$'\033]133;A\007'
__jasper_mark_b=$'\033]133;B\007'
__jasper_command_ran=""
__jasper_last_pwd=""

__jasper_precmd() {
    local code=$?
    if [[ -n "$__jasper_command_ran" ]]; then
        __jasper_osc "133;D;$code"
        __jasper_command_ran=""
    fi
    if [[ "$PWD" != "$__jasper_last_pwd" ]]; then
        __jasper_last_pwd="$PWD"
        __jasper_osc "7;file://${HOST:-$(hostname)}$(__jasper_encode "$PWD")"
    fi
    if [[ "$PROMPT" != *"$__jasper_mark_a"* ]]; then
        PROMPT="%{$__jasper_mark_a%}$PROMPT%{$__jasper_mark_b%}"
    fi
}

__jasper_preexec() {
    __jasper_command_ran=1
    local encoded
    if encoded="$(printf '%s' "$1" | base64 2>/dev/null | tr -d '\n')" && [[ -n "$encoded" ]]; then
        __jasper_osc "1341;jasper;cmd;$encoded"
    fi
    __jasper_osc "133;C"
}

add-zsh-hook precmd __jasper_precmd
add-zsh-hook preexec __jasper_preexec
```

`jasper.bash`:

```bash
# Jasper shell integration for bash 3.2 and newer. Jasper loads this automatically when
# terminal.shell_integration = "auto"; otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.bash"` to your .bashrc.
[[ $- == *i* ]] || return 0
[[ "$TERM_PROGRAM" == "Jasper" ]] || return 0
[[ -n "$JASPER_INTEGRATION_LOADED" ]] && return 0
export JASPER_INTEGRATION_LOADED=1

__jasper_osc() { printf '\033]%s\007' "$1"; }

# Percent-encodes a path byte by byte, keeping unreserved characters and slashes.
__jasper_encode() {
    local LC_ALL=C input="$1" out="" i c code
    for (( i = 0; i < ${#input}; i++ )); do
        c="${input:i:1}"
        case "$c" in
            [A-Za-z0-9/._~-]) out+="$c" ;;
            *) code=$(printf '%d' "'$c"); out+="$(printf '%%%02X' $(( code & 255 )))" ;;
        esac
    done
    printf '%s' "$out"
}

__jasper_mark_a=$'\033]133;A\007'
__jasper_mark_b=$'\033]133;B\007'
__jasper_command_ran=""
__jasper_last_pwd=""
__jasper_last_status=0
__jasper_in_prompt=""
__jasper_prompt_history=""

__jasper_capture_status() { __jasper_last_status=$?; }

__jasper_prompt_command() {
    if [[ -n "$__jasper_command_ran" ]]; then
        __jasper_osc "133;D;$__jasper_last_status"
        __jasper_command_ran=""
    fi
    if [[ "$PWD" != "$__jasper_last_pwd" ]]; then
        __jasper_last_pwd="$PWD"
        __jasper_osc "7;file://${HOSTNAME:-$(hostname)}$(__jasper_encode "$PWD")"
    fi
    if [[ "$PS1" != *"$__jasper_mark_a"* ]]; then
        PS1="\[$__jasper_mark_a\]$PS1\[$__jasper_mark_b\]"
    fi
    __jasper_prompt_history="$(HISTTIMEFORMAT= builtin history 1 | sed -E '1!d; s/^ *([0-9]+).*/\1/')"
    __jasper_in_prompt=1
}

# Fires before each simple command; the first one after a prompt is the user's command line.
__jasper_debug_trap() {
    [[ -n "$__jasper_in_prompt" ]] || return 0
    case "$BASH_COMMAND" in *__jasper_*) return 0 ;; esac
    __jasper_in_prompt=""
    __jasper_command_ran=1
    local entry number line encoded
    entry="$(HISTTIMEFORMAT= builtin history 1)"
    number="$(printf '%s\n' "$entry" | sed -E '1!d; s/^ *([0-9]+).*/\1/')"
    if [[ -z "$number" || "$number" == "$__jasper_prompt_history" ]]; then
        line="$BASH_COMMAND"
    else
        line="$(printf '%s\n' "$entry" | sed -E '1s/^ *[0-9]+ +//')"
    fi
    if encoded="$(printf '%s' "$line" | base64 2>/dev/null | tr -d '\n')" && [[ -n "$encoded" ]]; then
        __jasper_osc "1341;jasper;cmd;$encoded"
    fi
    __jasper_osc "133;C"
}

if [[ "$(declare -p PROMPT_COMMAND 2>/dev/null)" == "declare -a"* ]]; then
    PROMPT_COMMAND=(__jasper_capture_status "${PROMPT_COMMAND[@]}" __jasper_prompt_command)
else
    __jasper_existing="${PROMPT_COMMAND}"
    while [[ "$__jasper_existing" == *";" || "$__jasper_existing" == *" " ]]; do __jasper_existing="${__jasper_existing%?}"; done
    if [[ -n "$__jasper_existing" ]]; then
        PROMPT_COMMAND="__jasper_capture_status;${__jasper_existing};__jasper_prompt_command"
    else
        PROMPT_COMMAND="__jasper_capture_status;__jasper_prompt_command"
    fi
    unset __jasper_existing
fi

__jasper_previous_debug="$(trap -p DEBUG)"
__jasper_previous_debug="${__jasper_previous_debug#trap -- }"
__jasper_previous_debug="${__jasper_previous_debug% DEBUG}"
if [[ -n "$__jasper_previous_debug" && "$__jasper_previous_debug" != "''" ]]; then
    eval "trap '__jasper_debug_trap; '$__jasper_previous_debug DEBUG"
else
    trap '__jasper_debug_trap' DEBUG
fi
unset __jasper_previous_debug
```

`jasper.fish`:

```fish
# Jasper shell integration for fish 3.0 and newer. Jasper loads this automatically when
# terminal.shell_integration = "auto"; otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.fish"` to config.fish.
if status is-interactive; and test "$TERM_PROGRAM" = Jasper; and not set -q JASPER_INTEGRATION_LOADED
    set -gx JASPER_INTEGRATION_LOADED 1

    function __jasper_osc
        printf '\033]%s\007' $argv[1]
    end

    function __jasper_encode
        string escape --style=url -- $argv[1] | string replace -a '%2F' '/'
    end

    set -g __jasper_last_status 0
    set -g __jasper_last_pwd ''

    function __jasper_postexec --on-event fish_postexec
        set -g __jasper_last_status $status
        set -g __jasper_command_ran 1
    end

    function __jasper_precmd --on-event fish_prompt
        if set -q __jasper_command_ran
            __jasper_osc "133;D;$__jasper_last_status"
            set -e __jasper_command_ran
        end
        if test "$PWD" != "$__jasper_last_pwd"
            set -g __jasper_last_pwd $PWD
            __jasper_osc "7;file://$hostname"(__jasper_encode $PWD)
        end
        __jasper_osc "133;A"
    end

    function __jasper_preexec --on-event fish_preexec
        set -l encoded (printf '%s' $argv[1] | base64 2>/dev/null | string join '')
        if test -n "$encoded"
            __jasper_osc "1341;jasper;cmd;$encoded"
        end
        __jasper_osc "133;C"
    end

    if functions -q fish_prompt
        functions -c fish_prompt __jasper_original_prompt
    else
        function __jasper_original_prompt
            printf '%s> ' (prompt_pwd)
        end
    end
    function fish_prompt
        __jasper_original_prompt
        __jasper_osc "133;B"
    end
end
```

`zsh/.zshenv`:

```zsh
# Jasper wrapper: restores your ZDOTDIR, loads your .zshenv, then hands the next startup file back to Jasper.
__jasper_zdotdir="$ZDOTDIR"
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zshenv" ]] && source "${ZDOTDIR:-$HOME}/.zshenv"
if [[ -n "$ZDOTDIR" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
export ZDOTDIR="$__jasper_zdotdir"
unset __jasper_zdotdir
```

`zsh/.zprofile`: identical to `.zshenv` with `.zprofile` in place of `.zshenv` in the comment and the two `source` lines.

`zsh/.zshrc`:

```zsh
# Jasper wrapper: loads your .zshrc, then Jasper's integration; ZDOTDIR is yours again afterwards.
__jasper_zdotdir="$ZDOTDIR"
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zshrc" ]] && source "${ZDOTDIR:-$HOME}/.zshrc"
source "${JASPER_SHELL_INTEGRATION:-${__jasper_zdotdir:h}}/jasper.zsh"
if [[ -o login ]]; then
    if [[ -n "$ZDOTDIR" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
    export ZDOTDIR="$__jasper_zdotdir"
else
    unset JASPER_ORIGINAL_ZDOTDIR
fi
unset __jasper_zdotdir
```

`zsh/.zlogin`:

```zsh
# Jasper wrapper: loads your .zlogin and leaves ZDOTDIR as yours.
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zlogin" ]] && source "${ZDOTDIR:-$HOME}/.zlogin"
unset JASPER_ORIGINAL_ZDOTDIR
```

`bash/rc.bash`:

```bash
# Jasper wrapper: loads your startup files the way bash would, then Jasper's integration.
if [[ -n "$JASPER_LOGIN_SHELL" ]]; then
    unset JASPER_LOGIN_SHELL
    [[ -r /etc/profile ]] && source /etc/profile
    for __jasper_profile in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
        if [[ -r "$__jasper_profile" ]]; then source "$__jasper_profile"; break; fi
    done
    unset __jasper_profile
else
    [[ -r /etc/bash.bashrc ]] && source /etc/bash.bashrc
    [[ -r /etc/bashrc ]] && source /etc/bashrc
    [[ -r "$HOME/.bashrc" ]] && source "$HOME/.bashrc"
fi
source "${JASPER_SHELL_INTEGRATION:-$(dirname "${BASH_SOURCE[0]}")/..}/jasper.bash"
```

`fish/fish/vendor_conf.d/jasper.fish`:

```fish
# Jasper loader: fish finds this through XDG_DATA_DIRS and it pulls in the real script.
if set -q JASPER_SHELL_INTEGRATION; and test -r "$JASPER_SHELL_INTEGRATION/jasper.fish"
    source "$JASPER_SHELL_INTEGRATION/jasper.fish"
end
```

`ShellIntegrationScripts.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** The bundled shell integration files and their extraction into the application directory. */
final class ShellIntegrationScripts {
    static final String RESOURCE_ROOT = "dev/jasper/app/shell-integration/";
    static final List<String> FILES = List.of("jasper.zsh", "jasper.bash", "jasper.fish",
        "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin", "bash/rc.bash", "fish/fish/vendor_conf.d/jasper.fish");

    private ShellIntegrationScripts() {}

    static byte[] bundled(String name) throws IOException {
        try (InputStream in = ShellIntegrationScripts.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + name)) {
            if (in == null) throw new IOException("Missing bundled shell integration file: " + name);
            return in.readAllBytes();
        }
    }

    /** Writes each bundled file whose on-disk content differs; unchanged files keep their timestamps. */
    static Path install(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String name : FILES) {
            byte[] content = bundled(name);
            Path target = dir.resolve(name);
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) continue;
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        }
        return dir;
    }
}
```

`AppDirs`: add `Path shellIntegration() { return root.resolve("shell-integration"); }`.

- [ ] **Step 4: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptsTest'`
Expected: PASS (2 tests). Gradle copies dot-files from `src/main/resources` by default; if `.zshenv` is missing from the build output, add `include("**/.*")` handling to the `processResources` task in `jasper-app/build.gradle.kts` and say so in the report.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/resources/dev/jasper/app/shell-integration jasper-app/src/main/java/dev/jasper/app/ShellIntegrationScripts.java jasper-app/src/main/java/dev/jasper/app/AppDirs.java jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptsTest.java
git commit -m "feat: bundle zsh, bash and fish shell integration scripts

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: The scripts against real zsh and bash

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/ShellRun.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java`
- Modify the script resources from Task 1 as needed to make the tests pass.

**Interfaces:**
- Produces: `ShellRun.run(List<String> command, Map<String,String> environment, Path directory, String input) -> String` (merged stdout+stderr, decoded as UTF-8, process killed after 10 seconds); constants `ShellRun.A = "\033]133;A\007"`, `B`, `C`, `D(int)` -> `"\033]133;D;<n>\007"`, `CMD(String)` -> `"\033]1341;jasper;cmd;" + base64 + "\007"`, `CWD(String host, String encodedPath)`.

- [ ] **Step 1: Write the helper and the failing tests**

`ShellRun.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs a real shell on pipes with -i so its integration script loads; stderr (prompts) is merged in order. */
final class ShellRun {
    static final String A = "\033]133;A\007";
    static final String B = "\033]133;B\007";
    static final String C = "\033]133;C\007";
    static String D(int status) { return "\033]133;D;" + status + "\007"; }
    static String CMD(String command) {
        return "\033]1341;jasper;cmd;" + Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8)) + "\007";
    }
    static String CWD(String host, String encodedPath) { return "\033]7;file://" + host + encodedPath + "\007"; }

    private ShellRun() {}

    static String run(List<String> command, Map<String, String> environment, Path directory, String input)
            throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        }
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        return new String(output, StandardCharsets.UTF_8);
    }

    static String hostname() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    }
}
```

`ShellIntegrationScriptTest.java`:

```java
package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationScriptTest {
    @TempDir Path dir;

    private Path scripts() throws Exception { return ShellIntegrationScripts.install(dir.resolve("shell-integration")); }

    private Map<String, String> environment(Path home) {
        var env = new HashMap<String, String>();
        env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("HOME", home.toString());
        env.put("TERM", "xterm-256color");
        env.put("TERM_PROGRAM", "Jasper");
        env.put("LANG", "en_US.UTF-8");
        return env;
    }

    private static int indexAfter(String output, String needle, int from) {
        int at = output.indexOf(needle, from);
        assertThat(at).as("expected %s after offset %d in:%n%s", needle.replace("\033", "ESC").replace("\007", "BEL"), from, output).isGreaterThanOrEqualTo(0);
        return at + needle.length();
    }

    @Test void zshEmitsMarksDirectoryAndTheExactCommandLine() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home with space"));
        Path work = Files.createDirectories(home.resolve("my dir"));
        Files.writeString(home.resolve(".zshrc"), "export RC_RAN=yes\nPROMPT='rc%% '\nsource \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("ZDOTDIR", home.toString());
        String output = ShellRun.run(List.of("/bin/zsh", "-i"), env, work, "echo $RC_RAN\ncd ..\nfalse\necho \"one\ntwo\"\n\nexit\n");
        String host = ShellRun.hostname();
        String encodedWork = work.toString().replace(" ", "%20");
        int at = indexAfter(output, ShellRun.CWD(host, encodedWork), 0);
        at = indexAfter(output, ShellRun.A, at);
        at = indexAfter(output, "rc% ", at);
        at = indexAfter(output, ShellRun.B, at);
        at = indexAfter(output, ShellRun.CMD("echo $RC_RAN"), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "yes", at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CMD("cd .."), at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CWD(host, home.toString().replace(" ", "%20")), at);
        at = indexAfter(output, ShellRun.CMD("false"), at);
        at = indexAfter(output, ShellRun.D(1), at);
        at = indexAfter(output, ShellRun.CMD("echo \"one\ntwo\""), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, ShellRun.D(0), at);
        // The empty Enter yields a prompt with A and B but no D between them.
        int emptyA = indexAfter(output, ShellRun.A, at);
        int nextD = output.indexOf("\033]133;D;", emptyA);
        int nextC = output.indexOf(ShellRun.C, emptyA);
        assertThat(nextD == -1 || nextD > nextC).isTrue();
        assertThat(output.split(java.util.regex.Pattern.quote(ShellRun.A), -1).length - 1).isEqualTo(6);
        assertThat(output).doesNotContain("dquote>");
    }

    @Test void zshDoesNotDoubleLoadOrRunOutsideJasper() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".zshrc"), "source \"" + scripts() + "/jasper.zsh\"\nsource \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("ZDOTDIR", home.toString());
        String twice = ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n");
        assertThat(twice.split(java.util.regex.Pattern.quote(ShellRun.C), -1).length - 1).isEqualTo(1);
        env.put("JASPER_INTEGRATION_LOADED", "1");
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.C);
        env.remove("JASPER_INTEGRATION_LOADED");
        env.put("TERM_PROGRAM", "iTerm.app");
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.A);
    }

    @Test void bashEmitsMarksDirectoryAndTheExactCommandLine() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path home = Files.createDirectories(dir.resolve("home with space"));
        Path work = Files.createDirectories(home.resolve("my dir"));
        Files.writeString(home.resolve(".bashrc"), "export RC_RAN=yes\nPS1='rc$ '\nPROMPT_COMMAND='export PC_RAN=yes'\ntrap 'export TRAP_RAN=yes' DEBUG\nsource \"" + scripts() + "/jasper.bash\"\n");
        var env = environment(home);
        String output = ShellRun.run(List.of("/bin/bash", "-i"), env, work, "echo $RC_RAN $PC_RAN $TRAP_RAN\ncd ..\nfalse\necho \"one\ntwo\"\n\nexit\n");
        String host = ShellRun.hostname();
        int at = indexAfter(output, ShellRun.CWD(host, work.toString().replace(" ", "%20")), 0);
        at = indexAfter(output, ShellRun.A, at);
        at = indexAfter(output, "rc$ ", at);
        at = indexAfter(output, ShellRun.B, at);
        at = indexAfter(output, ShellRun.CMD("echo $RC_RAN $PC_RAN $TRAP_RAN"), at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, "yes yes yes", at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CMD("cd .."), at);
        at = indexAfter(output, ShellRun.D(0), at);
        at = indexAfter(output, ShellRun.CWD(host, home.toString().replace(" ", "%20")), at);
        at = indexAfter(output, ShellRun.CMD("false"), at);
        at = indexAfter(output, ShellRun.D(1), at);
        at = indexAfter(output, "\033]1341;jasper;cmd;", at);
        at = indexAfter(output, ShellRun.C, at);
        at = indexAfter(output, ShellRun.D(0), at);
        assertThat(output.split(java.util.regex.Pattern.quote(ShellRun.A), -1).length - 1).isEqualTo(6);
        assertThat(output).doesNotContain("> \033]133;C");
    }

    @Test void bashKeepsAPromptCommandArrayAndIgnoresSpaceHiddenHistoryOnBash5() throws Exception {
        Path bash5 = Path.of("/opt/homebrew/bin/bash");
        Assumptions.assumeTrue(Files.isExecutable(bash5));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".bashrc"), "PS1='$ '\nPROMPT_COMMAND=('export PC_ONE=1' 'export PC_TWO=2')\nHISTCONTROL=ignorespace\nsource \"" + scripts() + "/jasper.bash\"\n");
        String output = ShellRun.run(List.of(bash5.toString(), "-i"), environment(home), home, "echo $PC_ONE$PC_TWO\n echo hidden\nexit\n");
        int at = indexAfter(output, ShellRun.CMD("echo $PC_ONE$PC_TWO"), 0);
        at = indexAfter(output, "12", at);
        at = indexAfter(output, ShellRun.CMD("echo hidden"), at);
        indexAfter(output, ShellRun.D(0), at);
    }
}
```

The bash multi-line case only asserts a `cmd` line exists (bash's history may join the two lines with a semicolon or keep the newline depending on `cmdhist`/`lithist`); the zsh case asserts the exact text.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellIntegrationScriptTest'`
Expected: the tests run against the real shells. Any failure is a script defect: fix the script resource (not the assertion) until every expected sequence appears in order. Typical fixes: `printf` code-point arithmetic for the percent-encoder, the history-number extraction, `PROMPT` wrapping on the first prompt (the first `precmd` runs before the first prompt, so the very first prompt must already carry A and B), and `set -o` state in `zsh -i` on a pipe.

- [ ] **Step 3: Run the whole module**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add jasper-app/src/main/resources/dev/jasper/app/shell-integration jasper-app/src/test/java/dev/jasper/app/ShellRun.java jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java
git commit -m "test: prove the integration scripts against real zsh and bash

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The `terminal.shell_integration` setting

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/ShellIntegrationMode.java`
- Modify: `TerminalConfig.java`, `ConfigLoader.java`, `ConfigTemplate.java`, `config.example.toml`, `docs/configuration.md`
- Modify tests: `ConfigLoaderTest.java`, `ConfigTemplateTest.java`

**Interfaces:**
- Produces: `enum ShellIntegrationMode { AUTO, MANUAL, OFF }`; `TerminalConfig` gains an eleventh component `ShellIntegrationMode shellIntegration` (the existing ten-argument constructor delegates with `AUTO`; `defaults()` is `AUTO`); `terminal.shell_integration` parsed with the `choice` helper (`"auto"`, `"manual"`, `"off"`), new panes only.

- [ ] **Step 1: Write the failing tests**

Add to `ConfigLoaderTest`:

```java
    @Test void shellIntegrationParsesItsThreeChoicesAndRejectsOthers() {
        assertThat(parse("[terminal]\nshell_integration = \"manual\"\n").snapshot().terminal().shellIntegration())
            .isEqualTo(ShellIntegrationMode.MANUAL);
        assertThat(parse("[terminal]\nshell_integration = \"off\"\n").snapshot().terminal().shellIntegration())
            .isEqualTo(ShellIntegrationMode.OFF);
        assertThat(ConfigSnapshot.defaults().terminal().shellIntegration()).isEqualTo(ShellIntegrationMode.AUTO);
        var bad = parse("[terminal]\nshell_integration = \"sometimes\"\n");
        assertThat(bad.snapshot().terminal().shellIntegration()).isEqualTo(ShellIntegrationMode.AUTO);
        assertDiagnostic(bad, "terminal.shell_integration", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var wrongType = parse("[terminal]\nshell_integration = true\n");
        assertThat(wrongType.rejected()).isTrue();
    }
```

In `ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`, add `"shell_integration"` to the expected `terminal` key set.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest'`
Expected: compilation failure (`ShellIntegrationMode`, `shellIntegration()` missing).

- [ ] **Step 3: Add the setting**

`ShellIntegrationMode.java`:

```java
package dev.jasper.app;

/** How Jasper's shell integration script reaches a new shell. */
enum ShellIntegrationMode { AUTO, MANUAL, OFF }
```

`TerminalConfig`: append `ShellIntegrationMode shellIntegration` to the record header, `Objects.requireNonNull(shellIntegration, "shellIntegration")` in the compact constructor, and change the existing ten-argument constructor (the one ending in `ShellExitBehavior onExit`) to delegate `this(..., onExit, ShellIntegrationMode.AUTO)`; keep the nine-argument one delegating as before.

`ConfigLoader`: add `"shell_integration"` to the `terminal` field set; field `private ShellIntegrationMode shellIntegration = ShellIntegrationMode.AUTO;`; in `readField`:

```java
            case "terminal.shell_integration" -> shellIntegration = choice(path, value, Map.of(
                "auto", ShellIntegrationMode.AUTO, "manual", ShellIntegrationMode.MANUAL, "off", ShellIntegrationMode.OFF),
                shellIntegration);
```

and pass `shellIntegration` as the last `TerminalConfig` argument in `parse`.

`ConfigTemplate`, after the `# on_exit = "keep_open"` line:

```
            # Shell integration: "auto" loads Jasper's zsh/bash/fish script after your own startup files (new panes only),
            # "manual" only exports JASPER_SHELL_INTEGRATION so you can source it yourself, "off" does neither.
            # shell_integration = "auto"
```

`config.example.toml`, after `on_exit = "keep_open"`:

```toml
# New panes only. "auto" loads Jasper's shell integration script (zsh, bash, fish) after your startup files;
# "manual" only exports JASPER_SHELL_INTEGRATION for you to source; "off" does neither.
shell_integration = "auto"
```

`docs/configuration.md`: add the row `| `terminal.shell_integration` | `"auto"` | `"auto"`, `"manual"`, `"off"` | New pane requests |` after `terminal.on_exit`, and the setting to the TOML block near the top; the full "Shell integration" section is written in Task 6.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest' --tests 'dev.jasper.app.ExpandedConfigTest'` then `./gradlew check`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app config.example.toml docs/configuration.md
git commit -m "feat: add the terminal.shell_integration setting

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Injection at launch

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/LaunchSettings.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/LaunchSettingsTest.java`, `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationLaunchTest.java`

**Interfaces:**
- Produces: `LaunchSettings.resolve(ConfigSnapshot, String osName, Map<String,String> inherited, int columns, int lines, Path integrationDir)` (the five-argument form delegates with `null`, meaning no exports and no injection); `static void inject(List<String> command, Map<String,String> environment, Path dir)` (package-private, `auto` behaviour for one command).

- [ ] **Step 1: Write the failing tests**

Add to `LaunchSettingsTest` (imports `java.nio.file.Path`, `java.util.List`, `java.util.HashMap`):

```java
    private static ConfigSnapshot withShell(String program, List<String> args, ShellIntegrationMode mode) {
        var d = ConfigSnapshot.defaults();
        var t = d.terminal();
        var terminal = new TerminalConfig(new TerminalConfig.Shell(program, args), t.env(), t.scrollback(), t.optionAsMeta(),
            t.cursorShape(), t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell(), t.onExit(), mode);
        return new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), Map.of(), d.columns(),
            d.lines(), terminal, d.buddyEnabled(), d.historyEnabled(), d.maxResults());
    }

    @Test void termProgramIsAlwaysJasperAndOnlyAutoInjects() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var off = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.OFF), "Mac OS X", Map.of(), 150, 45, dir);
        assertThat(off.environment()).containsEntry("TERM_PROGRAM", "Jasper").doesNotContainKey("JASPER_SHELL_INTEGRATION").doesNotContainKey("ZDOTDIR");
        var manual = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.MANUAL), "Mac OS X", Map.of(), 150, 45, dir);
        assertThat(manual.environment()).containsEntry("JASPER_SHELL_INTEGRATION", dir.toString()).doesNotContainKey("ZDOTDIR");
        assertThat(manual.command()).containsExactly("/bin/zsh", "-l");
        var auto = LaunchSettings.resolve(withShell("/bin/zsh", List.of("-l"), ShellIntegrationMode.AUTO), "Mac OS X",
            Map.of("ZDOTDIR", "/Users/me/dots"), 150, 45, dir);
        assertThat(auto.environment()).containsEntry("ZDOTDIR", dir.resolve("zsh").toString())
            .containsEntry("JASPER_ORIGINAL_ZDOTDIR", "/Users/me/dots").containsEntry("JASPER_SHELL_INTEGRATION", dir.toString());
        assertThat(auto.command()).containsExactly("/bin/zsh", "-l");
        var noDir = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.AUTO), "Mac OS X", Map.of(), 150, 45, null);
        assertThat(noDir.environment()).containsEntry("TERM_PROGRAM", "Jasper").doesNotContainKey("JASPER_SHELL_INTEGRATION");
        assertThat(LaunchSettings.resolve(withShell("/bin/zsh", List.of(), ShellIntegrationMode.AUTO), "Mac OS X", Map.of(), 150, 45).environment())
            .doesNotContainKey("ZDOTDIR");
    }

    @Test void injectHandlesEachShellAndLeavesOthersAlone() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var zsh = new java.util.ArrayList<>(List.of("/usr/local/bin/zsh")); var zshEnv = new HashMap<String, String>();
        LaunchSettings.inject(zsh, zshEnv, dir);
        assertThat(zshEnv).containsEntry("ZDOTDIR", dir.resolve("zsh").toString()).doesNotContainKey("JASPER_ORIGINAL_ZDOTDIR");
        var bash = new java.util.ArrayList<>(List.of("/bin/bash", "-l", "--noprofile", "--login")); var bashEnv = new HashMap<String, String>();
        LaunchSettings.inject(bash, bashEnv, dir);
        assertThat(bash).containsExactly("/bin/bash", "--rcfile", dir.resolve("bash/rc.bash").toString(), "--noprofile");
        assertThat(bashEnv).containsEntry("JASPER_LOGIN_SHELL", "1");
        var bashPlain = new java.util.ArrayList<>(List.of("bash")); var plainEnv = new HashMap<String, String>();
        LaunchSettings.inject(bashPlain, plainEnv, dir);
        assertThat(bashPlain).containsExactly("bash", "--rcfile", dir.resolve("bash/rc.bash").toString());
        assertThat(plainEnv).doesNotContainKey("JASPER_LOGIN_SHELL");
        var fish = new java.util.ArrayList<>(List.of("/opt/homebrew/bin/fish")); var fishEnv = new HashMap<String, String>(Map.of("XDG_DATA_DIRS", "/x:/y"));
        LaunchSettings.inject(fish, fishEnv, dir);
        assertThat(fishEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/x:/y");
        var fishDefault = new java.util.ArrayList<>(List.of("fish")); var fishDefaultEnv = new HashMap<String, String>();
        LaunchSettings.inject(fishDefault, fishDefaultEnv, dir);
        assertThat(fishDefaultEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/usr/local/share:/usr/share");
        var other = new java.util.ArrayList<>(List.of("/bin/sh", "-l")); var otherEnv = new HashMap<String, String>();
        LaunchSettings.inject(other, otherEnv, dir);
        assertThat(other).containsExactly("/bin/sh", "-l");
        assertThat(otherEnv).isEmpty();
    }

    @Test void userEnvOverlayCanStillOverrideTermProgram() {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        var terminal = new TerminalConfig(t.shell(), Map.of("TERM_PROGRAM", "Other"), t.scrollback(), t.optionAsMeta(), t.cursorShape(),
            t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell(), t.onExit(), ShellIntegrationMode.AUTO);
        var snapshot = new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), Map.of(), d.columns(),
            d.lines(), terminal, d.buddyEnabled(), d.historyEnabled(), d.maxResults());
        assertThat(LaunchSettings.resolve(snapshot, "Linux", Map.of(), 150, 45, Path.of("/tmp/si")).environment())
            .containsEntry("TERM_PROGRAM", "Other");
    }
```

`ShellIntegrationLaunchTest.java` (real shells through the wrappers):

```java
package dev.jasper.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class ShellIntegrationLaunchTest {
    @TempDir Path dir;

    private Map<String, String> base(Path home) {
        var env = new HashMap<String, String>();
        env.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        env.put("HOME", home.toString());
        env.put("TERM", "xterm-256color");
        env.put("TERM_PROGRAM", "Jasper");
        env.put("LANG", "en_US.UTF-8");
        return env;
    }

    @Test void zshWrappersLoadTheUsersFilesThenTheIntegrationAndRestoreZdotdir() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path scripts = ShellIntegrationScripts.install(dir.resolve("si"));
        Path dots = Files.createDirectories(dir.resolve("dots"));
        Files.writeString(dots.resolve(".zshenv"), "export ENV_RAN=yes\n");
        Files.writeString(dots.resolve(".zprofile"), "export PROFILE_RAN=yes\n");
        Files.writeString(dots.resolve(".zshrc"), "export RC_RAN=yes\nPROMPT='rc%% '\n");
        Files.writeString(dots.resolve(".zlogin"), "export LOGIN_RAN=yes\n");
        var env = base(dir);
        env.put("ZDOTDIR", dots.toString());
        env.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        var command = new ArrayList<>(List.of("/bin/zsh", "-l", "-i"));
        LaunchSettings.inject(command, env, scripts);
        String output = ShellRun.run(command, env, dir, "echo $ENV_RAN $PROFILE_RAN $RC_RAN $LOGIN_RAN $ZDOTDIR\nexit\n");
        assertThat(output).contains("yes yes yes yes " + dots);
        assertThat(output).contains(ShellRun.A).contains("rc% ").contains(ShellRun.B).contains(ShellRun.CMD("echo $ENV_RAN $PROFILE_RAN $RC_RAN $LOGIN_RAN $ZDOTDIR")).contains(ShellRun.D(0));
        var plain = new ArrayList<>(List.of("/bin/zsh", "-i"));
        var plainEnv = base(dir);
        plainEnv.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        Files.writeString(dir.resolve(".zshrc"), "export RC_RAN=home\n");
        LaunchSettings.inject(plain, plainEnv, scripts);
        String homeOutput = ShellRun.run(plain, plainEnv, dir, "echo $RC_RAN ${ZDOTDIR:-unset} $JASPER_INTEGRATION_LOADED\nexit\n");
        assertThat(homeOutput).contains("home unset 1").contains(ShellRun.C);
    }

    @Test void bashRcfileLoadsBashrcOrTheLoginFilesThenTheIntegration() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        Path scripts = ShellIntegrationScripts.install(dir.resolve("si"));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.writeString(home.resolve(".bashrc"), "export RC_RAN=yes\nPS1='rc$ '\n");
        Files.writeString(home.resolve(".bash_profile"), "export PROFILE_RAN=yes\nsource ~/.bashrc\n");
        var env = base(home);
        env.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        var interactive = new ArrayList<>(List.of("/bin/bash", "-i"));
        LaunchSettings.inject(interactive, env, scripts);
        String output = ShellRun.run(interactive, env, home, "echo ${RC_RAN}-${PROFILE_RAN:-no}\nexit\n");
        assertThat(output).contains("yes-no").contains(ShellRun.A).contains("rc$ ").contains(ShellRun.B).contains(ShellRun.C).contains(ShellRun.D(0));
        var login = new ArrayList<>(List.of("/bin/bash", "-l", "-i"));
        var loginEnv = base(home);
        loginEnv.put("JASPER_SHELL_INTEGRATION", scripts.toString());
        LaunchSettings.inject(login, loginEnv, scripts);
        assertThat(login).doesNotContain("-l");
        String loginOutput = ShellRun.run(login, loginEnv, home, "echo ${RC_RAN}-${PROFILE_RAN} ${JASPER_LOGIN_SHELL:-unset}\nexit\n");
        assertThat(loginOutput).contains("yes-yes unset").contains(ShellRun.C);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.LaunchSettingsTest' --tests 'dev.jasper.app.ShellIntegrationLaunchTest'`
Expected: compilation failure (`resolve` six-argument form and `inject` missing).

- [ ] **Step 3: Implement injection**

In `LaunchSettings`:

```java
    static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines) {
        return resolve(snapshot, osName, inherited, windowColumns, windowLines, null);
    }

    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig terminal = snapshot.terminal();
        var command = new ArrayList<>(terminal.shell().program().isEmpty()
            ? DefaultShell.command(osName, inherited) : List.of(terminal.shell().program()));
        command.addAll(terminal.shell().args());
        var environment = new HashMap<>(inherited);
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_"));
        environment.put("TERM_PROGRAM", "Jasper");
        environment.putAll(terminal.env());
        if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
                && environment.getOrDefault("LANG", "").isBlank()) {
            environment.put("LANG", "en_US.UTF-8");
        }
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        if (integrationDir != null && terminal.shellIntegration() != ShellIntegrationMode.OFF) {
            environment.put("JASPER_SHELL_INTEGRATION", integrationDir.toString());
            if (terminal.shellIntegration() == ShellIntegrationMode.AUTO) inject(command, environment, integrationDir);
        }
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback());
    }

    /** Auto mode for one shell: zsh through ZDOTDIR wrappers, bash through --rcfile, fish through XDG_DATA_DIRS. */
    static void inject(List<String> command, Map<String, String> environment, Path dir) {
        String shell;
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            shell = name == null ? "" : name.toString();
        } catch (InvalidPathException failure) {
            return;
        }
        switch (shell) {
            case "zsh" -> {
                String original = environment.get("ZDOTDIR");
                if (original != null && !original.isBlank()) environment.put("JASPER_ORIGINAL_ZDOTDIR", original);
                else environment.remove("JASPER_ORIGINAL_ZDOTDIR");
                environment.put("ZDOTDIR", dir.resolve("zsh").toString());
            }
            case "bash" -> {
                boolean login = false;
                for (int i = command.size() - 1; i >= 1; i--) {
                    if (command.get(i).equals("-l") || command.get(i).equals("--login")) { command.remove(i); login = true; }
                }
                command.add(1, "--rcfile");
                command.add(2, dir.resolve("bash/rc.bash").toString());
                if (login) environment.put("JASPER_LOGIN_SHELL", "1");
            }
            case "fish" -> {
                String existing = environment.get("XDG_DATA_DIRS");
                String rest = existing == null || existing.isBlank() ? "/usr/local/share:/usr/share" : existing;
                environment.put("XDG_DATA_DIRS", dir.resolve("fish") + ":" + rest);
            }
            default -> {
                // Only the exported variables reach other programs.
            }
        }
    }
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.LaunchSettingsTest' --tests 'dev.jasper.app.ShellIntegrationLaunchTest'`. Failures in the real-shell test are wrapper-file defects: fix the resources under `shell-integration/zsh` or `bash`, not the assertions. Then `./gradlew check`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app
git commit -m "feat: inject the shell integration scripts at launch

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The `cmd` payload, the detected flag, and the status dot

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/TerminalSession.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/TerminalPane.java`, `WindowContent.java`, `WindowStatusBar.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/ShellIntegrationSessionTest.java`, `jasper-app/src/test/java/dev/jasper/app/MockUiTest.java` (status assertions)

**Interfaces:**
- Produces: `TerminalSession.shellIntegrationDetected()` (public, volatile, true after the first A mark); the `cmd` custom command (`OSC 1341;jasper;cmd;<base64>`) whose decoded UTF-8 text is used for the cycle's command instead of the screen read; `TerminalPane.shellIntegrationDetected()`; `WindowStatusBar.setMetadata(String shell, String directory, String dimensions, boolean running, boolean integration)` (the four-argument form delegates with `false`).

- [ ] **Step 1: Write the failing tests**

Add to `ShellIntegrationSessionTest` (reuse its `listenForCommands()` helper and `captured`/`statuses` fields):

```java
    @Test void theCmdPayloadIsPreferredOverTheScreenAndMalformedPayloadsAreIgnored() throws Exception {
        listenForCommands();
        String encoded = java.util.Base64.getEncoder().encodeToString("echo \"one\ntwo\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ \033]133;B\007echo \"one\r\ndquote> two\"\r\n\033]1341;jasper;cmd;" + encoded
            + "\007\033]133;C\007one\r\ntwo\r\n\033]133;D;0\007\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "captured through cmd");
        assertThat(captured).containsExactly("echo \"one\ntwo\"");
        assertThat(session.shellIntegrationDetected()).isTrue();
        connector.feed("\033]133;B\007ls\r\n\033]1341;jasper;cmd;***not base64***\007\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 2, "fell back to the screen");
        assertThat(captured.get(1)).isEqualTo("ls");
    }

    @Test void aCmdPayloadWithoutABMarkStillCapturesTheCommand() throws Exception {
        listenForCommands();
        assertThat(session.shellIntegrationDetected()).isFalse();
        String encoded = java.util.Base64.getEncoder().encodeToString("pwd".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ pwd\r\n\033]1341;jasper;cmd;" + encoded + "\007\033]133;C\007/tmp\r\n\033]133;D;0\007");
        Await.until(() -> captured.size() == 1, "captured from cmd alone");
        assertThat(captured).containsExactly("pwd");
    }
```

In `MockUiTest`, next to the existing `status.setMetadata("bash", "/tmp", "91 × 35", true)` render assertions, add a case with the five-argument form and assert `status.getText()` contains `"bash \u25cf"` for `integration = true` and `"bash \u25cb"` for `false`, and that the four-argument form yields the hollow dot.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.ShellIntegrationSessionTest' :jasper-app:test --tests 'dev.jasper.app.MockUiTest'`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`TerminalSession`:
- fields `private volatile boolean shellIntegrationDetected; private String pendingCommandText;` (reader thread only for the latter).
- `public boolean shellIntegrationDetected() { return shellIntegrationDetected; }`
- in `onCustomCommand`: new case before `"mark"`:

```java
            case "cmd" -> pendingCommandText = args.size() > 2 ? decodeCommand(args.get(2)) : null;
```

with

```java
    private static String decodeCommand(String encoded) {
        try {
            String text = new String(java.util.Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? null : text;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
```

- in the `"A"` case also `pendingCommandText = null;` and set `shellIntegrationDetected = true` in `recordPrompt()`.
- `captureCommand()`:

```java
    private void captureCommand() {
        String reported = pendingCommandText;
        pendingCommandText = null;
        buffer.lock();
        try {
            String text = reported;
            if (text == null) {
                if (commandStartRow < 0) return;
                long endRow = absoluteRow(terminal.getCursorY() - 1);
                if (terminal.getCursorX() - 1 == 0) endRow--; // Enter moved the cursor to a fresh line
                text = CommandCapture.text(commandStartRow, commandStartColumn, endRow, buffer.getWidth(), this::lineAtLocked);
            }
            commandStartRow = -1;
            pendingCommand = text.isEmpty() ? null : text;
        } finally {
            buffer.unlock();
        }
    }
```

`TerminalPane`: `boolean shellIntegrationDetected() { return session != null && session.shellIntegrationDetected(); }`.

`WindowContent.update` (line with `chrome.status().setMetadata(...)`): pass `pane != null && pane.shellIntegrationDetected()` as the fifth argument.

`WindowStatusBar`: add `private boolean integration;` and

```java
    void setMetadata(String shell, String directory, String dimensions, boolean running) {
        setMetadata(shell, directory, dimensions, running, false);
    }

    void setMetadata(String shell, String directory, String dimensions, boolean running, boolean integration) {
        this.running = running; this.shell = shell; this.directory = directory; this.dimensions = dimensions;
        this.integration = integration;
        String shown = shell.isEmpty() ? "" : shell + (integration ? " \u25cf" : " \u25cb");
        left.setParts(shown, directory);
        left.setToolTipText(directory);
        left.setFirstToolTip(shell.isEmpty() ? null : integration ? "Shell integration active" : "Shell integration not detected");
        updateText();
    }
```

with `updateText` using `shown`'s text via the `shell` field plus the dot: `text = shell.isEmpty() ? configText : shell + (integration ? " \u25cf" : " \u25cb") + "  |  " + directory + …`. Add `void setFirstToolTip(String tip) { first.setToolTipText(tip); }` to `Segment`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew check`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-terminal/src jasper-app/src
git commit -m "feat: accept the exact command line from shell integration and show its status

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Startup extraction, launch wiring and documentation

**Files:**
- Modify: `Main.java`, `JasperApplication.java`, `docs/configuration.md`, `docs/command-palette.md`, `docs/STATUS.md`, spec and plan banners
- Test: `jasper-app/src/test/java/dev/jasper/app/ConfigurationControllerTest.java` (one `windowLauncher` call passes a directory)

**Interfaces:**
- Produces: `JasperApplication.windowLauncher(Executor, Supplier<ConfigSnapshot>, BiFunction<Path, LaunchSettings, TerminalSession>, Path integrationDir)` (the three-argument form delegates with `null`); `JasperApplication(service, launcher, history, buddyStateFile, terminate, shellHistory, snippets, Path shellIntegrationDir)` (the seven-argument form delegates with `null`); `Main` installs the scripts before creating the application.

- [ ] **Step 1: Write the failing test**

In `ConfigurationControllerTest`, add a test that builds `JasperApplication.windowLauncher(pending::add, controller::snapshot, (path, settings) -> { captured.set(settings); return DesktopTestSupport.shell(path); }, Path.of("/opt/si"))`, launches once, and asserts the captured settings' environment contains `JASPER_SHELL_INTEGRATION=/opt/si` and `TERM_PROGRAM=Jasper` (follow the shape of the existing `windowLauncher` tests at lines ~411 and ~434).

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ConfigurationControllerTest'`
Expected: compilation failure.

- [ ] **Step 3: Wire it**

`JasperApplication`: field `private final Path shellIntegrationDir;`; eight-argument constructor storing it (seven-argument delegates `null`); `windowLauncher` gains the `Path integrationDir` parameter and passes it to `LaunchSettings.resolve(..., integrationDir)`; `newWindow` passes `shellIntegrationDir`.

`Main`, before `new JasperApplication(...)`:

```java
                    Path integrationDir = null;
                    try {
                        integrationDir = ShellIntegrationScripts.install(dirs.shellIntegration());
                    } catch (java.io.IOException failure) {
                        LOG.log(System.Logger.Level.WARNING, "Shell integration scripts could not be installed; integration is off", failure);
                    }
```

and pass `integrationDir` as the eighth argument.

- [ ] **Step 4: Documentation**

`docs/configuration.md`: a `### Shell integration` section after "Shell exit behavior": what the scripts emit (directory, prompt marks, exit status, exact command line) and what Jasper does with them (status-bar directory, prompt jumping, History with directory and status); the three modes; the mechanisms per shell (zsh `ZDOTDIR` wrappers that restore your `ZDOTDIR`, bash `--rcfile` with `-l` emulated so `shopt -q login_shell` is false and `logout` is unavailable, fish `XDG_DATA_DIRS` vendor snippet); the manual `source` line for each shell; `TERM_PROGRAM=Jasper` and that a `[terminal.env]` override wins; nested shells get no marks unless they source the script; the status-bar dot and tooltip for troubleshooting; where the files live (`shell-integration` under the app directory, rewritten on upgrade).

`docs/command-palette.md`: in the Shell history section replace "Jasper ships no shell-integration script of its own; wiring one up is a separate future feature, and it remains the main limit on live capture today." with a sentence that Jasper's own scripts emit the marks and the exact command line, linking to the configuration section.

`docs/STATUS.md`: a dated entry at the top: branch, what landed, the extraction-on-main-thread deviation, check counts from the XML, user-run items (each shell on the desktop with real dotfiles and prompt theme; fish if installed), "no GUI, merge or push". Remove the "known limitation" wording about shipped scripts from the snippets/scopes entries only where it states no script exists.

Spec banner: **Status** → implemented with the commit range. This plan's banner: complete with the final counts.

- [ ] **Step 5: Verify**

Run: `./gradlew check --rerun-tasks`, the source-hygiene snippet from `AGENTS.md` over both modules, `git diff --check`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app docs
git commit -m "feat: install shell integration at startup and document it

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Self-review notes

- Spec coverage: scripts and wrappers (T1), real-shell behaviour (T2), setting (T3), injection and wrapper behaviour (T4), `cmd` payload, detected flag and status dot (T5), extraction at startup, launcher wiring and docs (T6).
- Type consistency: `ShellIntegrationScripts.install(Path) -> Path`, `ShellIntegrationMode`, `TerminalConfig` eleven components, `LaunchSettings.resolve(..., Path)`, `LaunchSettings.inject(List, Map, Path)`, `ShellRun.run/A/B/C/D/CMD/CWD`, `TerminalSession.shellIntegrationDetected()`, `WindowStatusBar.setMetadata(…, boolean integration)`, `windowLauncher(…, Path)` are used with the same shapes throughout.
- Real-shell tests are the net for script defects; the plan instructs fixing resources, never assertions.
