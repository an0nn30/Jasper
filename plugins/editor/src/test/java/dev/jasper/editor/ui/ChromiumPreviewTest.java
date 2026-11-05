package dev.jasper.editor.ui;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.MarkdownRenderer;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.markdown.resources.ResourceSession;
import dev.jasper.editor.testsupport.EdtTests;
import dev.jasper.editor.testsupport.FakeMarkdownPreviews;
import dev.jasper.sdk.Variant;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.fife.ui.rtextarea.SearchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** The Chromium preview over the Browser's API, with a recording preview in place of Chromium's page. */
@ExtendWith(EdtTests.class)
class ChromiumPreviewTest {
    private static final String TEXT = "# Target\n\nSee [one](a.md), [two](b.md), [three](c.md) and [four](d.md).\n";
    private final FakeMarkdownPreviews previews = new FakeMarkdownPreviews();
    private final List<Integer> links = new ArrayList<>();
    private final AtomicInteger geometry = new AtomicInteger();
    private DocumentArea area;
    private ChromiumPreview surface;
    private FakeMarkdownPreviews.Recording page;

    private static DocumentArea.PreviewStyle style(Variant variant) {
        return new DocumentArea.PreviewStyle(variant, PreviewPaneTest.TYPE, PreviewPaneTest.palette(variant == Variant.DARK), EditorThemeTest.syntax(1));
    }

    @BeforeEach void make() {
        area = new DocumentArea(null);
        area.setFont(new Font("Serif", Font.PLAIN, 16));
        area.previewCallbacks(links::add, geometry::incrementAndGet);
        surface = new ChromiumPreviews(previews).create(area);
        page = previews.last();
    }

    @AfterEach void close() { surface.close(); area.close(); }

    /** Renders {@code text} as the render worker does, in the surface's dialect. */
    private PreviewSurface.Prepared prepared(String text) { return surface.prepare(text, EditorThemeTest.syntax(1)); }

    private MarkdownResult installed(String text) {
        var prepared = prepared(text);
        surface.install(prepared, style(Variant.LIGHT), area.getFont(), 1);
        page.fire(new PreviewEvent.Shown(page.calls().stream().filter(call -> call.startsWith("show ")).count()));
        assertThat(surface.installedResult()).containsSame(prepared.result());
        return prepared.result();
    }

    @Test void theFirstInstallSendsTheThemeThenTheDocumentAndALaterOneOnlyTheDocument() {
        assertThat(surface.dialect()).isEqualTo(MarkdownRenderer.Dialect.WEB);
        var first = prepared(TEXT);
        assertThat(first.swing()).as("Swing parses nothing for the WEB dialect").isNull();
        surface.install(first, style(Variant.LIGHT), area.getFont(), .5);
        assertThat(page.calls()).containsExactly("theme", "show 1");
        assertThat(page.themes()).containsExactly(new PreviewTheme(Variant.LIGHT, PreviewPaneTest.palette(false), EditorThemeTest.syntax(1),
            "Serif", PreviewPaneTest.TYPE));
        assertThat(page.document(1)).isEqualTo(first.result().html()).contains("data-b");
        assertThat(surface.installing()).isTrue();
        assertThat(surface.installedResult()).isEmpty();
        assertThat(surface.installFailure()).isEmpty();
        surface.install(prepared(TEXT + "\nMore.\n"), style(Variant.LIGHT), area.getFont(), 1);
        assertThat(page.calls()).as("the same style: the document only").containsExactly("theme", "show 1", "show 2");
    }

    @Test void restyleAndANewBodyFontSendTheThemeOnly() {
        surface.bodyFont(new Font("Dialog", Font.PLAIN, 12));
        assertThat(page.calls()).as("no style yet").isEmpty();
        installed(TEXT);
        surface.restyle(style(Variant.LIGHT), area.getFont(), .4);
        assertThat(page.calls()).as("the opacity alone changes nothing: the preview is opaque").containsExactly("theme", "show 1");
        surface.restyle(style(Variant.DARK), area.getFont(), 1);
        surface.bodyFont(new Font("Dialog", Font.PLAIN, 18));
        assertThat(page.calls()).containsExactly("theme", "show 1", "theme", "theme");
        assertThat(page.themes().getLast()).isEqualTo(new PreviewTheme(Variant.DARK, PreviewPaneTest.palette(true), EditorThemeTest.syntax(1),
            "Dialog", PreviewPaneTest.TYPE));
    }

