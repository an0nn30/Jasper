# Jasper wrapper: restores your ZDOTDIR, loads your .zprofile, then hands the next startup file back to Jasper.
__jasper_zdotdir="$ZDOTDIR"
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zprofile" ]] && source "${ZDOTDIR:-$HOME}/.zprofile"
if [[ -n "$ZDOTDIR" ]]; then export JASPER_ORIGINAL_ZDOTDIR="$ZDOTDIR"; else unset JASPER_ORIGINAL_ZDOTDIR; fi
export ZDOTDIR="$__jasper_zdotdir"
unset __jasper_zdotdir
