# Cygwin login initialization on the owned POSIX PTY, followed by an explicit requested directory.
if [[ -r /etc/profile ]]; then source /etc/profile; fi
for __jasper_profile in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
    if [[ -r "$__jasper_profile" ]]; then source "$__jasper_profile"; break; fi
done
unset __jasper_profile
if [[ -n "${JASPER_CYGWIN_CWD-}" ]]; then
    builtin cd -- "$JASPER_CYGWIN_CWD" || { printf 'Jasper: requested directory is unavailable\n' >&2; exit 1; }
    unset JASPER_CYGWIN_CWD
else
    builtin cd -- "$HOME" || exit 1
fi
if [[ "${JASPER_CYGWIN_MODE-auto}" == auto ]]; then
    source "$JASPER_SHELL_INTEGRATION/jasper.bash"
fi
