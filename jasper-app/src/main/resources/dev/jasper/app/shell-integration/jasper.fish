# Jasper shell integration for fish 3.1 and newer. Jasper loads this automatically when
# terminal.shell_integration = "auto"; otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.fish"` to config.fish.
# tmux overwrites TERM_PROGRAM in every pane, so JASPER_TERMINAL is the marker that survives it.
if status is-interactive
    and begin; test "$TERM_PROGRAM" = Jasper; or test "$JASPER_TERMINAL" = 1; end
    and not set -q JASPER_INTEGRATION_LOADED
    set -gx JASPER_INTEGRATION_LOADED 1

    # tmux forwards almost no escape out of a pane: measured on 3.5a, not one OSC 133 mark reaches the
    # terminal, so integration is silently dead there. Its passthrough wrapper is the way out, and needs
    # allow-passthrough on - set pane-scoped, leaving a global tmux configuration exactly as written.
    if set -q TMUX
        command tmux set-option -p allow-passthrough on 2>/dev/null
        function __jasper_osc
            command printf '\033Ptmux;\033\033]%s\007\033\\' $argv[1]
        end
    else
        function __jasper_osc
            command printf '\033]%s\007' $argv[1]
        end
    end

    function __jasper_encode
        string escape --style=url -- $argv[1] | string replace -a '%2F' '/'
    end

    set -g __jasper_last_status 0
    set -g __jasper_last_pwd ''

    function __jasper_postexec --on-event fish_postexec
        set -g __jasper_last_status $status
        set -g __jasper_command_ran 1
    end

    function __jasper_precmd --on-event fish_prompt
        if set -q __jasper_command_ran
            __jasper_osc "133;D;$__jasper_last_status"
            set -e __jasper_command_ran
        end
        if test "$PWD" != "$__jasper_last_pwd"
            set -g __jasper_last_pwd $PWD
            __jasper_osc "7;file://$hostname"(__jasper_encode $PWD)
        end
        __jasper_osc "133;A"
        __jasper_wrap_prompt
    end

    function __jasper_preexec --on-event fish_preexec
        set -l encoded (command printf '%s' $argv[1] | command base64 2>/dev/null | string join '')
        if test -n "$encoded"
            __jasper_osc "1341;jasper;cmd;$encoded"
        end
        __jasper_osc "133;C"
    end

    # fish loads vendor_conf.d before config.fish, so a prompt defined there replaces this wrapper.
    # Re-check every prompt, the way the zsh and bash scripts re-check PROMPT and PS1.
    function __jasper_wrap_prompt
        if functions -q fish_prompt
            and not functions fish_prompt | string match -q '*__jasper_osc "133;B"*'
            functions -c fish_prompt __jasper_original_prompt
            function fish_prompt
                __jasper_original_prompt
                __jasper_osc "133;B"
            end
        end
    end
end
