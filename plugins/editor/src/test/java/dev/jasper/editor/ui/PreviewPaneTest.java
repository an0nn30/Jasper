package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.*;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PreviewPaneTest {
    static final TerminalTypography TYPE = new TerminalTypography("Monospaced", 16, List.of("Serif"), true, 1.3);
    static WorkspacePalette palette(boolean dark) {
        Color bg=dark?new Color(32,36,40):new Color(246,244,238), fg=dark?new Color(230,232,235):new Color(25,30,35);
        return new WorkspacePalette(bg,bg.brighter(),bg.darker(),fg,Color.GRAY,Color.GRAY,Color.BLUE,Color.WHITE,
            Color.GRAY,dark?Color.CYAN:Color.BLUE,Color.GREEN,Color.ORANGE,Color.RED);
    }
    static PreviewPane.Prepared prepare(String source) { return PreviewPane.prepare(new MarkdownRenderer().render(source, EditorThemeTest.syntax(0x668800))); }
    static PreviewPane install(PreviewPane.Prepared prepared) throws Exception {
        PreviewPane[] pane={null};
        SwingUtilities.invokeAndWait(() -> {pane[0]=new PreviewPane(id -> {}, () -> {}); pane[0].install(prepared,TYPE,palette(false),EditorThemeTest.syntax(0x668800),new Font("Serif",0,12),1);});
        await(pane[0],prepared.result()); return pane[0];
    }
    static void await(PreviewPane pane, MarkdownResult result) throws Exception {
        long end=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<end) {
            boolean[] done={false}; SwingUtilities.invokeAndWait(() -> done[0]=!pane.installing() && pane.installedResult().orElse(null)==result);
            if(done[0]) return; Thread.sleep(5);
        }
        fail("Preview did not attach");
    }
    @Test void preparationObservesWorkerCancellationAndCannotRunOnEdt() throws Exception {
        var result=new MarkdownRenderer().render("text",EditorThemeTest.syntax(1));
        Thread.currentThread().interrupt();
        try{assertThatThrownBy(() -> PreviewPane.prepare(result)).isInstanceOf(java.util.concurrent.CancellationException.class);}
        finally{Thread.interrupted();}
        SwingUtilities.invokeAndWait(() -> assertThatThrownBy(() -> PreviewPane.prepare(result)).isInstanceOf(IllegalStateException.class));
    }
    @Test void actualDocumentHasInvisibleAnchorsSelectionAndMetadataOnlyLinks() throws Exception {
        var prepared=prepare("# Title\n\nHello **world** [link](https://example.com).\n\n`code`\n");
        var pane=install(prepared);
        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.editor().isEditable()).isFalse();
            assertThat(pane.anchor("b0")).isPresent();
            assertThat(pane.anchor("title")).isPresent();
            pane.editor().selectAll();
            assertThat(pane.editor().getSelectedText()).contains("Title", "Hello world", "code").doesNotContain("b0", "jasper-link");
            Clipboard clipboard=new Clipboard("test");
            pane.editor().getTransferHandler().exportToClipboard(pane.editor(),clipboard,TransferHandler.COPY);
            try { assertThat(clipboard.getData(DataFlavor.stringFlavor).toString()).contains("Hello world"); } catch(Exception e) {throw new AssertionError(e);}
            pane.close();
        });
    }
    @Test void obsoleteAndCancelledBuildNeverReplaceInstalledDocument() throws Exception {
        var first=prepare("First");var large=prepare("paragraph\n\n".repeat(2000));var last=prepare("Last");
        var pane=install(first);
        SwingUtilities.invokeAndWait(() -> {
            var original=pane.editor().getDocument();
            pane.install(large,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1);
            assertThat(pane.editor().getDocument()).isSameAs(original);
        });
        SwingUtilities.invokeAndWait(pane::cancelInstall);
        SwingUtilities.invokeAndWait(() -> assertThat(pane.installedResult()).contains(first.result()));
        SwingUtilities.invokeAndWait(() -> pane.install(last,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1));
        await(pane,last.result()); SwingUtilities.invokeAndWait(pane::close);
    }
    @Test void imageAndLinkMarkupCannotTriggerHttpOrFileReads() throws Exception {
        var requests=new AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", e -> {requests.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/image.png";
            var result=new MarkdownResult("<html><body><img src=\""+url+"\"><img src=\"file:///etc/passwd\"><img src=\"jasper-image:99\"><a href=\""+url+"\">link</a></body></html>",List.of(),Map.of(),List.of(),List.of());
            var pane=install(PreviewPane.prepare(result));
            SwingUtilities.invokeAndWait(() -> {pane.editor().setSize(300,300);pane.editor().paint(new BufferedImage(300,300,2).getGraphics()); assertThat(allViews(pane.editor().getUI().getRootView(pane.editor()))).noneMatch(v -> v instanceof javax.swing.text.html.ImageView);pane.close();});
            assertThat(requests).hasValue(0);
        } finally {server.stop(0);}
    }
    static List<javax.swing.text.View> allViews(javax.swing.text.View root) {
        var result=new java.util.ArrayList<javax.swing.text.View>();result.add(root);
        for(int i=0;i<root.getViewCount();i++)result.addAll(allViews(root.getView(i)));return result;
    }
    @Test void failedAndClosedInstallationsKeepThePreviousDocumentAndSuppressStaleCallbacks() throws Exception {
        var initial=prepare("Keep this");var replacement=prepare("Replacement");var pane=install(initial);
        SwingUtilities.invokeAndWait(() -> {
            var broken=new Font("Serif",0,12){@Override public Font deriveFont(float size){throw new IllegalStateException("font failure");}};
            pane.install(replacement,TYPE,palette(false),EditorThemeTest.syntax(1),broken,1);
        });
        for(int i=0;i<10;i++)SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.installFailure()).isPresent();assertThat(pane.installedResult()).contains(initial.result());
            pane.editor().selectAll();assertThat(pane.editor().getSelectedText()).contains("Keep this");
            pane.install(replacement,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1);
            pane.close();
        });
        SwingUtilities.invokeAndWait(() -> {assertThat(pane.installedResult()).isEmpty();assertThat(pane.installFailure()).isEmpty();});
    }
    @Test void onlyKnownLinkIdsReachTheController() throws Exception {
        var prepared=prepare("[known](https://example.com)");var activated=new java.util.ArrayList<Integer>();PreviewPane[] holder={null};
        SwingUtilities.invokeAndWait(() -> {holder[0]=new PreviewPane(activated::add,() -> {});holder[0].install(prepared,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1);});
        var pane=holder[0];await(pane,prepared.result());
        SwingUtilities.invokeAndWait(() -> {
            for(String id:List.of("jasper-link:0","jasper-link:8","https://example.com"))for(var listener:pane.editor().getHyperlinkListeners())listener.hyperlinkUpdate(new HyperlinkEvent(pane.editor(),HyperlinkEvent.EventType.ACTIVATED,null,id));
            assertThat(activated).containsExactly(0);pane.close();
        });
    }
    @Test void liveMountedBodyFontChangesActualProseWithoutChangingSourceOrCode() throws Exception {
        var prepared=prepare("Body text and `ffi code`");var pane=install(prepared);
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(500,300);editor.paint(new BufferedImage(500,300,2).getGraphics());
            editor.select(1,5);var document=editor.getDocument();
            pane.bodyFont(new Font("SansSerif",0,22));editor.paint(new BufferedImage(500,300,2).getGraphics());
            var glyphs=allViews(editor.getUI().getRootView(editor)).stream().filter(javax.swing.text.GlyphView.class::isInstance).map(javax.swing.text.GlyphView.class::cast).toList();
            assertThat(glyphs).anyMatch(v -> v.getFont().getFamily().equals("SansSerif"));
            assertThat(glyphs).anyMatch(v -> v.getFont().getFamily().equals("Monospaced"));
            assertThat(editor.getDocument()).isSameAs(document);assertThat(editor.getSelectionStart()).isEqualTo(1);assertThat(editor.getSelectionEnd()).isEqualTo(5);pane.close();
        });
    }
    @Test void longCodeAndTableScrollWhileProseWrapsAndImagesFitTheViewport() throws Exception {
        var prepared=prepare("ordinary prose ".repeat(30)+"\n\n```\n"+"long_code_".repeat(70)+"\n```\n\n| wide | table |\n| --- | --- |\n| "+"wide".repeat(100)+" | cell |\n\n![alt](x.png)");
        var pane=install(prepared);
        SwingUtilities.invokeAndWait(() -> {
            var scroll=new JScrollPane(pane.editor());scroll.setSize(320,400);
            for(int i=0;i<4;i++){scroll.doLayout();scroll.getViewport().doLayout();pane.editor().doLayout();}
            pane.image(0,new BufferedImage(2000,1000,2));
            var views=allViews(pane.editor().getUI().getRootView(pane.editor()));
            assertThat(scroll.getHorizontalScrollBar().isVisible()).isTrue();
            assertThat(pane.editor().getWidth()).isGreaterThan(scroll.getViewport().getWidth());
            assertThat(views.stream().filter(PreviewImageView.class::isInstance).findFirst().orElseThrow().getPreferredSpan(0)).isLessThanOrEqualTo(scroll.getViewport().getWidth());
            try {var start=pane.editor().modelToView2D(1);var later=pane.editor().modelToView2D(100);assertThat(later.getY()).isGreaterThan(start.getY());assertThat(later.getX()).isLessThan(320);}catch(Exception e){throw new AssertionError(e);}
            pane.close();
        });
    }
    @Test void codeLineHeightAppliesTheEffectiveMultiplierOnce() throws Exception {
        var pane=install(prepare("```\none\ntwo\n```"));
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(400,300);editor.paint(new BufferedImage(400,300,2).getGraphics());
            var fonts=new dev.jasper.editor.textview.EditorFonts(TYPE,new java.awt.font.FontRenderContext(null,true,true));
            try{String text=editor.getDocument().getText(0,editor.getDocument().getLength());
                double advance=editor.modelToView2D(text.indexOf("two")).getY()-editor.modelToView2D(text.indexOf("one")).getY();
                assertThat(advance).isCloseTo(fonts.height(),within(1.0));
            }catch(Exception e){throw new AssertionError(e);}finally{pane.close();}
        });
    }
    @Test void imageCompletionStillReflowsAfterLiveFontRestyling() throws Exception {
        var pane=install(prepare("Text\n\n![image](x.png)"));
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(320,600);editor.paint(new BufferedImage(320,600,2).getGraphics());
            pane.bodyFont(new Font("SansSerif",0,20));editor.paint(new BufferedImage(320,600,2).getGraphics());
            int before=editor.getPreferredSize().height;pane.image(0,new BufferedImage(600,600,2));
            assertThat(editor.getPreferredSize().height).isGreaterThan(before+100);pane.close();
        });
    }
    @Test void boundedInstallationYieldsAndMeasuresActualEdtWork() throws Exception {
        String source=("## Heading\n\nA paragraph with **bold** and `code`.\n\n").repeat(3000);
        var prepared=prepare(source);var pane=install(prepare("old content"));
        var dispatches=new java.util.concurrent.CopyOnWriteArrayList<Long>();
        class MeasuringQueue extends EventQueue {
            @Override protected void dispatchEvent(AWTEvent event){long start=System.nanoTime();try{super.dispatchEvent(event);}finally{dispatches.add(System.nanoTime()-start);}}
            void remove(){pop();}
        }
        var queue=new MeasuringQueue();Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue);
        try {
            SwingUtilities.invokeAndWait(() -> pane.install(prepared,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1));
            AtomicInteger checkpoints=new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {assertThat(pane.installing()).isTrue();checkpoints.incrementAndGet();});
            await(pane,prepared.result());
            assertThat(checkpoints).hasValue(1);assertThat(dispatches.size()).isGreaterThan(10);
            System.out.printf("Preview install: source=%,d UTF16, html=%,d chars, EDT dispatches=%d, max=%.3f ms%n",source.length(),prepared.result().html().length(),dispatches.size(),dispatches.stream().mapToLong(Long::longValue).max().orElse(0)/1e6);
        }finally{SwingUtilities.invokeAndWait(() -> {pane.close();queue.remove();});}
    }
    @Test void modesKeepSourceDocumentUndoCaretAndSplitRatio() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var area=new DocumentArea();area.setSize(900,500);area.doLayout();area.area.setText("first");area.area.append(" second");area.area.setCaretPosition(4);
            var doc=area.area.getDocument();area.mode(MarkdownMode.SPLIT);area.doLayout();
            var split=(JSplitPane)area.getComponent(0);split.setDividerLocation(300);double ratio=area.dividerRatio();
            area.mode(MarkdownMode.PREVIEW);area.setSize(1200,500);area.mode(MarkdownMode.SPLIT);area.doLayout();
            assertThat(area.area.getDocument()).isSameAs(doc);assertThat(area.area.getCaretPosition()).isEqualTo(4);
            assertThat(area.dividerRatio()).isCloseTo(ratio,within(.001));area.area.undoLastAction();assertThat(area.area.getText()).isEqualTo("first");area.close();
        });
    }
}
