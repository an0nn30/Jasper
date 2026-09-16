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