    @Test void aStaleShownDoesNotInstall() {
        var installs = new AtomicInteger();
        var replaced = new AtomicInteger();
        surface.onInstalled(installs::incrementAndGet);
        surface.beforeReplace(replaced::incrementAndGet);
        var older = prepared(TEXT);
        var newer = prepared(TEXT + "\nNewer.\n");
        surface.install(older, style(Variant.LIGHT), area.getFont(), 1);
        surface.install(newer, style(Variant.LIGHT), area.getFont(), 1);
        page.fire(new PreviewEvent.Shown(1));
        assertThat(surface.installedResult()).isEmpty();
        assertThat(surface.installing()).isTrue();
        assertThat(installs).hasValue(0);
        assertThat(replaced).hasValue(0);
        assertThat(geometry).as("the area's geometry hook").hasValue(0);
        page.fire(new PreviewEvent.Shown(2));
        assertThat(surface.installedResult()).containsSame(newer.result());
        assertThat(surface.installing()).isFalse();
        assertThat(installs).hasValue(1);
        assertThat(replaced).hasValue(1);
        assertThat(geometry).hasValue(1);
        page.fire(new PreviewEvent.Shown(2));
        assertThat(installs).as("once").hasValue(1);
    }

    @Test void aCancelledInstallIsNotInstalledWhenItsPageShowsIt() {
        var shown = installed(TEXT);
        surface.install(prepared(TEXT + "\nLater.\n"), style(Variant.LIGHT), area.getFont(), 1);
        surface.cancelInstall();
        assertThat(surface.installing()).isFalse();
        page.fire(new PreviewEvent.Shown(2));
        assertThat(surface.installedResult()).containsSame(shown);
        assertThat(geometry).hasValue(1);
    }

    @Test void eachImageGoesToTheImageOrItsReasonAndAClearClearsThem() {
        var picture = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        surface.images(new ResourceSession.Update(true, Map.of()));
        surface.images(new ResourceSession.Update(false, Map.of(0, new ResourceSession.ImageState(picture, ""))));
        assertThat(page.image(0)).isSameAs(picture);
        surface.images(new ResourceSession.Update(false, Map.of(1, new ResourceSession.ImageState(null, "alt: Loading image"))));
        surface.images(new ResourceSession.Update(true, Map.of(2, new ResourceSession.ImageState(null, "Load external images to display"))));
        assertThat(page.calls()).containsExactly("clearImages", "image 0", "imageReason 1 alt: Loading image", "clearImages",
            "imageReason 2 Load external images to display");
        assertThat(page.image(0)).isNull();
    }

    @Test void revealFindsOnlyTheNamesOfTheInstalledDocument() {
        assertThat(surface.reveal("target")).as("nothing installed").isFalse();
        surface.install(prepared(TEXT), style(Variant.LIGHT), area.getFont(), 1);
        assertThat(surface.reveal("target")).as("not shown yet").isFalse();
        page.fire(new PreviewEvent.Shown(1));
        assertThat(surface.reveal("nowhere")).isFalse();
        assertThat(surface.reveal("target")).isTrue();
        assertThat(page.calls()).containsExactly("theme", "show 1", "reveal target");
    }

    @Test void aLinkOfTheShownDocumentReachesTheAreasLinkCallback() {
        surface.install(prepared(TEXT), style(Variant.LIGHT), area.getFont(), 1);
        page.fire(new PreviewEvent.LinkActivated(3));
        assertThat(links).as("nothing installed").isEmpty();
        page.fire(new PreviewEvent.Shown(1));
        assertThat(surface.installedResult().orElseThrow().links()).extracting(MarkdownResult.Link::id).contains(3);
        page.fire(new PreviewEvent.LinkActivated(3));
        page.fire(new PreviewEvent.LinkActivated(9));
        assertThat(links).as("only the document's own links").containsExactly(3);
        surface.install(prepared(TEXT), style(Variant.LIGHT), area.getFont(), 1);
        surface.cancelInstall();
        page.fire(new PreviewEvent.Shown(2));
        page.fire(new PreviewEvent.LinkActivated(2));
        assertThat(links).as("the page shows a document the view does not hold").containsExactly(3);
    }

