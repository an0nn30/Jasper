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
    @Test void aWrappedLargeHeadingReservesBothRowsBeforeFollowingProse() throws Exception {
        for(String sample:List.of(PreviewRenderTest.SAMPLE,PreviewRenderTest.SAMPLE.replace("日本語 é","ASCII"),
            "# Native preview\n\nProse after the heading.\n\n```\n"+"wide ".repeat(100)+"\n```\n")) {
        var prepared=prepare(sample);var pane=install(prepared);
        SwingUtilities.invokeAndWait(() -> pane.install(prepared,new TerminalTypography("Monospaced",28,List.of("Serif"),true,1.3),palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1));
        await(pane,prepared.result());
        SwingUtilities.invokeAndWait(() -> {
            try {
                var editor=pane.editor();var scroll=new JScrollPane(editor);scroll.setSize(300,1000);
                for(int pass=0;pass<4;pass++){scroll.doLayout();scroll.getViewport().doLayout();editor.doLayout();}
                var image=new BufferedImage(300,1000,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();scroll.paint(g);g.dispose();
                String text=editor.getDocument().getText(0,editor.getDocument().getLength());
                var lastHeading=editor.modelToView2D(text.indexOf("preview")+6);
                var prose=editor.modelToView2D(text.indexOf("Prose"));
                assertThat(prose.getY()).as("following prose must clear the wrapped heading").isGreaterThanOrEqualTo(lastHeading.getMaxY());
            }catch(javax.swing.text.BadLocationException failure){throw new AssertionError(failure);}
            finally {pane.close();}
        });
        }
    }
    @Test void boundedFlowCandidatesKeepNativeBreaksWithoutMeasuringTheWholeRemainingLeaf() throws Exception {
        var pane=install(prepare("a_long_unbroken_word_".repeat(100)+" **bold text** "+"word ".repeat(100)));
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(320,300);editor.getPreferredSize();
            var paragraph=(javax.swing.text.FlowView)allViews(editor.getUI().getRootView(editor)).stream()
                .filter(v -> v instanceof javax.swing.text.FlowView && v.getEndOffset()-v.getStartOffset()>100).findFirst().orElseThrow();
            class NativeFlow extends javax.swing.text.FlowView.FlowStrategy {
                javax.swing.text.View candidate(int offset,int span){return super.createView(paragraph,offset,span,0);}
            }
            var nativeFlow=new NativeFlow();var bounded=new PreviewKit.BoundedFlow();
            for(int offset:new int[]{paragraph.getStartOffset(),paragraph.getStartOffset()+500})for(int width:new int[]{1,50,200}) {
                var expected=nativeFlow.candidate(offset,width);var actual=bounded.createView(paragraph,offset,width,0);
                float x=paragraph.getFlowStart(0)+paragraph.getFlowSpan(0)-width;
                if(width>1)assertThat(actual.getEndOffset()-actual.getStartOffset()).isLessThan(100);
                else assertThat(actual.getEndOffset()).isEqualTo(expected.getEndOffset());
                assertThat(actual.getBreakWeight(0,x,width)).isEqualTo(expected.getBreakWeight(0,x,width));
                assertThat(actual.breakView(0,offset,x,width).getEndOffset()).isEqualTo(expected.breakView(0,offset,x,width).getEndOffset());
            }
            pane.close();
        });
    }
    @Test void denseAsciiInlineBreaksMatchNativeAndShareOneParagraphIndex() throws Exception {
        var pane=install(prepare("alpha **bold words** *italic words* `code` tail ".repeat(30)));
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(320,300);editor.getPreferredSize();
            for(var view:allViews(editor.getUI().getRootView(editor))) if(view instanceof javax.swing.text.html.InlineView inline
                && !view.getElement().getAttributes().isDefined(javax.swing.text.html.HTML.Tag.CODE)) {
                var original=new javax.swing.text.html.InlineView(view.getElement());original.setParent(view.getParent());
                var nativeView=(javax.swing.text.GlyphView)original.createFragment(view.getStartOffset(),view.getEndOffset());
                assertThat(inline.getMinimumSpan(0)).isCloseTo(nativeView.getMinimumSpan(0),within(.01f));
                for(float width:new float[]{1,20,60,500}) {
                    assertThat(inline.getBreakWeight(0,13,width)).isEqualTo(nativeView.getBreakWeight(0,13,width));
                    var actual=inline.breakView(0,inline.getStartOffset(),13,width);
                    var expected=nativeView.breakView(0,nativeView.getStartOffset(),13,width);
                    assertThat(actual.getEndOffset()).isEqualTo(expected.getEndOffset());
                }
            }
            try {var field=PreviewKit.class.getDeclaredField("paragraphBreaks");field.setAccessible(true);
                assertThat(((java.util.Map<?,?>)field.get(editor.getEditorKit())).size()).isBetween(1,2);}
            catch(ReflectiveOperationException e){throw new AssertionError(e);}
            pane.close();
        });
    }
    @Test void bulkImagesAndReasonsProduceOneGeometryNotificationPerTurn() throws Exception {
        var prepared=prepare("![alt](relative.png) ".repeat(1024));var geometry=new AtomicInteger();var ref=new java.util.concurrent.atomic.AtomicReference<PreviewPane>();
        SwingUtilities.invokeAndWait(()->{var pane=new PreviewPane(id->{},geometry::incrementAndGet);ref.set(pane);pane.install(prepared,TYPE,palette(false),EditorThemeTest.syntax(1),new Font("Serif",0,12),1);});
        await(ref.get(),prepared.result());SwingUtilities.invokeAndWait(()->{});geometry.set(0);
        SwingUtilities.invokeAndWait(()->{var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
            for(int id=0;id<1024;id++){ref.get().imageReason(id,"Loading");ref.get().image(id,image);}
            assertThat(geometry.get()).isZero();});
        SwingUtilities.invokeAndWait(()->assertThat(geometry.get()).isEqualTo(1));
        geometry.set(0);SwingUtilities.invokeAndWait(()->{for(int id=0;id<1024;id++)ref.get().image(id,null);});
        SwingUtilities.invokeAndWait(()->{assertThat(geometry.get()).isEqualTo(1);ref.get().close();});
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
    @Test void traversalExitsWithNoLinksAndAtBothEndsOfLinkSequence() throws Exception {
        var empty=install(prepare("No links here"));
        var linked=install(prepare("[first](https://example.com/1) and [last](https://example.com/2)"));
        SwingUtilities.invokeAndWait(() -> {
            var original=KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var transfers=new java.util.ArrayList<String>();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                @Override public void focusNextComponent(Component component) {transfers.add("next");}
                @Override public void focusPreviousComponent(Component component) {transfers.add("previous");}
            });
            try {
                linkAction(empty,"next");linkAction(empty,"previous");
                assertThat(transfers).containsExactly("next","previous");transfers.clear();
                linked.editor().setCaretPosition(0);
                linkAction(linked,"next");assertThat(linked.editor().getSelectedText()).isEqualTo("first");
                linkAction(linked,"previous");assertThat(transfers).containsExactly("previous");transfers.clear();
                linkAction(linked,"next");assertThat(linked.editor().getSelectedText()).isEqualTo("last");
                linkAction(linked,"next");assertThat(transfers).containsExactly("next");
                linkAction(linked,"previous");assertThat(linked.editor().getSelectedText()).isEqualTo("first");
            } finally {KeyboardFocusManager.setCurrentKeyboardFocusManager(original);empty.close();linked.close();}
        });
    }
    private static void linkAction(PreviewPane pane,String direction) {
        pane.editor().getActionMap().get("preview."+direction+"-link").actionPerformed(
            new java.awt.event.ActionEvent(pane.editor(),java.awt.event.ActionEvent.ACTION_PERFORMED,""));
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
    @Test void highlightedTabsShareLineStopsForPaintingCaretsAndPointerHits() throws Exception {
        var pane=install(prepare("```java\nint\tfoo;\nif\tfoo;\nx\tfoo;\nreturn\tfoo;\nint x=1+2+3;\tfoo;\n```"));
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(500,260);
            var image=new BufferedImage(500,260,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();editor.paint(graphics);graphics.dispose();
            var fonts=new dev.jasper.editor.textview.EditorFonts(TYPE,new java.awt.font.FontRenderContext(null,true,true));
            try {
                var directory=java.nio.file.Path.of("build/preview-matrix");java.nio.file.Files.createDirectories(directory);
                javax.imageio.ImageIO.write(image,"png",directory.resolve("highlighted-tabs.png").toFile());
                String text=editor.getDocument().getText(0,editor.getDocument().getLength());
                for(String prefix:List.of("int","if","x","return","int x=1+2+3;")) {
                    int start=text.indexOf(prefix+"\t"),target=start+prefix.length()+1;
                    var beginning=editor.modelToView2D(start);var caret=editor.modelToView2D(target);
                    float expected=fonts.spaceAdvance()*((prefix.length()/4+1)*4);
                    assertThat(caret.getX()-beginning.getX()).as("tab after %s",prefix).isCloseTo(expected,within(1.5));
                    int hit=editor.viewToModel2D(new java.awt.geom.Point2D.Double(caret.getX()+.1,caret.getCenterY()));
                    assertThat(hit).as("pointer at foo after %s",prefix).isEqualTo(target);
                    var glyph=allViews(editor.getUI().getRootView(editor)).stream().filter(javax.swing.text.GlyphView.class::isInstance)
                        .map(javax.swing.text.GlyphView.class::cast).filter(v -> v.getStartOffset()<=target&&v.getEndOffset()>target).findFirst().orElseThrow();
                    int minInkX=image.getWidth();
                    for(int y=(int)caret.getY();y<Math.min(image.getHeight(),(int)caret.getMaxY());y++)
                        for(int x=(int)caret.getX();x<Math.min(image.getWidth(),(int)caret.getX()+16);x++)
                            if((image.getRGB(x,y)&0xffffff)==(glyph.getForeground().getRGB()&0xffffff))minInkX=Math.min(minInkX,x);
                    assertThat(minInkX).as("painted foo after %s",prefix).isBetween((int)caret.getX(),(int)caret.getX()+8);
                }
            }catch(Exception failure){throw new AssertionError(failure);}finally{pane.close();}
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
        var pane=install(prepare("Text\n\n![image](x.png)"));var before=new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            var editor=pane.editor();editor.setSize(320,600);editor.paint(new BufferedImage(320,600,2).getGraphics());
            pane.bodyFont(new Font("SansSerif",0,20));editor.paint(new BufferedImage(320,600,2).getGraphics());
            before.set(editor.getPreferredSize().height);pane.image(0,new BufferedImage(600,600,2));
        });
        SwingUtilities.invokeAndWait(() -> {assertThat(pane.editor().getPreferredSize().height).isGreaterThan(before.get()+100);pane.close();});
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
            var area=new DocumentArea(null);area.setSize(900,500);area.doLayout();area.area.setText("first");area.area.append(" second");area.area.setCaretPosition(4);
            var doc=area.area.getDocument();area.mode(MarkdownMode.SPLIT);area.doLayout();
            var split=(JSplitPane)area.getComponent(0);split.setDividerLocation(300);double ratio=area.dividerRatio();
            area.mode(MarkdownMode.PREVIEW);area.setSize(1200,500);area.mode(MarkdownMode.SPLIT);area.doLayout();
            assertThat(area.area.getDocument()).isSameAs(doc);assertThat(area.area.getCaretPosition()).isEqualTo(4);
            assertThat(area.dividerRatio()).isCloseTo(ratio,within(.001));area.area.undoLastAction();assertThat(area.area.getText()).isEqualTo("first");area.close();
        });
    }
}
