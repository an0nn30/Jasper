package dev.jasper.terminal.view;

/** Reusable terminal commands, dispatched synchronously on the Event Dispatch Thread. */
public enum TerminalAction {
    COPY_SELECTION, PASTE_CLIPBOARD, CLEAR_SCROLLBACK,
    FIND_NEXT, FIND_PREVIOUS, PREVIOUS_PROMPT, NEXT_PROMPT
}