    @Test void commandsGoToThePageAndItsFocusIsThePreviews() {
        assertThat(surface.focused()).isFalse();
        surface.copy();
        surface.selectAll();
        surface.focus();
        surface.clearFind();
        assertThat(page.calls()).containsExactly("copy", "selectAll", "focus", "clearFind");
        assertThat(surface.component()).isSameAs(page.component());
        assertThat(surface.focused()).as("the canvas's own focus").isTrue();
        area.mode(MarkdownMode.SPLIT);
        area.focusSource(true);
        assertThat(area.previewFocused()).isFalse();
        var canvas = page.component();
        for (var listener : canvas.getFocusListeners()) listener.focusGained(new FocusEvent(canvas, FocusEvent.FOCUS_GAINED));
        assertThat(area.previewFocused()).as("the view's commands go to the preview it focused last").isTrue();
    }

    /** Task 7 replaces these stand-ins; FindBar joins their futures on the UI thread, so they must be complete. */
    @Test void theFindStandInsAnswerAtOnce() {
        var context = new SearchContext("Target");
        assertThat(surface.markAll(context)).isCompletedWithValue(0);
        assertThat(surface.find(context)).isCompletedWithValue(false);
        assertThat(surface.selectedText()).isCompletedWithValue("");
        assertThat(surface.blockY("b0")).isEmpty();
        assertThat(surface.endY()).isNaN();
        assertThat(surface.scroller().max()).isZero();
    }

    /**
     * The page may report it from inside the layout that adds its canvas, as one whose client already failed does when it
     * first shows: the view falls back to Basic once that layout is done, so it is told on a later turn (fix round 1, E1).
     */
    @Test void unavailableIsToldOnALaterTurnUnlessThePreviewClosedMeanwhile() {
        var told = new AtomicInteger();
        surface.onUnavailable(told::incrementAndGet);
        page.fire(new PreviewEvent.Unavailable("Chromium stopped"));
        assertThat(told).as("not from inside the page's report").hasValue(0);
        nextTurn();
        assertThat(told).hasValue(1);
        var other = new ChromiumPreviews(previews).create(area);
        var closedTold = new AtomicInteger();
        other.onUnavailable(closedTold::incrementAndGet);
        previews.last().fire(new PreviewEvent.Unavailable("Chromium stopped"));
        other.close();
        nextTurn();
        assertThat(closedTold).as("a preview closed since").hasValue(0);
    }

    /** The surface the view draws on next takes the focus the one it leaves had; the closed one is not asked for it. */
    @Test void theSurfaceDrawnOnNextTakesTheFocusTheOldOneHad() {
        area.previewEngine(surface);
        surface.focus();
        var next = new ChromiumPreviews(previews).create(area);
        area.previewEngine(next);
        assertThat(page.closed()).isTrue();
        assertThat(page.calls()).as("nothing after its close").containsExactly("focus", "close");
        assertThat(previews.last().calls()).containsExactly("focus");
        assertThat(area.surface()).isSameAs(next);
        area.previewEngine(area.basic());
        assertThat(previews.last().calls()).containsExactly("focus", "close");
    }

    /** Runs what the UI thread has queued, from the test's own turn on it. */
    private static void nextTurn() {
        var loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        java.awt.EventQueue.invokeLater(loop::exit);
        assertThat(loop.enter()).isTrue();
    }

    @Test void closeEndsThePage() {
        surface.close();
        surface.close();
        assertThat(surface.closed()).isTrue();
        assertThat(page.closed()).isTrue();
        assertThat(page.component().getFocusListeners()).as("the canvas forgets the view").isEmpty();
        surface.install(prepared(TEXT), style(Variant.LIGHT), area.getFont(), 1);
        assertThat(page.calls()).containsExactly("close");
    }
}
