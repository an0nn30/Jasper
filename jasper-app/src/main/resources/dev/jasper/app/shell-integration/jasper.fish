# Jasper shell integration for fish 3.0 and newer. Jasper loads this automatically when
# terminal.shell_integration = "auto"; otherwise add `source "$JASPER_SHELL_INTEGRATION/jasper.fish"` to config.fish.
if status is-interactive; and test "$TERM_PROGRAM" = Jasper; and not set -q JASPER_INTEGRATION_LOADED
    set -gx JASPER_INTEGRATION_LOADED 1

    function __jasper_osc
        printf '\033]%s\007' $argv[1]
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
    end

    function __jasper_preexec --on-event fish_preexec
        set -l encoded (printf '%s' $argv[1] | base64 2>/dev/null | string join '')
        if test -n "$encoded"
            __jasper_osc "1341;jasper;cmd;$encoded"
        end
        __jasper_osc "133;C"
    end

    if functions -q fish_prompt
        functions -c fish_prompt __jasper_original_prompt
    else
        function __jasper_original_prompt
            printf '%s> ' (prompt_pwd)
        end
    end
    function fish_prompt
        __jasper_original_prompt
        __jasper_osc "133;B"
    end
end
