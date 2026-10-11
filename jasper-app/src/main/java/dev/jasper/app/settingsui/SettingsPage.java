package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * One page of the Settings dialog, in the manner of IntelliJ's {@code Configurable}. EDT only. Schema pages share the
 * dialog's {@link SettingsDraft}, which the dialog saves once before it applies other pages.
 */
public interface SettingsPage {
    /** A stable id, such as {@code terminal.font}. */
    String id();

    /** The title shown in the tree and the breadcrumb. */
    String title();

    /** The id of the page this one sits under. */
    Optional<String> parent();

    /** Optional registered artwork key for this page; custom/plugin identity remains supplied by its owner. */
    default Optional<String> icon() { return Optional.empty(); }

    /** Text the tree search matches: labels, descriptions, keywords and choice names. */
    List<String> searchTerms();

    /** The page's content; built on first use and kept until {@link #dispose}. */
    JComponent component();

    /** Whether the page holds changes Apply would save. */
    boolean modified();

    /** Drops this page's unapplied changes. */
    void reset();

    /** Saves this page's changes; completes on any thread. */
    CompletableFuture<Void> apply();

    /** Marks what matches {@code query} on the page; a blank query clears the marks. */
    void highlight(String query);

    /** Releases listeners; the page is not used again. */
    void dispose();

    /** Whether every control holds a value Apply could save. */
    default boolean valid() { return true; }

    /** Whether the tree hides this page while no page names it as parent. */
    default boolean hiddenWhenChildless() { return false; }

    /** The application's state changed; re-read anything shown from it. */
    default void refresh() { }

    /** The dialog shows this page now; called each time it is shown. */
    default void selected() { }

    /**
     * Shows the part of the page {@code fragment} names, such as a tab, without loading anything; the dialog calls
     * {@link #selected()} right after. Unknown fragments are ignored.
     */
    default void reveal(String fragment) { }

    /** The configuration guide section that documents this page. */
    default String helpAnchor() { return "supported-settings"; }
}
