# Jasper wrapper: loads your startup files the way bash would, then Jasper's integration.
if [[ -n "$JASPER_LOGIN_SHELL" ]]; then
    unset JASPER_LOGIN_SHELL
    [[ -r /etc/profile ]] && source /etc/profile
    for __jasper_profile in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
        if [[ -r "$__jasper_profile" ]]; then source "$__jasper_profile"; break; fi
    done
    unset __jasper_profile
else
    # One system file, not both: Debian ships /etc/bash.bashrc, macOS and Fedora /etc/bashrc.
    if [[ -r /etc/bash.bashrc ]]; then source /etc/bash.bashrc
    elif [[ -r /etc/bashrc ]]; then source /etc/bashrc
    fi
    [[ -r "$HOME/.bashrc" ]] && source "$HOME/.bashrc"
fi
source "${JASPER_SHELL_INTEGRATION:-$(dirname "${BASH_SOURCE[0]}")/..}/jasper.bash"
