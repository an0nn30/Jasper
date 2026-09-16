# Jasper wrapper: restores your ZDOTDIR, loads your .zshenv, then hands the next startup file back to Jasper.
__jasper_zdotdir="${ZDOTDIR-}"
if [[ -n "${JASPER_ORIGINAL_ZDOTDIR-}" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zshenv" ]] && source "${ZDOTDIR:-$HOME}/.zshenv"
# Hand ZDOTDIR back when more startup files follow, and also when nothing has loaded the
# integration yet: tmux runs its default-command as `$SHELL -c ...`, so the interactive shell we
# want is the child of a non-interactive one, and stripping ZDOTDIR here would strand it. Once the
# integration has loaded, a `zsh -c` the user runs is a plain child and keeps their own ZDOTDIR.
if [[ -o interactive || -o login || -z "${JASPER_INTEGRATION_LOADED-}" ]]; then
    if [[ -n "${ZDOTDIR-}" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
    export ZDOTDIR="$__jasper_zdotdir"
else
    unset JASPER_ORIGINAL_ZDOTDIR
fi
unset __jasper_zdotdir
