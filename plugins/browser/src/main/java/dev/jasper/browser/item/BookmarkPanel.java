package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginPopover;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Edit Bookmark panel (spec section 10), which Add Bookmark opens once the page is bookmarked: a popover from the
 * item's menu button holding the bookmark's Title and URL, read-only, and its Tags, a token field, the one place tags
 * are edited. Save closes it. Delete Bookmark closes it and asks, over the item's tab, "Are you sure you want to delete
 * this bookmark?"; Delete then deletes it and says "Bookmark Deleted". The panel also closes at its page's next
 * main-frame load start, when its item closes, and on Escape or a click outside. However it closes, the bookmark keeps
 * the tags the panel shows, but for Escape, which drops them (plan 5's H23); tags it opened with are not written again.
 * UI thread.
 */
public final class BookmarkPanel {
    /** The group the bookmark notifications post in, which the plugin declares as it starts. */
    public static final GroupSpec GROUP = new GroupSpec("dev.jasper.browser.bookmarks", "Bookmarks", DisplayMode.CARD);
    static final String FORM = "bookmark", CARD = "bookmark.card", TITLE = "bookmark.title", URL = "bookmark.url", TAGS = "bookmark.tags",
        DELETE = "bookmark.delete", SAVE = "bookmark.save", CANCEL = "bookmark.cancel";
    /** The question Delete Bookmark asks, its form, and what its Cancel and Delete report. */
    static final String ASK = "bookmark.ask", QUESTION = "bookmark.ask.question", CANCEL_DELETE = "bookmark.ask.cancel",
        CONFIRM_DELETE = "bookmark.ask.delete";
    private static final String DELETE_TITLE = "Delete Bookmark";
    private static final System.Logger LOG = System.getLogger(BookmarkPanel.class.getName());
    private final PluginContext context;
    private final Stores stores;
    private final Bookmark bookmark;
    private final NotificationGroup group;
    private final ContentTab owner;
    // The tags as the field shows them, which the bookmark keeps as the panel closes.
    private List<String> tags;
    private PluginPopover popover;
    private UiView view;
    private List<Subscription> ends = List.of();
    // Whether Save, Delete Bookmark or Escape closed the panel, which keep the tags or drop them themselves.
    private boolean settled;
    private boolean closed;

    private BookmarkPanel(PluginContext context, Stores stores, Bookmark bookmark, NotificationGroup group, ContentTab owner) {
        this.context = Objects.requireNonNull(context, "context");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.bookmark = Objects.requireNonNull(bookmark, "bookmark");
        this.group = Objects.requireNonNull(group, "group");
        this.owner = Objects.requireNonNull(owner, "owner");
        tags = bookmark.tags();
    }

    /**
     * Shows the panel for {@code bookmark} below {@code anchor}, the Tags field focused.
     *
     * @param context the plugin's context
     * @param anchor the item's menu button's anchor
     * @param stores the item's stores, which keep the bookmark
     * @param bookmark the bookmark, as stored
     * @param group where "Bookmark Deleted" posts
     * @param page the item's page, whose next main-frame load start, or close, closes the panel
     * @param owner the item's tab, over which Delete Bookmark asks
     * @return the panel, shown
     * @throws IllegalArgumentException when the anchor has closed
     */
    static BookmarkPanel open(PluginContext context, UiAnchor anchor, Stores stores, Bookmark bookmark, NotificationGroup group, Page page,
                              ContentTab owner) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(page, "page");
        var panel = new BookmarkPanel(context, stores, bookmark, group, owner);
        panel.show(anchor, page);
        return panel;
    }

    /**
     * Closes the panel, its content with it, as a click outside does: the bookmark keeps the tags shown. Its page's load
     * start and close close it so too.
     */
    void close() {
        if (popover != null) popover.close();
    }

    private void show(UiAnchor anchor, Page page) {
        PluginPopover shown = context.windows().popover(anchor);
        popover = shown;
        try {
            view = context.kit().create(spec(), this::event);
            shown.setContent(view);
            shown.onClosed(this::ended);
            // The page is listened to before the popover shows; a page closed already closes the panel at once.
            ends = List.of(page.onLoadStart(address -> close()), page.onClose(this::close));
            if (closed) stopListening();
            else shown.show();
        } catch (RuntimeException | Error failure) {
            if (view != null) view.close();
            shown.close();
            throw failure;
        }
        if (!closed) view.focus(TAGS);
    }

    /** The popover closed, however it did: the page is no longer listened to, and unless the panel settled, the tags kept. */
    private void ended() {
        closed = true;
        stopListening();
        if (!settled) keepTags();
    }

    private void stopListening() {
        for (Subscription end : ends) end.close();
        ends = List.of();
    }

    /** Save and Delete Bookmark keep the tags shown, and Escape drops them; each closes the panel. */
    private void settle(boolean keep) {
        settled = true;
        if (keep) keepTags();
        close();
    }

    /** The bookmark gets the tags shown, unless they are its own: each write reads the bookmarks again. */
    private void keepTags() {
        if (tags.equals(bookmark.tags())) return;
        stores.bookmarks().tags(bookmark.id(), tags).whenComplete((saved, failure) -> {
            if (failure != null) LOG.log(System.Logger.Level.DEBUG, "A bookmark's tags were not saved", failure);
        });
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.TokensChanged changed when changed.id().equals(TAGS) -> {
                tags = changed.tokens();
                view.update(spec());
            }
            case UiEvent.Action action when action.id().equals(SAVE) -> settle(true);
            case UiEvent.Action action when action.id().equals(DELETE) -> {
                // The tags typed before are kept, so the question's Cancel leaves the bookmark as the panel showed it (H25).
                settle(true);
                // Asked once the popover has gone, from a turn of its own: a modal shows and waits.
                EventQueue.invokeLater(this::ask);
            }
            // Escape.
            case UiEvent.Action action when action.id().equals(CANCEL) -> settle(false);
            default -> { }
        }
    }

    /** "Are you sure you want to delete this bookmark?", over the item's tab: Delete deletes it, and says so. */
    private void ask() {
        PluginDialog dialog;
        try {
            dialog = context.windows().dialog(new DialogSpec(DELETE_TITLE, owner, true, DialogSpec.Presentation.OVERLAY));
        } catch (RuntimeException refused) { // the item's tab closed, or the plugin stops
            LOG.log(System.Logger.Level.DEBUG, "The bookmark's deletion could not ask", refused);
            return;
        }
        UiView question = null;
        try {
            question = context.kit().create(question(), event -> {
                if (!(event instanceof UiEvent.Action action)) return;
                if (action.id().equals(CONFIRM_DELETE)) delete();
                if (action.id().equals(CONFIRM_DELETE) || action.id().equals(CANCEL_DELETE)) dialog.close();
            });
            dialog.setContent(question);
            question = null; // the dialog's now, which disposes it as it closes
            dialog.show(); // a modal overlay returns once it closes
        } catch (RuntimeException failed) {
            LOG.log(System.Logger.Level.WARNING, "The bookmark's deletion could not ask", failed);
            if (question != null) question.close();
            dialog.close();
        }
    }

    /** Deletes the bookmark, and once it is gone says "Bookmark Deleted", its title only: no address. */
    private void delete() {
        stores.bookmarks().delete(bookmark.id()).whenComplete((deleted, failure) -> EventQueue.invokeLater(() -> {
            if (failure != null) {
                LOG.log(System.Logger.Level.DEBUG, "A bookmark could not be deleted", failure);
                return;
            }
            if (!deleted) return;
            try {
                group.info("Bookmark Deleted", "");
            } catch (IllegalStateException stopped) { // the plugin stopped meanwhile
                LOG.log(System.Logger.Level.DEBUG, "A bookmark's deletion was not posted", stopped);
            }
        }));
    }

    /** The panel: Title, URL and Tags in one form card, then Delete Bookmark and Save; Escape closes it. */
    private ViewSpec spec() {
        var title = new Nodes.Field(TITLE, ControlState.READY, Nodes.FieldKind.TEXT, bookmark.title(), "", "Title", "", Optional.empty(),
            Nodes.FieldPresentation.STANDARD, "", "", "", true);
        var url = new Nodes.Field(URL, ControlState.READY, Nodes.FieldKind.TEXT, bookmark.url(), "", "URL", "", Optional.empty(),
            Nodes.FieldPresentation.STANDARD, "", "", "", true);
        var tagsField = new Nodes.TokenField(TAGS, ControlState.READY, "Tags", "Enter tags…", tags);
        var card = new Nodes.Card(CARD, ControlState.READY, Nodes.CardKind.FORM, List.of(row("title", "Title", title),
            row("url", "URL", url), row("tags", "Tags", tagsField)));
        var actions = List.of(
            new Nodes.Button(DELETE, ControlState.READY, "Delete Bookmark", "", Nodes.ButtonKind.DESTRUCTIVE_GHOST, DELETE, ""),
            new Nodes.Button(SAVE, ControlState.READY, "Save", "", Nodes.ButtonKind.PRIMARY, SAVE, ""));
        return new ViewSpec(new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.SHEET, "Edit Bookmark", Optional.empty(), List.of(card),
            Optional.empty(), actions, CANCEL));
    }

    private static Nodes.FormRow row(String id, String label, Nodes.Node control) {
        return new Nodes.FormRow(FORM + "." + id + ".row", ControlState.READY, label, "", control, Optional.empty(), false);
    }

    /** Delete Bookmark's question: Delete is the default, and Escape is Cancel. */
    private static ViewSpec question() {
        var actions = List.of(new Nodes.Button(CANCEL_DELETE, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL_DELETE, ""),
            new Nodes.Button(CONFIRM_DELETE, ControlState.READY, "Delete", "", Nodes.ButtonKind.PRIMARY, CONFIRM_DELETE, ""));
        return new ViewSpec(new Nodes.Form(ASK, ControlState.READY, Nodes.FormKind.SHEET, DELETE_TITLE, Optional.empty(),
            List.of(new Nodes.Text(QUESTION, ControlState.READY, "Are you sure you want to delete this bookmark?", Nodes.TextRole.BODY)),
            Optional.empty(), actions, CANCEL_DELETE));
    }
}
