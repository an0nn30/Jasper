package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One window's transfers as kit descriptors for the SFTP sidebar (Plan 2 Task 8c, ruling G8): a "Transfers" heading over
 * two-line rows keyed by the job's id, at most three visible before the list scrolls, and nothing at all while there is
 * no transfer. A row shows the direction arrow and title, a progress bar (indeterminate while scanning) with the status
 * text, the one action that applies as a link and a close button that cancels a running transfer or dismisses a finished
 * one (disabled while cancelling). Long titles truncate at the end; the row tooltip carries the full title, both paths
 * and the status. The strip owns no view: the SFTP panel renders {@link #node()} in its footer and hands its events to
 * {@link #handle}. UI thread only.
 */
public final class TransferStrip {
    static final int VISIBLE_ROWS = 3;
    public static final String ROOT = "transfers", HEADING = "transfers.heading", ROWS = "transfers.rows", UNAVAILABLE = "transfers.unavailable";
    static final String ACTION = "transfer.action:", CLOSE = "transfer.close:";
    public record Actions(BiConsumer<UUID, TransferRows.Action> action, Consumer<UUID> cancel, Consumer<UUID> dismiss) { }
    private final Actions actions;
    private List<TransferRows.Row> rows = List.of();
    private String unavailable = "", selected;
    private Runnable changed = () -> { };

    public TransferStrip(Actions actions) { this.actions = Objects.requireNonNull(actions); }

    /** Runs after every change of what the strip shows, so its owner can render it again. */
    public void onChanged(Runnable listener) { changed = Objects.requireNonNull(listener); }

    /** Shows these transfers, in this order; an unchanged list changes nothing, so a refresh tick never re-renders. */
    public void rows(List<TransferRows.Row> next) {
        var copy = List.copyOf(next);
        if (copy.equals(rows) && unavailable.isEmpty()) return;
        rows = copy;
        unavailable = "";
        if (selected != null && rows.stream().noneMatch(row -> rowId(row.id()).equals(selected))) selected = null;
        changed.run();
    }

    /** One line in place of the rows: the queue cannot be read (another instance owns it). */
    public void unavailable(String message) {
        if (message.equals(unavailable) && rows.isEmpty()) return;
        rows = List.of();
        selected = null;
        unavailable = message;
        changed.run();
    }

    /** Whether the strip shows anything: rows or the unavailable line. */
    public boolean visible() { return !rows.isEmpty() || !unavailable.isEmpty(); }

    /** The strip's descriptors, empty while there is nothing to show. */
    public Optional<Nodes.Node> node() {
        if (!visible()) return Optional.empty();
        var children = new ArrayList<Nodes.Node>();
        children.add(new Nodes.Text(HEADING, ControlState.READY, "Transfers", Nodes.TextRole.SECTION_LABEL));
        if (!unavailable.isEmpty()) children.add(new Nodes.Text(UNAVAILABLE, ControlState.READY, unavailable, Nodes.TextRole.DESCRIPTION));
        else children.add(new Nodes.ItemList(ROWS, ControlState.READY, Nodes.ListKind.DOUBLE, rows.stream().map(TransferStrip::item).toList(),
            selected == null ? Set.of() : Set.of(selected), "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(VISIBLE_ROWS)));
        return Optional.of(new Nodes.Stack(ROOT, ControlState.READY, Nodes.LayoutRole.VERTICAL, children));
    }

    /** Acts on one of the strip's events and says whether it was one. */
    public boolean handle(UiEvent event) {
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(ROWS) -> {
                selected = chosen.ids().stream().findFirst().orElse(null);
                return true;
            }
            case UiEvent.Action action when action.id().startsWith(ACTION) -> {
                job(action.id().substring(ACTION.length())).ifPresent(row -> row.action().ifPresent(chosen -> actions.action().accept(row.id(), chosen)));
                return true;
            }
            case UiEvent.Action action when action.id().startsWith(CLOSE) -> {
                job(action.id().substring(CLOSE.length())).filter(row -> !row.cancelling())
                    .ifPresent(row -> { if (row.finished()) actions.dismiss().accept(row.id()); else actions.cancel().accept(row.id()); });
                return true;
            }
            default -> { return false; }
        }
    }

    private Optional<TransferRows.Row> job(String id) { return rows.stream().filter(row -> row.id().toString().equals(id)).findFirst(); }

    Optional<TransferRows.Row> row(UUID id) { return rows.stream().filter(row -> row.id().equals(id)).findFirst(); }
    List<UUID> order() { return rows.stream().map(TransferRows.Row::id).toList(); }
    String unavailableText() { return unavailable; }

    static String rowId(UUID id) { return id.toString(); }
    static String titleId(UUID id) { return "transfer." + id + ".title"; }
    static String progressId(UUID id) { return "transfer." + id + ".progress"; }
    static String statusId(UUID id) { return "transfer." + id + ".status"; }
    static String actionId(UUID id) { return "transfer." + id + ".action"; }
    static String closeId(UUID id) { return "transfer." + id + ".close"; }

    /** The visible title: the direction arrow and what goes where. */
    static String fullTitle(TransferRows.Row row) { return row.arrow() + " " + row.title(); }

    /** The row's literal tooltip: the full title (which may truncate), both endpoints with their paths, and the status. */
    static String tooltip(TransferRows.Row row) {
        return fullTitle(row) + " — " + row.tooltip() + (row.status().isEmpty() ? "" : " — " + row.status());
    }

    private static ListItem item(TransferRows.Row row) {
        UUID id = row.id();
        boolean progressShown = row.indeterminate() || row.fraction().isPresent();
        var subtitle = List.<Nodes.Node>of(
            new Nodes.Progress(progressId(id), new ControlState(progressShown, true, false, ""), row.indeterminate() ? -1 : row.fraction().orElse(0), ""),
            new Nodes.Text(statusId(id), ControlState.READY, row.status(), Nodes.TextRole.DESCRIPTION));
        var trailing = new ArrayList<Nodes.Node>();
        row.action().ifPresent(action -> trailing.add(new Nodes.Button(actionId(id), ControlState.READY, action.label(), "", Nodes.ButtonKind.LINK,
            ACTION + id, "", Nodes.ButtonPresentation.EMBEDDED)));
        trailing.add(new Nodes.Button(closeId(id), new ControlState(true, !row.cancelling(), false, ""), "", "close", Nodes.ButtonKind.GHOST,
            CLOSE + id, row.finished() ? "Dismiss" : "Cancel transfer", Nodes.ButtonPresentation.EMBEDDED));
        return new ListItem(rowId(id), Optional.empty(),
            List.of(new Nodes.Text(titleId(id), ControlState.READY, fullTitle(row), Nodes.TextRole.BODY)),
            subtitle, trailing, false, Optional.empty(), tooltip(row));
    }
}
