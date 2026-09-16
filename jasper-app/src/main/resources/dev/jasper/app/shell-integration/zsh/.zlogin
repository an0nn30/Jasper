# Jasper wrapper: loads your .zlogin and leaves ZDOTDIR as yours.
if [[ -n "$JASPER_ORIGINAL_ZDOTDIR" ]]; then export ZDOTDIR="$JASPER_ORIGINAL_ZDOTDIR"; else unset ZDOTDIR; fi
[[ -r "${ZDOTDIR:-$HOME}/.zlogin" ]] && source "${ZDOTDIR:-$HOME}/.zlogin"
unset JASPER_ORIGINAL_ZDOTDIR
