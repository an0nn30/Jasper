package dev.jasper.editor.ui;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.editor.testsupport.FakeMarkdownPreviews;
import dev.jasper.sdk.Variant;
import java.awt.Font;
import java.awt.Point;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.editor.testsupport.PreviewFixture.layout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Scroll sync between a real source area and the Chromium preview, in Split with Synchronize scrolling on. A recording
 * preview stands in for Chromium's page: its Geometry says where the document's blocks are, and its Scrolled events, the
 * user's or the echo of Jasper's own scroll, say where it shows them (Review Focus 4).
 */
class ScrollLinkChromiumTest {
    private static final int BLOCKS = 30, STEP = 100, VIEWPORT = 140, HEIGHT = BLOCKS * STEP + 200;
    /** One short paragraph per block, but block 5, which spans five lines of the source. */
    private static final String TEXT = IntStream.range(0, BLOCKS)
        .mapToObj(n -> n == 5 ? "paragraph 5\nline\nline\nline\nline\n\n" : "paragraph " + n + "\n\n").collect(Collectors.joining());
    private final FakeMarkdownPreviews previews = new FakeMarkdownPreviews();
    private DocumentArea area;
    private ChromiumPreview surface;
    private FakeMarkdownPreviews.Recording page;
    private PreviewSurface.Prepared prepared;

    static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    /** Runs the turns scroll sync queues to rebuild its correspondence. */
    static void drain() throws Exception { for (int i = 0; i < 8; i++) edt(() -> { }); }

    /** Each block's top in the page: {@code STEP} pixels apart. */
    private static double[] tops() { return IntStream.range(0, BLOCKS).mapToDouble(n -> n * STEP).toArray(); }

    @BeforeEach void open() throws Exception {
        edt(() -> {
            area = new DocumentArea(null);
            area.setFont(new Font("Serif", Font.PLAIN, 16));
            area.area.typography(PreviewPaneTest.TYPE);
            area.area.setText(TEXT);
            area.area.setCaretPosition(0);
            area.area.setLineWrap(true);
            surface = new ChromiumPreviews(previews).create(area);
            page = previews.last();
            area.previewEngine(surface);
            area.mode(MarkdownMode.SPLIT);
            layout(area.scroll, 240, VIEWPORT);
        });
        // The render worker's part, here on the test thread.
        prepared = surface.prepare(TEXT, EditorThemeTest.syntax(1));
        assertThat(prepared.result().blocks()).hasSize(BLOCKS);
    }

    @AfterEach void close() throws Exception { edt(area::close); }

    /** Sends the document to the page; the install waits for the page to say it shows it. */
    private void send() {
        surface.install(prepared, new DocumentArea.PreviewStyle(Variant.LIGHT, PreviewPaneTest.TYPE, PreviewPaneTest.palette(false),
            EditorThemeTest.syntax(1)), area.getFont(), 1);
    }

    /** The page shows the document, which ends the install, and scroll sync takes its blocks, as the controller has it. */
    private void shown() {
        page.fire(new PreviewEvent.Shown(1));
        area.scrollLink.install(1, prepared.result());
    }

    /** Installs the document and lays it out in the page with {@code tops}. */
    private void installed(double[] tops) throws Exception {
        edt(() -> { send(); shown(); });
        drain();
        edt(() -> geometry(tops, HEIGHT));
        drain();
    }

    private void geometry(double[] tops, double height) { page.fire(new PreviewEvent.Geometry(1, tops, height, VIEWPORT)); }
    private void user(double y) { page.fire(new PreviewEvent.Scrolled(y, true)); }
    private void echo(double y) { page.fire(new PreviewEvent.Scrolled(y, false)); }

