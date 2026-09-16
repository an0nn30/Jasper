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
    if [[ "$PS1" != *"$__jasper_mark_a"* ]]; then
        PS1="\[$__jasper_mark_a\]$PS1\[$__jasper_mark_b\]"
    fi
    __jasper_prompt_history="$(HISTTIMEFORMAT= builtin history 1 | sed -E '1!d; s/^ *([0-9]+).*/\1/')"
    __jasper_in_prompt=1
}

# Fires before each simple command; the first after a prompt is the user's line. History holds
# it as typed; one kept out of history (HISTCONTROL) keeps the number, so $BASH_COMMAND serves.
__jasper_debug_trap() {
    [[ -n "$__jasper_in_prompt" ]] || return 0
    case "$BASH_COMMAND" in *__jasper_*) return 0 ;; esac
    __jasper_in_prompt="" __jasper_command_ran=1
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

if [[ "$(declare -p PROMPT_COMMAND 2>/dev/null)" == "declare -a"* ]]; then
    PROMPT_COMMAND=(__jasper_capture_status 'eval "$__jasper_install_debug"' "${PROMPT_COMMAND[@]}" __jasper_prompt_command)
else
    __jasper_existing="${PROMPT_COMMAND}"
    while [[ "$__jasper_existing" == *";" || "$__jasper_existing" == *" " ]]; do __jasper_existing="${__jasper_existing%?}"; done
    PROMPT_COMMAND='__jasper_capture_status;eval "$__jasper_install_debug"'
    [[ -n "$__jasper_existing" ]] && PROMPT_COMMAND="$PROMPT_COMMAND;$__jasper_existing"
    PROMPT_COMMAND="$PROMPT_COMMAND;__jasper_prompt_command"
    unset __jasper_existing
fi
