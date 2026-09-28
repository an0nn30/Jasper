package dev.jasper.editor.vim;

import java.util.List;
import java.util.Map;

/**
 * One case of the differential corpus (spec 11.1): a starting text, cursor and options, the keys typed, and what
 * Vim did.
 *
 * @param file the file it came from, for messages
 * @param name its title
 * @param divergent why Jasper deliberately differs, or null; a divergent case's expectation is written by hand
 * @param set {@code :set} arguments applied first, or empty
 * @param given registers set first
 * @param text the starting lines
 * @param line the cursor's line, 1-based
 * @param col the cursor's column in characters, 1-based
 * @param keys the keys, in Vim notation
 * @param registers the registers whose contents are recorded and compared
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, Expected expected) {
    /**
     * A register's content as the corpus writes it.
     *
     * @param type as getregtype() gives it, but in letters: {@code c} charwise, {@code l} linewise, or {@code b} and
     *     the width for a block ({@code b3})
     * @param text its text, as getreg() gives it
     */
    record Register(String type, String text) {
        /** @return the content a register holds for it */
        Registers.Content content() {
            if (type.startsWith("b")) {
                return new Registers.Content(java.util.List.of(text.split("\n", -1)), Registers.Type.BLOCK, Integer.parseInt(type.substring(1)) - 1);
            }
            return Registers.Content.of(text, type.equals("l") ? Registers.Type.LINE : Registers.Type.CHAR);
        }

        /** @return how the corpus writes a register's content */
        static Register of(Registers.Content content) {
            String type = switch (content.type()) {
                case CHAR -> "c";
                case LINE -> "l";
                case BLOCK -> "b" + (content.width() + 1);
            };
            return new Register(type, content.text());
        }
    }

    /**
     * The result.
     *
     * @param recordedBy the Vim that recorded it ({@code vim 9.1}), or {@code expected} for a divergent case
     * @param text the lines
     * @param line the cursor line, 1-based
     * @param col the cursor column in characters, 1-based
     * @param mode {@link Mode#id()}
     * @param visual in Visual mode, the other end of the area as {@code line,col} (1-based, in characters); else null
     * @param registers the compared registers; a missing value means empty
     */
    record Expected(String recordedBy, List<String> text, int line, int col, String mode, String visual, Map<Character, Register> registers) { }

    CorpusCase withExpected(Expected value) { return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, value); }

    @Override public String toString() { return file + ": " + name; }
}