    /** @return the top of block {@code n}'s first line in the source */
    private int sourceY(int n) {
        try { return (int) area.area.modelToView2D(prepared.result().blocks().get(n).sourceStart()).getY(); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }
    private int source() { return area.scroll.getViewport().getViewPosition().y; }
    private void source(int y) { area.scroll.getViewport().setViewPosition(new Point(0, y)); }
    private int sourceMax() {
        var viewport = area.scroll.getViewport();
        return viewport.getViewSize().height - viewport.getExtentSize().height;
    }
    /** @return where scroll sync last asked the page to scroll */
    private String lastScroll() {
        return page.calls().stream().filter(call -> call.startsWith("scrollTo ")).reduce((first, second) -> second).orElse("none");
    }

    @Test void aGeometryBeforeInstallChangesNothing() throws Exception {
        double[] tops = tops();
        edt(() -> {
            send();
            geometry(tops, HEIGHT);
            assertThat(surface.blockY("b12")).as("not the installed document's").isEmpty();
            assertThat(surface.endY()).isNaN();
            assertThat(surface.scroller().max()).isZero();
            shown();
        });
        drain();
        edt(() -> {
            assertThat(surface.blockY("b12")).as("dropped, not kept for the install").isEmpty();
            user(tops[12]);
            assertThat(source()).isZero();
        });
    }

    @Test void afterInstallAUserScrollToABlocksTopMovesTheSourceToThatBlocksLine() throws Exception {
        double[] tops = tops();
        installed(tops);
        edt(() -> {
            user(tops[12]);
            assertThat(source()).isCloseTo(sourceY(12), within(2));
            user(tops[20]);
            assertThat(source()).isCloseTo(sourceY(20), within(2));
        });
    }

    @Test void anEchoNeverMovesTheSource() throws Exception {
        double[] tops = tops();
        installed(tops);
        edt(() -> {
            echo(tops[12]);
            assertThat(source()).as("a scroll Jasper asked for is not the user's").isZero();
            source(sourceY(8));
            assertThat(lastScroll()).isEqualTo("scrollTo " + tops[8]);
            echo(tops[8]);
            // Where the page settled, a pixel or many away, is still Jasper's own scroll.
            echo(tops[8] + 37);
            assertThat(source()).isEqualTo(sourceY(8));
            user(tops[15]);
            assertThat(source()).as("the user's next scroll leads").isCloseTo(sourceY(15), within(2));
        });
    }

    @Test void aLaterGeometryWithATallerDiagramReAlignsTheFollower() throws Exception {
        double[] tops = tops();
        installed(tops);
        edt(() -> {
            source(sourceY(12));
            assertThat(lastScroll()).isEqualTo("scrollTo " + tops[12]);
        });
        // Block 2, a diagram, is drawn 250 pixels taller: every block after it moves down.
        double[] taller = tops.clone();
        for (int n = 3; n < BLOCKS; n++) taller[n] += 250;
        edt(() -> geometry(taller, HEIGHT + 250));
        drain();
        edt(() -> {
            assertThat(lastScroll()).isEqualTo("scrollTo " + taller[12]);
            assertThat(source()).isCloseTo(sourceY(12), within(2));
        });
    }

    @Test void aTallerDiagramKeepsThePassageWhenThePreviewLeads() throws Exception {
        double[] tops = tops();
        installed(tops);
        edt(() -> {
            user(tops[12]);
            assertThat(source()).isCloseTo(sourceY(12), within(2));
        });
        double[] taller = tops.clone();
        for (int n = 3; n < BLOCKS; n++) taller[n] += 250;
        edt(() -> geometry(taller, HEIGHT + 250));
        drain();
        edt(() -> {
            assertThat(lastScroll()).as("the preview keeps showing block 12").isEqualTo("scrollTo " + taller[12]);
            assertThat(source()).as("and the source follows that passage, not the old y").isCloseTo(sourceY(12), within(2));
        });
    }

    @Test void aBlockWithNoTopIsSkipped() throws Exception {
        double[] tops = tops();
        tops[5] = Double.NaN;
        installed(tops);
        edt(() -> {
            assertThat(surface.blockY("b5")).isEmpty();
            user((tops[4] + tops[6]) / 2);
            assertThat(source()).as("halfway between the blocks either side").isCloseTo((sourceY(4) + sourceY(6)) / 2, within(2));
            assertThat(Math.abs(source() - sourceY(5))).as("not at the skipped block, which spans more lines").isGreaterThan(10);
            user(tops[8]);
            assertThat(source()).isCloseTo(sourceY(8), within(2));
        });
    }

    @Test void thePreviewsBottomBringsTheSourceToItsBottom() throws Exception {
        installed(tops());
        edt(() -> {
            assertThat(surface.scroller().max()).isEqualTo(HEIGHT - VIEWPORT);
            user(HEIGHT - VIEWPORT);
            assertThat(sourceMax()).isPositive();
            assertThat(source()).isEqualTo(sourceMax());
        });
    }
}
