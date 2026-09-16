# Jasper shell integration for zsh. Jasper loads this automatically when terminal.shell_integration = "auto";
# otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.zsh"` to your .zshrc.
[[ -o interactive ]] || return 0
[[ "${TERM_PROGRAM-}" == "Jasper" ]] || return 0
[[ -n "${JASPER_INTEGRATION_LOADED-}" ]] && return 0
export JASPER_INTEGRATION_LOADED=1

autoload -Uz add-zsh-hook

__jasper_osc() { builtin printf '\033]%s\007' "$1"; }

# Percent-encodes a path byte by byte, keeping unreserved characters and slashes.
__jasper_encode() {
    emulate -L zsh
    setopt no_multibyte
    local input="$1" out="" i c
    for (( i = 1; i <= ${#input}; i++ )); do
        c="${input[i]}"
        case "$c" in
            [A-Za-z0-9/._~-]) out+="$c" ;;
            *) out+="$(builtin printf '%%%02X' $(( #c & 255 )))" ;;
        esac
    done
    builtin printf '%s' "$out"
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
    if [[ "$PROMPT" != *"$__jasper_mark_a"* && "${(t)PROMPT}" != *readonly* ]]; then
        PROMPT="%{$__jasper_mark_a%}$PROMPT%{$__jasper_mark_b%}"
    fi
}

__jasper_preexec() {
    __jasper_command_ran=1
    local encoded
    if encoded="$(builtin printf '%s' "$1" | command base64 2>/dev/null | command tr -d '\n')" && [[ -n "$encoded" ]]; then
        __jasper_osc "1341;jasper;cmd;$encoded"
    fi
    __jasper_osc "133;C"
}

add-zsh-hook precmd __jasper_precmd
add-zsh-hook preexec __jasper_preexec
