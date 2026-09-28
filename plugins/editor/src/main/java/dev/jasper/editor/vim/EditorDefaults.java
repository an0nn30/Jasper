package dev.jasper.editor.vim;

/**
 * Option defaults the Editor supplies for a tab (spec 3.8), used until the user sets the option.
 *
 * @param tabstop the Editor's tab size, used for 'tabstop' and 'shiftwidth'
 * @param expandtab whether the tab indents with spaces
 * @param autoindent whether new lines copy the indent (the Editor auto-indents, so Jasper passes true)
 * @param wrap whether long lines wrap (the tab's Wrap state), for 'wrap'
 */
public record EditorDefaults(int tabstop, boolean expandtab, boolean autoindent, boolean wrap) {
    /** Vim's own defaults ({@code -u NONE}), which the corpus uses. */
    public static final EditorDefaults VIM = new EditorDefaults(8, false, false, true);

    /**
     * Defaults with lines wrapping.
     *
     * @param tabstop the Editor's tab size
     * @param expandtab whether the tab indents with spaces
     * @param autoindent whether new lines copy the indent
     */
    public EditorDefaults(int tabstop, boolean expandtab, boolean autoindent) { this(tabstop, expandtab, autoindent, true); }
}
