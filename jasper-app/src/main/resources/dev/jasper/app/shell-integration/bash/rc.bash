# Jasper wrapper: loads your startup files the way bash would, then Jasper's integration.
if [[ -n "$JASPER_LOGIN_SHELL" ]]; then
    unset JASPER_LOGIN_SHELL
    [[ -r /etc/profile ]] && source /etc/profile
    for __jasper_profile in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
        if [[ -r "$__jasper_profile" ]]; then source "$__jasper_profile"; break; fi
    done
    unset __jasper_profile
else
    [[ -r /etc/bash.bashrc ]] && source /etc/bash.bashrc
    [[ -r /etc/bashrc ]] && source /etc/bashrc
    [[ -r "$HOME/.bashrc" ]] && source "$HOME/.bashrc"
fi
source "${JASPER_SHELL_INTEGRATION:-$(dirname "${BASH_SOURCE[0]}")/..}/jasper.bash"
