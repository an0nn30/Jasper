package dev.jasper.terminal.view;

/** Reusable terminal commands, dispatched synchronously on the Event Dispatch Thread. */
public enum TerminalAction {
    /** Copy validated selected text. */
    COPY_SELECTION,
    /** Paste available clipboard text using terminal paste rules. */
    PASTE_CLIPBOARD,
    /** Clear saved history, retaining the live screen. */
    CLEAR_SCROLLBACK,
    /** Reveal the next newer match, wrapping. */
    FIND_NEXT,
    /** Reveal the next older match, wrapping. */
    FIND_PREVIOUS,
    /** Scroll to the preceding retained prompt. */
    PREVIOUS_PROMPT,
    /** Scroll to the next retained prompt. */
    NEXT_PROMPT
}
