package dev.jasper.ui.swing;

/** The empty point (spec 5.2): the tile, title, description and actions, or a bold line, a sentence and one button. */
interface EmptyFace extends Face {
    /** Whether the state shows its tile. */
    boolean tile();
    /** Which of the state's {@code declared} action controls are placed, in their order. */
    java.util.List<ControlRegistry.Control> actions(java.util.List<ControlRegistry.Control> declared);
    /** Styles the title and description after the state refreshes. */
    void style(EmptyStates.EmptyControl empty);
}
