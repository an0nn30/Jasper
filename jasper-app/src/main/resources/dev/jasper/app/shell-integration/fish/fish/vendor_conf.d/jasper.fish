# Jasper loader: fish finds this through XDG_DATA_DIRS and it pulls in the real script.
if set -q JASPER_SHELL_INTEGRATION; and test -r "$JASPER_SHELL_INTEGRATION/jasper.fish"
    source "$JASPER_SHELL_INTEGRATION/jasper.fish"
end
