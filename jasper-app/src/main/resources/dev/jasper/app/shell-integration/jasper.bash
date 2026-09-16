# Jasper shell integration for bash 3.2 and newer. Jasper loads this automatically when
# terminal.shell_integration = "auto"; otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.bash"` to your .bashrc.
[[ $- == *i* ]] || return 0
[[ "${TERM_PROGRAM-}" == "Jasper" || "${JASPER_TERMINAL-}" == "1" ]] || return 0
[[ -n "${JASPER_INTEGRATION_LOADED-}" ]] && return 0
export JASPER_INTEGRATION_LOADED=1

__jasper_osc() { builtin printf '\033]%s\007' "$1"; }

# Assigning to a readonly PS1 or PROMPT_COMMAND aborts the function and prints an error every
# prompt. Read only the flag word, so a value that happens to contain "r" is not mistaken for one.
__jasper_writable() {
    local spec
    spec="$(builtin declare -p "$1" 2>/dev/null)"
    spec="${spec#declare }"
    spec="${spec%% *}"
    [[ "$spec" != *r* ]]
}

# Percent-encodes a path byte by byte, keeping unreserved characters and slashes.
__jasper_encode() {
    local LC_ALL=C input="$1" out="" i c code
    for (( i = 0; i < ${#input}; i++ )); do
        c="${input:i:1}"
        case "$c" in
            [A-Za-z0-9/._~-]) out+="$c" ;;
            *) code=$(builtin printf '%d' "'$c"); out+="$(builtin printf '%%%02X' $(( code & 255 )))" ;;
        esac
    done
    builtin printf '%s' "$out"
}

__jasper_mark_a=$'\033]133;A\007' __jasper_mark_b=$'\033]133;B\007'
__jasper_command_ran="" __jasper_last_pwd="" __jasper_last_status=0
__jasper_in_prompt="" __jasper_prompt_history="" __jasper_previous_debug_body=""

# First in PROMPT_COMMAND: keeps the status, and stops the trap mistaking the rest of it for typing.
__jasper_capture_status() { __jasper_last_status=$?; __jasper_in_prompt=""; }

__jasper_prompt_command() {
    if [[ -n "$__jasper_command_ran" ]]; then
        __jasper_osc "133;D;$__jasper_last_status"
        __jasper_command_ran=""
    fi
    if [[ "$PWD" != "$__jasper_last_pwd" ]]; then
        __jasper_last_pwd="$PWD"
        __jasper_osc "7;file://${HOSTNAME:-$(hostname)}$(__jasper_encode "$PWD")"
    fi
    if [[ "$PS1" != *"$__jasper_mark_a"* ]] && __jasper_writable PS1; then
        PS1="\[$__jasper_mark_a\]$PS1\[$__jasper_mark_b\]"
    fi
    __jasper_prompt_history="$(HISTTIMEFORMAT= builtin history 1 | command sed -E '1!d; s/^ *([0-9]+).*/\1/')"
    __jasper_in_prompt=1
}

# Fires before each simple command; the first after a prompt is the user's line. History holds
# it as typed; one kept out of history (HISTCONTROL) keeps the number, so $BASH_COMMAND serves.
__jasper_debug_trap() {
    [[ -n "$__jasper_in_prompt" ]] || return 0
    case "$BASH_COMMAND" in
        __jasper_*|'eval "$__jasper_install_debug"') return 0 ;;
    esac
    __jasper_in_prompt="" __jasper_command_ran=1
    local entry number line encoded
    entry="$(HISTTIMEFORMAT= builtin history 1)"
    number="$(builtin printf '%s\n' "$entry" | command sed -E '1!d; s/^ *([0-9]+).*/\1/')"
    line="$(builtin printf '%s\n' "$entry" | command sed -E '1s/^ *[0-9]+ +//')"
    if [[ -z "$number" || "$number" == "$__jasper_prompt_history" ]]; then
        # History refused the line. A newest entry equal to this command means bash dropped a
        # duplicate (ignoredups), so the text is safe. One that differs — including none at all,
        # when every line so far was hidden — means ignorespace hid it, so Jasper drops the text
        # and C alike; C alone would let the screen read recapture it. History off or HISTSIZE=0
        # keeps nothing either way and so says nothing about privacy. A repeated compound command
        # also suppresses: BASH_COMMAND holds only its first simple command, costing an entry.
        if [[ -o history && "${HISTSIZE-}" != 0 && "$line" != "$BASH_COMMAND" ]]; then
            case ":${HISTCONTROL-}:" in
                *:ignorespace:*|*:ignoreboth:*) return 0 ;;
            esac
        fi
        line="$BASH_COMMAND"
    fi
    if encoded="$(builtin printf '%s' "$line" | command base64 2>/dev/null | command tr -d '\n')" && [[ -n "$encoded" ]]; then
        __jasper_osc "1341;jasper;cmd;$encoded"
    fi
    __jasper_osc "133;C"
    return 0
}

# Unless functrace is set, bash hides the DEBUG trap from a sourced file and restores the old one
# when it returns, so it must be installed from the shell's top level: PROMPT_COMMAND evaluates
# this once at the first prompt, chaining the user's own trap, then blanks it.
__jasper_install_debug='__jasper_install_debug=
__jasper_previous_debug="$(trap -p DEBUG)"
__jasper_previous_debug="${__jasper_previous_debug#trap -- }"
eval "__jasper_previous_debug_body=${__jasper_previous_debug% DEBUG}"
unset __jasper_previous_debug
trap "__jasper_debug_trap; eval \"\$__jasper_previous_debug_body\"" DEBUG'

__jasper_writable PROMPT_COMMAND || return 0

# An array PROMPT_COMMAND is run element by element only from bash 5.1; before that bash runs
# element 0 alone, so an array from an older rc file is flattened back into the string form.
if [[ "$(builtin declare -p PROMPT_COMMAND 2>/dev/null)" == "declare -a"* ]] &&
    (( BASH_VERSINFO[0] > 5 || (BASH_VERSINFO[0] == 5 && BASH_VERSINFO[1] >= 1) )); then
    PROMPT_COMMAND=(__jasper_capture_status 'eval "$__jasper_install_debug"' "${PROMPT_COMMAND[@]}" __jasper_prompt_command)
else
    __jasper_existing="$(IFS=';'; builtin printf '%s' "${PROMPT_COMMAND[*]-}")"
    while [[ "$__jasper_existing" == *";" || "$__jasper_existing" == *" " ]]; do __jasper_existing="${__jasper_existing%?}"; done
    PROMPT_COMMAND='__jasper_capture_status;eval "$__jasper_install_debug"'
    [[ -n "$__jasper_existing" ]] && PROMPT_COMMAND="$PROMPT_COMMAND;$__jasper_existing"
    PROMPT_COMMAND="$PROMPT_COMMAND;__jasper_prompt_command"
    unset __jasper_existing
fi
