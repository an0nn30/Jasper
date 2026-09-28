package dev.jasper.editor.vim;

/**
 * One parsed Ex command, as Vim's exarg_T holds it: its range, name, {@code !}, register, count and argument. Line
 * numbers are Vim's (1 is the first line; 0 is before it).
 */
final class ExCommand {
    final VimEditor editor;
    /** What Jasper runs for it. */
    Ex.Def def;
    /** The command as typed from its name on (eap->cmd), for {@code :g}'s {@code v} and {@code :s}'s {@code s}. */
    String typed;
    int line1 = 1, line2 = 1;
    /** How many addresses were given (0 for none). */
    int addrCount;
    /** Whether {@code !} followed the name. */
    boolean forceit;
    /** The register named after it, or 0. */
    char regname;
    /** For {@code :>} and {@code :<}, how many {@code >} or {@code <} were typed. */
    int amount;
    /** The argument, after the name, register, count and flags. */
    String arg = "";
    /** {@link Ex#PRINT}, {@link Ex#LIST} and {@link Ex#NUMBER}: print the cursor line afterwards. */
    int flags;
    /** The command after a {@code |}, or null. */
    String nextcmd;

    ExCommand(VimEditor editor) { this.editor = editor; }

    /** @return the command's full name ({@code substitute}) */
    String name() { return def.name(); }

    /** @return the context an operator or put runs with: the editor and this command's register */
    Context context() {
        Context c = new Context(editor);
        c.register = regname;
        return c;
    }
}
