# Jasper wrapper: restores your ZDOTDIR, loads your .zshenv, then hands the next startup file back to Jasper.
__jasper_zdotdir="$ZDOTDIR"
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zshenv" ]] && source "${ZDOTDIR:-$HOME}/.zshenv"
# Only an interactive or login shell reads more startup files; anything else must be left with
# the user's own ZDOTDIR, not Jasper's wrapper directory.
if [[ -o interactive || -o login ]]; then
    if [[ -n "$ZDOTDIR" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
    export ZDOTDIR="$__jasper_zdotdir"
else
    unset JASPER_ORIGINAL_ZDOTDIR
fi
unset __jasper_zdotdir
