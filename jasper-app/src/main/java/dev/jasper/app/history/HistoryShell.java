package dev.jasper.app.history;

/** Shells whose history files Jasper reads; the label is the tag shown on palette rows. */
enum HistoryShell {
    ZSH("zsh"), BASH("bash"), FISH("fish"), NUSHELL("nu"), POWERSHELL("pwsh");

    private final String label;

    HistoryShell(String label) { this.label = label; }

    String label() { return label; }
}
