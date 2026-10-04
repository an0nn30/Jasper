package dev.jasper.app.settingsui;

import javax.swing.JComponent;

/** One setting's control on a page. EDT only. */
public interface ValueEditor<T> {
    JComponent component();

    /** The control's current value; throws IllegalArgumentException when it cannot be read as one. */
    T value();

    /** Shows {@code value} without counting as an edit. */
    void show(T value);

    /** Runs {@code listener} after each edit the user makes. */
    void onEdit(Runnable listener);

    /** Whether the control shows the setting's label itself, as a checkbox does. */
    default boolean labelled() { return false; }

    /** Whether choosing the default value means "remove the key" rather than "save the default". */
    default boolean removesAtDefault() { return false; }

    /** Reads again whatever the control offers, such as its list of choices; its page calls this on each refresh. */
    default void refresh() { }

    /** Releases whatever the control listens to; its page calls this when it is disposed. */
    default void dispose() { }
}
