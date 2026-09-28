package dev.jasper.editor.vim;

/** The mode Vim reports (spec 3.2); it drives the caret, the handler rule and the Vim bar. */
public enum Mode {
    /** Normal mode. */
    NORMAL("normal", ""),
    /** An operator waits for its motion. */
    OPERATOR_PENDING("operator-pending", ""),
    /** Insert mode. */
    INSERT("insert", "-- INSERT --"),
    /** Replace mode ({@code R}). */
    REPLACE("replace", "-- REPLACE --"),
    /** One Normal-mode command from Insert mode ({@code i_CTRL-O}). */
    INSERT_NORMAL("insert-normal", "-- (insert) --"),
    /** Characterwise Visual mode ({@code v}). */
    VISUAL("visual", "-- VISUAL --"),
    /** Linewise Visual mode ({@code V}). */
    VISUAL_LINE("visual-line", "-- VISUAL LINE --"),
    /** Blockwise Visual mode ({@code CTRL-V}). */
    VISUAL_BLOCK("visual-block", "-- VISUAL BLOCK --"),
    /** A command line is being typed ({@code /}, {@code ?}). */
    COMMAND_LINE("command-line", "");

    private final String id, label;

    Mode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** @return the name the corpus uses, such as {@code insert} */
    public String id() { return id; }

    /** @return what the Vim bar shows with 'showmode', such as {@code -- INSERT --}; empty for Normal mode */
    public String label() { return label; }

    /** @return whether typed characters go into the text */
    public boolean inserting() { return this == INSERT || this == REPLACE; }

    /** @return whether this is one of the Visual modes */
    public boolean visual() { return this == VISUAL || this == VISUAL_LINE || this == VISUAL_BLOCK; }
}
