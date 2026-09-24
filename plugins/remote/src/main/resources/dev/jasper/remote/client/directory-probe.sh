# Jasper Remote directory probe. Fixed and read-only; Remote runs it as `sh -s` on an exec channel of
# a pane's dedicated SSH connection. It prints the working directory of the foreground process of that
# connection's only terminal shell, followed by NUL, and prints nothing when it cannot tell.
# Exit 0, or 3 on a system that is neither Linux nor macOS. JASPER_PROBE_ANCESTOR (tests) names the
# ancestor process instead of sshd.
LC_ALL=C
export LC_ALL
case $(uname -s) in
Linux)
    fields() { s=$(cat "/proc/$1/stat" 2>/dev/null) || return 1; printf '%s\n' "${s##*) }"; }
    parent_of() { f=$(fields "$1") || return 1; set -- $f; printf '%s\n' "$2"; }
    name_of() { cat "/proc/$1/comm" 2>/dev/null; }
    children_of() {
        if [ -r "/proc/$1/task/$1/children" ]; then cat "/proc/$1/task/$1/children"; return; fi
        for entry in /proc/[0-9]*; do
            [ "$(parent_of "${entry#/proc/}")" = "$1" ] && printf '%s\n' "${entry#/proc/}"
        done
    }
    has_tty() { f=$(fields "$1") || return 1; set -- $f; [ "$5" != 0 ]; }
    tpgid_of() { f=$(fields "$1") || return 1; set -- $f; printf '%s\n' "$6"; }
    cwd_of() { readlink "/proc/$1/cwd" 2>/dev/null; }
    ;;
Darwin)
    parent_of() { ps -o ppid= -p "$1" 2>/dev/null | tr -d ' '; }
    name_of() { n=$(ps -o comm= -p "$1" 2>/dev/null) || return 1; printf '%s\n' "${n##*/}"; }
    children_of() { pgrep -P "$1" 2>/dev/null; }
    has_tty() { t=$(ps -o tty= -p "$1" 2>/dev/null | tr -d ' '); [ -n "$t" ] && [ "$t" != "??" ]; }
    tpgid_of() { ps -o tpgid= -p "$1" 2>/dev/null | tr -d ' '; }
    cwd_of() { lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p'; }
    ;;
*)
    exit 3
    ;;
esac

ancestor=${JASPER_PROBE_ANCESTOR:-}
if [ -z "$ancestor" ]; then
    pid=$$
    while [ -n "$pid" ] && [ "$pid" -gt 1 ]; do
        case $(name_of "$pid") in
        sshd|sshd-session|dropbear) ancestor=$pid; break ;;
        esac
        pid=$(parent_of "$pid")
    done
fi
[ -n "$ancestor" ] || exit 0

shell=
for child in $(children_of "$ancestor"); do
    has_tty "$child" || continue
    [ -z "$shell" ] || exit 0
    shell=$child
done
[ -n "$shell" ] || exit 0

directory=
target=$(tpgid_of "$shell")
case $target in
''|0|-*) ;;
*) directory=$(cwd_of "$target") ;;
esac
[ -n "$directory" ] || directory=$(cwd_of "$shell")
case $directory in
/*) printf '%s\0' "$directory" ;;
esac
exit 0
