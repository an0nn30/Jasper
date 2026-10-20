package dev.jasper.app.notificationui;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Font;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ErrorsViewTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final IncidentReport.Environment HERE = new IncidentReport.Environment("1.4.0", "TestOS 1 (arm)", "TestJava 25 (Vendor)");
    private final MutableClock clock = new MutableClock();
    private final Incidents incidents = new Incidents(clock);
    private final List<String> copied = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private int logFolders, dones;

    /** Views a test opens; each is closed afterwards, so no stale view (or its UI listeners) outlives its test. */
    private final List<ErrorsView> opened = new ArrayList<>();

    @AfterEach void closeViews() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { opened.forEach(ErrorsView::close); opened.clear(); });
    }

    private ErrorsView track(ErrorsView view) { opened.add(view); return view; }

    private ErrorsView view() {
        return track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.context(), incidents, new ErrorsView.Hooks(id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            id -> id.equals("dev.jasper.remote") ? "2.1.0" : "", HERE, copied::add, disabled::add, () -> logFolders++),
            ZoneOffset.UTC, () -> dones++));
    }

    private Incident record(String source, String operation, Throwable failure) {
        return incidents.record(source, operation, "AWT-EventQueue-0", Capture.of(failure));
    }

    @Test void enlargedNarrowFooterKeepsAllActionsSeparateAndListUsable() {
        var themes = ThemeTestSupport.controller(true);
        themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
        var view = new ErrorsView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),incidents,
            new ErrorsView.Hooks(id->"Plugin",id->"",HERE,copied::add,disabled::add,()->{}),ZoneOffset.UTC,()->{});
        try {
            var incident=record("dev.fixture","operation",new IllegalStateException("failure"));
            var originalTrace=view.traceArea();var document=originalTrace.getDocument();originalTrace.select(1,4);
            view.setSize(900,560);for(int i=0;i<5;i++)dev.jasper.app.testsupport.KitTestSupport.layout(view);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",32));
            view.setSize(600,320);for(int i=0;i<5;i++)dev.jasper.app.testsupport.KitTestSupport.layout(view);
            var heading=List.of(view.disableButton(),view.copyButton());
            var footer=List.of(view.logFolderButton(),view.clearButton(),view.doneButton());
            for(var group:List.of(heading,footer))for(int i=0;i<group.size();i++) {
                var box=bounds(group.get(i),view);
                assertThat(box.width).isGreaterThanOrEqualTo(group.get(i).getPreferredSize().width);
                assertThat(box.x+box.width).isLessThanOrEqualTo(view.getWidth());
                for(int j=i+1;j<group.size();j++)assertThat(box.intersects(bounds(group.get(j),view))).isFalse();
            }
            assertThat(ErrorsViewTestSupport.list(view).getWidth()).isGreaterThanOrEqualTo(140);
            var trace=view.traceArea();var viewport=(javax.swing.JViewport)trace.getParent();
            assertThat(viewport.getHeight()).as("two trace lines survive short height").isGreaterThanOrEqualTo(2*trace.getFontMetrics(trace.getFont()).getHeight());
            reveal(viewport.getParent());
            trace.scrollRectToVisible(new java.awt.Rectangle(0,0,1,trace.getFontMetrics(trace.getFont()).getHeight()));
            assertThat(trace.getVisibleRect().height).as("trace %s viewport %s visible %s",trace.getBounds(),viewport.getBounds(),trace.getVisibleRect()).isGreaterThanOrEqualTo(trace.getFontMetrics(trace.getFont()).getHeight());
            for(var action:List.of(view.disableButton(),view.copyButton(),view.logFolderButton(),view.clearButton(),view.doneButton())) {
                reveal(action);
                assertThat(action.getVisibleRect().height).isEqualTo(action.getHeight());
            }
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());
            view.setSize(900,560);for(int i=0;i<5;i++)dev.jasper.app.testsupport.KitTestSupport.layout(view);
            assertThat(view.selected()).map(Incident::id).contains(incident.id());assertThat(view.traceArea()).isSameAs(originalTrace);
            assertThat(originalTrace.getDocument()).isSameAs(document);assertThat(originalTrace.getSelectionStart()).isEqualTo(1);
            assertThat(detailScroll(view).getVerticalScrollBar().isVisible()).as("the default window shows the whole detail").isFalse();
        } finally {view.close();themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();}
    }

    @Test void freshEnlargedSplitLeavesTraceViewportReachable() {
        var themes=ThemeTestSupport.controller(true);
        themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system",32));
        var view=new ErrorsView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),incidents,
            new ErrorsView.Hooks(id->"Plugin",id->"",HERE,copied::add,disabled::add,()->{}),ZoneOffset.UTC,()->{});
        try {
            record("dev.fixture","operation",new IllegalStateException("failure"));
            view.setSize(600,320);for(int i=0;i<6;i++)dev.jasper.app.testsupport.KitTestSupport.layout(view);
            var trace=view.traceArea();var viewport=(javax.swing.JViewport)trace.getParent();
            assertThat(view.messageArea().getFont().getSize2D()).as("native body uses kit font exactly once").isEqualTo(view.rowLabels(0).get(0).getFont().getSize2D());
            assertThat(viewport.getWidth()).as("fresh enlarged trace lane").isGreaterThanOrEqualTo(100);
            var outer=detailScroll(view);
            reveal(viewport.getParent());
            trace.scrollRectToVisible(new java.awt.Rectangle(0,0,30,trace.getFontMetrics(trace.getFont()).getHeight()));
            assertThat(trace.getVisibleRect().height).as("trace %s viewport %s visible %s outer %s",trace.getBounds(),viewport.getBounds(),trace.getVisibleRect(),outer.getViewport().getViewRect()).isGreaterThanOrEqualTo(trace.getFontMetrics(trace.getFont()).getHeight());
        } finally {view.close();themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults());themes.close();}
    }

    @Test void incidentsAreListedNewestFirstWithSummarySourceAndTime() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("first broke\nmore detail"));
        clock.advance(Duration.ofSeconds(30));
        Incident second = record("dev.jasper.remote", "panel remote.sftp",
            new java.io.UncheckedIOException("no route", new java.io.IOException("no route")));
        assertThat(view.listed()).extracting(Incident::id).first().isEqualTo(second.id());
        assertThat(view.rowSummary(0)).isEqualTo("UncheckedIOException: no route");
        assertThat(view.rowDetail(0)).isEqualTo("Remote · 14:05:30");
        assertThat(view.rowSummary(1)).isEqualTo("IllegalStateException: first broke");
        assertThat(view.rowDetail(1)).isEqualTo("Jasper · 14:05:00");
    }

    @Test void theSelectedIncidentShowsItsSourceSightingsMessageAndMonospacedTrace() {
        var view = view();
        var failure = new IllegalStateException("could not open the panel\nbecause of reasons");
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", failure);
        clock.advance(Duration.ofMinutes(2));
        record("dev.jasper.remote", "panel remote.sftp", failure);
        assertThat(view.selected()).map(Incident::id).contains(incident.id());
        assertThat(view.showingEmpty()).isFalse();
        assertThat(view.headerText()).isEqualTo("Remote · panel remote.sftp");
        assertThat(view.sightingsText()).isEqualTo("First seen 14:05:00 · last seen 14:07:00 · 2 times");
        assertThat(view.messageText()).isEqualTo("could not open the panel\nbecause of reasons");
        assertThat(view.traceText()).isEqualTo(incident.trace()).startsWith("java.lang.IllegalStateException: could not open the panel");
        assertThat(view.traceFont().getName()).isEqualTo(Font.MONOSPACED);
        assertThat(view.rowDetail(0)).isEqualTo("Remote · ×2 · 14:07:00");
    }

    @Test void aSingleSightingSaysWhenItWasSeen() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("once"));
        assertThat(view.sightingsText()).isEqualTo("Seen at 14:05:00");
        assertThat(view.headerText()).isEqualTo("Jasper · thread main");
    }

    @Test void copyHandsTheReportToTheClipboardHook() {
        var view = view();
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", new IllegalStateException("broke"));
        view.copyButton().doClick();
        assertThat(copied).containsExactly(IncidentReport.text(incident, "2.1.0", HERE));
    }

    @Test void disablePluginIsOfferedOnlyForAPluginsIncident() {
        var view = view();
        Incident own = record("jasper", "thread main", new IllegalStateException("ours"));
        Incident plugin = record("dev.jasper.remote", "panel remote.sftp", new IllegalArgumentException("theirs"));
        view.select(own.id());
        assertThat(view.disableButton().isVisible()).isFalse();
        view.select(plugin.id());
        assertThat(view.disableButton().isVisible()).isTrue();
        view.disableButton().doClick();
        assertThat(disabled).containsExactly("dev.jasper.remote");
    }

    @Test void openLogFolderAndClear() {
        var view = view();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.clearButton().isEnabled()).isFalse();
        assertThat(view.copyButton()).as("nothing to copy without an incident").isNull();
        record("jasper", "thread main", new IllegalStateException("broke"));
        assertThat(view.clearButton().isEnabled()).isTrue();
        view.logFolderButton().doClick();
        assertThat(logFolders).isEqualTo(1);
        view.clearButton().doClick();
        assertThat(incidents.list()).isEmpty();
        assertThat(view.listed()).isEmpty();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.copyButton()).isNull();
        assertThat(view.statusText()).isEqualTo("No errors this session");
    }

    @Test void aNewIncidentKeepsTheUsersSelection() {
        var view = view();
        Incident older = record("jasper", "a", new IllegalStateException("older"));
        record("jasper", "b", new IllegalArgumentException("newer"));
        view.select(older.id());
        record("jasper", "c", new UnsupportedOperationException("newest"));
        assertThat(view.selected()).map(Incident::id).contains(older.id());
        assertThat(view.listed()).hasSize(3);
    }

    /** Review I3: after the user clicks a row, Show more… on another incident moves the list's row too, not only the details. */
    @Test void aHostSelectionAfterTheUsersClickMovesTheListRow() {
        var view = view();
        Incident oldest = record("jasper", "a", new IllegalStateException("oldest"));
        clock.advance(Duration.ofSeconds(1));
        Incident middle = record("jasper", "b", new IllegalArgumentException("middle"));
        clock.advance(Duration.ofSeconds(1));
        Incident newest = record("jasper", "c", new UnsupportedOperationException("newest"));
        assertThat(view.selected()).map(Incident::id).as("the first incident stays selected").contains(oldest.id());
        view.setSize(900, 560);
        for (int pass = 0; pass < 3; pass++) dev.jasper.app.testsupport.KitTestSupport.layout(view);
        var label = dev.jasper.app.testsupport.KitTestSupport.require(view, "summary:" + newest.id(), javax.swing.JComponent.class);
        for (var listener : label.getMouseListeners())
            listener.mousePressed(new java.awt.event.MouseEvent(label, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
        for (var listener : label.getMouseListeners())
            listener.mouseClicked(new java.awt.event.MouseEvent(label, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
        assertThat(view.selected()).map(Incident::id).as("the user's click").contains(newest.id());
        view.select(oldest.id());
        assertThat(view.listNode().selectedIds()).containsExactly(oldest.id().toString());
        var canvas = canvas(ErrorsViewTestSupport.list(view));
        canvas.getActionMap().get(canvas.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("UP"))).actionPerformed(null);
        assertThat(view.selected()).map(Incident::id).as("Up moves on from the host's row").contains(middle.id());
        assertThat(view.headerText()).isEqualTo("Jasper · b");
    }

    private static javax.swing.JComponent canvas(java.awt.Component root) {
        if (root instanceof javax.swing.JComponent c && c.isFocusable() && c.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("DOWN")) != null) return c;
        if (root instanceof java.awt.Container container) for (var child : container.getComponents()) {
            var found = canvas(child);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * The window is a manager window: a WINDOW form whose body is a list column beside the detail column (the Vault
     * manager's arrangement without its rail), the incident's heading with its actions, the message and the trace in
     * detail cards, the sightings caption, and Open Log Folder, Clear and the right-most primary Done in the footer band.
     */
    @Test void theWindowIsAManagerListAndDetailWithTheFooterBand() {
        var view = view();
        record("dev.jasper.remote", "panel remote.sftp", new IllegalStateException("broke"));
        view.setSize(900, 560);
        for (int pass = 0; pass < 3; pass++) dev.jasper.app.testsupport.KitTestSupport.layout(view);
        var form = (dev.jasper.ui.model.Nodes.Form) view.kitView().spec().root();
        assertThat(form.kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.WINDOW);
        assertThat(form.submission()).as("Enter on a row never closes the window").isEqualTo(dev.jasper.ui.model.Nodes.FormSubmission.NONE);
        assertThat(form.cancelAction()).as("Escape is Done").isEqualTo(ErrorsView.DONE);
        var columns = (dev.jasper.ui.model.Nodes.Split) form.sections().getFirst();
        assertThat(columns.kind()).isEqualTo(dev.jasper.ui.model.Nodes.SplitKind.SIDEBAR_DETAIL);
        var listColumn = dev.jasper.app.testsupport.KitTestSupport.require(view, "errors.list", javax.swing.JComponent.class);
        assertThat(listColumn.getWidth()).as("the manager list width").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(
            dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.MANAGER_LIST_WIDTH)));
        assertThat(form.actions()).extracting(dev.jasper.ui.model.Nodes.Button::label).containsExactly("Open Log Folder", "Clear", "Done");
        assertThat(form.actions().getLast().kind()).isEqualTo(dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY);
        var title = bounds(view.titleLabel(), view);
        var source = bounds(view.headerLabel(), view);
        var copy = bounds(view.copyButton(), view);
        var disable = bounds(view.disableButton(), view);
        var trace = bounds(view.traceArea().getParent().getParent(), view);
        var sightings = bounds(view.sightingsLabel(), view);
        assertThat(view.titleText()).isEqualTo("IllegalStateException: broke");
        assertThat(source.y).as("source under the title").isGreaterThanOrEqualTo(title.y + title.height);
        assertThat(disable.x).as("Disable Plugin before Copy, right of the identity").isLessThan(copy.x).isGreaterThan(title.x);
        assertThat(copy.x + copy.width).isLessThanOrEqualTo(view.getWidth());
        assertThat(trace.y).as("the trace card under the heading").isGreaterThan(copy.y + copy.height);
        assertThat(sightings.y).as("when it was seen follows the cards").isGreaterThanOrEqualTo(trace.y + trace.height);
        var done = bounds(view.doneButton(), view);
        var clear = bounds(view.clearButton(), view);
        var folder = bounds(view.logFolderButton(), view);
        assertThat(folder.x + folder.width).isLessThanOrEqualTo(clear.x);
        assertThat(clear.x + clear.width).isLessThanOrEqualTo(done.x);
        assertThat(done.y).as("one footer row").isEqualTo(clear.y).isEqualTo(folder.y);
        assertThat(view.statusText()).isEqualTo("1 error this session");
        view.doneButton().doClick();
        assertThat(dones).as("Done closes the window").isEqualTo(1);
    }

    private static java.awt.Rectangle bounds(java.awt.Component component, java.awt.Component root) {
        return javax.swing.SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    /** Scrolls every scrolling ancestor of {@code target} until its top is in view, as a user scrolling there would. */
    private static void reveal(java.awt.Component target) {
        for (var parent = target.getParent(); parent != null; parent = parent.getParent())
            if (parent instanceof javax.swing.JViewport viewport && viewport.getView() != null) {
                var at = javax.swing.SwingUtilities.convertPoint(target, 0, 0, viewport.getView());
                int limit = Math.max(0, viewport.getView().getHeight() - viewport.getExtentSize().height);
                viewport.setViewPosition(new java.awt.Point(viewport.getViewPosition().x, Math.min(limit, Math.max(0, at.y))));
            }
    }

    /** The detail column's own scroll pane: a short window scrolls the detail, never the footer band. */
    private static javax.swing.JScrollPane detailScroll(ErrorsView view) {
        return dev.jasper.app.testsupport.KitTestSupport.require(view, "errors.detail.body-scroll", javax.swing.JScrollPane.class);
    }

    @Test void selectIgnoresAnIncidentThatIsNotListed() {
        var view = view();
        Incident listed = record("jasper", "a", new IllegalStateException("listed"));
        view.select(UUID.randomUUID());
        assertThat(view.selected()).map(Incident::id).contains(listed.id());
    }

    @Test void textIsNeverMarkup() {
        var view = track(new ErrorsView(dev.jasper.app.testsupport.KitTestSupport.context(), incidents, new ErrorsView.Hooks(id -> "<html><b>Evil</b>", id -> "", HERE, copied::add,
            disabled::add, () -> { }), ZoneOffset.UTC, () -> { }));
        record("dev.example.evil", "<html><i>op</i>", new IllegalStateException("<html><u>message</u>"));
        assertThat(view.headerText()).isEqualTo("<html><b>Evil</b> · <html><i>op</i>");
        for (JLabel label : List.of(view.headerLabel(), view.rowLabels(0).get(0), view.rowLabels(0).get(1))) {
            assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(label.getClientProperty(BasicHTML.propertyKey)).isNull();
        }
    }

    /** The list is a kit list whose rows carry the danger glyph; the message and trace stay selectable read-only text. */
    @Test void theListIsAKitListAndTheTextsStaySelectableAcrossAThemeSwitch() {
        var bindings = dev.jasper.app.testsupport.KitTestSupport.bindings(true);
        try {
            var kit = bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var view = new ErrorsView(kit, incidents, new ErrorsView.Hooks(id -> id, id -> "", HERE, copied::add, disabled::add, () -> { }), ZoneOffset.UTC, () -> { });
            Incident first = record("jasper", "a", new IllegalStateException("first"));
            Incident second = record("jasper", "b", new IllegalStateException("second"));
            view.select(first.id());
            var list = view.listNode();
            assertThat(list.selectedIds()).containsExactly(first.id().toString());
            var icon = (dev.jasper.ui.model.Nodes.Icon) list.items().getFirst().leading().orElseThrow();
            assertThat(icon.name()).isEqualTo("error");
            assertThat(icon.severity()).contains(dev.jasper.ui.model.Nodes.Severity.DANGER);
            assertThat(view.traceArea().isEditable()).isFalse();
            view.traceArea().select(0, 5);
            bindings.binding().publish(dev.jasper.app.appearance.ThemeTestSupport.pair(false));
            assertThat(view.selected()).map(Incident::id).contains(first.id());
            assertThat(view.traceArea().getSelectionStart()).isZero();
            assertThat(view.traceArea().getSelectionEnd()).isEqualTo(5);
            assertThat(view.traceArea().getForeground()).isEqualTo(kit.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
            assertThat(second.id()).isNotEqualTo(first.id());
            view.close();
        } finally { bindings.close(); }
    }

    @Test void closingStopsListening() {
        var view = view();
        view.close();
        record("jasper", "a", new IllegalStateException("after close"));
        assertThat(view.listed()).isEmpty();
    }
}
