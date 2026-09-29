package dev.jasper.editor.vim;

/**
 * The textobj-entire extension (spec 8), ported from Kana Natsuno's textobj-entire 0.0.4 on vim-textobj-user:
 * {@code ae} is every line, {@code ie} every line but the empty ones at either end. Both select linewise, as the
 * plugin's functions return (Vim 9.1 has no v:motion_force for textobj-user to follow), and both set the jump mark
 * first.
 */
final class TextobjEntire {
    private TextobjEntire() { }

    private static final String OPTION = "textobj-entire";

    static void register(VimCommands commands) {
        commands.object("<Plug>(textobj-entire-a)", "the whole text (`ae`, with `set textobj-entire`)", c -> select(c, false));
        commands.object("<Plug>(textobj-entire-i)", "the whole text but the empty lines at either end (`ie`)", c -> select(c, true));
    }

    /** textobj#entire#select_a and select_i, through textobj-user's s:select_function_wrapper. */
    private static Span select(Context c, boolean inner) {
        Extensions.require(c, OPTION);
        Buffer b = c.editor.buffer();
        // normal! m'
        c.editor.marks().setpcmark(c.editor.cursor());
        int last = b.count() - 1;
        // keepjumps normal! gg0, then for ie search('^.', 'cW'): the first line with a character, if any.
        int first = 0;
        if (inner) for (int n = 0; n <= last; n++) if (!b.line(n).isEmpty()) {
            first = n;
            break;
        }
        // keepjumps normal! G$, then for ie search('^.', 'bcW') and normal! $: the last line with a character.
        int end = last;
        if (inner) for (int n = last; n >= 0; n--) if (!b.line(n).isEmpty()) {
            end = n;
            break;
        }
        return Script.select(c, "V", new Pos(first, 0), new Pos(end, Math.max(0, Text.last(b.line(end)))));
    }
}
