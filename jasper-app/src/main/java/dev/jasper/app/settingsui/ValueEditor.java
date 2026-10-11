package dev.jasper.app.settingsui;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;

/**
 * One setting's control on a page, as a kit descriptor and the value it holds. The page renders {@link #node} and
 * routes kit events back through {@link #event}; nothing here paints or styles Swing. EDT only.
 */
public interface ValueEditor<T> {
    /**
     * The control's descriptor for what it holds now. Every node id starts with {@code id}.
     *
     * @param id the row's control id, the setting's name
     * @param state enablement and the row's validation message, for the primary control
     */
    Nodes.Node node(String id, ControlState state);

    /** The control's current value; throws IllegalArgumentException when it cannot be read as one. */
    T value();

    /** Shows {@code value} without counting as an edit. */
    void show(T value);

    /**
     * Applies a kit event for one of this editor's nodes. Returns true when the user edited the value; events for
     * other nodes, and actions that edit nothing, return false.
     */
    boolean event(String id, UiEvent event);

    /** Whether the control carries the setting's label itself, as a switch does. */
    default boolean labelled() { return false; }

    /** Composite content occupies the whole settings card width. */
    default boolean fullSpan() { return false; }

    /** Full-span composite rows may join the surrounding settings card. */
    default boolean cardRows() { return false; }

    /** A compound editor may use one shared row label. */
    default String rowLabel(String label) { return label; }

    /** The value depends on the preceding setting and is visually subordinate. */
    default boolean dependent() { return false; }

    /** Additional validated values to stage with this edit. */
    default java.util.Map<dev.jasper.app.settings.Setting<?>, java.util.Optional<?>> companionValues() { return java.util.Map.of(); }

    /** Synchronizes companion values after the draft changes, without recreating controls. */
    default void showRelated() { }

    /** Other settings changed/reset together with this editor. */
    default java.util.List<dev.jasper.app.settings.Setting<?>> relatedSettings() { return java.util.List.of(); }

    /** Whether choosing the default value means "remove the key" rather than "save the default". */
    default boolean removesAtDefault() { return false; }

    /** Reads again whatever the control offers, such as its list of choices; its page calls this on each refresh. */
    default void refresh() { }

    /** Tells the editor how to ask its page to render again after a change it made itself, such as a refill. */
    default void onChanged(Runnable render) { }

    /** Releases whatever the control listens to; its page calls this when it is disposed. */
    default void dispose() { }
}
