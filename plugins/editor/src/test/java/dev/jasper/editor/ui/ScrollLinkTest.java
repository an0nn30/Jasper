package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.editor.testsupport.PreviewFixture.layout;

@SuppressWarnings("try")
class ScrollLinkTest {
    private static final String TEXT = "# Start\n\n" + "A wrapped paragraph with many words. ".repeat(30)
        + "\n\n> Quote\n>\n> - nested first\n> - nested second\n\n"
        + "| Header | Other |\n| --- | --- |\n| row one | value |\n| row two | value |\n\n"
        + "```java\nint one = 1;\nint two = 2;\nint three = 3;\n```\n\n"
        + "# Target\n\n" + "tail paragraph\n\n".repeat(35);
    static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    static void drain() throws Exception { for (int i=0;i<8;i++) edt(() -> {}); }
    static final class Fixture implements AutoCloseable {
        DocumentArea area;
        PreviewPane.Prepared prepared;
        Fixture(String text) throws Exception { this(text, a -> {}); }
        Fixture(String text, java.util.function.Consumer<DocumentArea> setup) throws Exception {
            prepared=PreviewPaneTest.prepare(text);
            edt(() -> {
                area=new DocumentArea();area.setFont(new Font("Serif",0,16));
                area.area.typography(PreviewPaneTest.TYPE);area.area.setText(text);area.area.setCaretPosition(0);area.area.setLineWrap(true);
                setup.accept(area);area.mode(MarkdownMode.SPLIT);
                area.preview().install(prepared,PreviewPaneTest.TYPE,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),area.getFont(),1);
            });
            PreviewPaneTest.await(area.preview(),prepared.result());
            edt(() -> { layout();area.scrollLink.install(1,prepared.result()); });drain();
        }
        void layout() {
            dev.jasper.editor.testsupport.PreviewFixture.layout(area.scroll,240,140);dev.jasper.editor.testsupport.PreviewFixture.layout(area.previewScroll,420,140);
        }
        int sy(String text) { try{return (int)area.area.modelToView2D(area.area.getText().indexOf(text)).getY();}catch(Exception e){throw new AssertionError(e);} }
        int py(String text) {
            int offset=area.area.getText().indexOf(text);
            var block=prepared.result().blocks().stream().filter(b -> b.sourceStart()==offset).findFirst().orElseThrow();
            try{return (int)area.preview().editor().modelToView2D(area.preview().anchor(block.id()).orElseThrow()).getY();}catch(Exception e){throw new AssertionError(e);}
        }
        void source(int y) { area.scroll.getViewport().setViewPosition(new Point(0,y)); }
        void preview(int y) { area.previewScroll.getViewport().setViewPosition(new Point(0,y)); }
        int source() { return area.scroll.getViewport().getViewPosition().y; }
        int preview() { return area.previewScroll.getViewport().getViewPosition().y; }
        public void close() throws Exception {edt(area::close);}
    }
    @Test void actualWrappedNestedTableAndCodePassagesMapInBothDirections() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> { for(String passage:new String[]{"> Quote","| row two","int two","# Target"}) {
                f.source(f.sy(passage));assertThat(f.preview()).as(passage).isCloseTo(f.py(passage),within(2));
                f.preview(f.py(passage)+2);assertThat(f.source()).as(passage).isCloseTo(f.sy(passage),within(5));
            }});
        }
    }
    @Test void disabledSyncAndInvalidationNeverMoveTheOtherPane() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> { f.area.scrollLink.linked(false);f.source(f.sy("# Target"));assertThat(f.preview()).isZero();
                f.preview(f.py("int two"));int source=f.source();f.area.scrollLink.invalidate();
                f.area.scrollLink.linked(true);f.preview(10);assertThat(f.source()).isEqualTo(source);
            });
        }
    }
    @Test void repeatedDeferredFollowerEventsCannotStealLeadershipOrOscillate() throws Exception {
        try(var f=new Fixture(TEXT)) {
            var changes=new AtomicInteger();edt(() -> {
                f.area.previewScroll.getViewport().addChangeListener(e -> changes.incrementAndGet());
                f.source(f.sy("# Target"));
                for(int i=0;i<4;i++)SwingUtilities.invokeLater(() -> {
                    var port=f.area.previewScroll.getViewport();
                    for(var listener:port.getChangeListeners())listener.stateChanged(new javax.swing.event.ChangeEvent(port));
                });
            });drain();
            edt(() -> {assertThat(f.source()).isEqualTo(f.sy("# Target"));assertThat(changes.get()).isLessThan(10);
                f.preview(f.py("int two"));assertThat(f.source()).isCloseTo(f.sy("int two"),within(2));});
        }
    }
    @Test void readingPositionTracksLargeInsertionAndDeletedHeadingWithSyncDisabled() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> { f.area.scrollLink.linked(false); f.preview(f.py("# Target")); });
            String prefix="new paragraph\n\n".repeat(60);
            replace(f,prefix+TEXT,() -> f.area.area.insert(prefix,0));
            edt(() -> assertThat(f.preview()).isCloseTo(f.py("# Target"),within(2)));
            String next=(prefix+TEXT).replace("# Target\n\n", "");
            replace(f,next,() -> {int at=f.area.area.getText().indexOf("# Target");f.area.area.replaceRange("",at,at+10);});
            edt(() -> assertThat(f.preview()).isCloseTo(f.py("tail paragraph"),within(2)));
        }
    }
    private static void replace(Fixture f,String text,Runnable edit) throws Exception {
        var prepared=PreviewPaneTest.prepare(text);
        edt(() -> {edit.run();f.area.preview().install(prepared,PreviewPaneTest.TYPE,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),f.area.getFont(),1);});
        PreviewPaneTest.await(f.area.preview(),prepared.result());
        edt(() -> { f.prepared=prepared;f.layout();f.area.scrollLink.install(2,prepared.result()); });drain();
    }
    @Test void bottomIsRetainedThroughReplacementAndImageGrowth() throws Exception {
        try(var f=new Fixture("![image](image.png)\n\n"+TEXT)) {
            edt(() -> {f.preview(f.area.previewScroll.getViewport().getViewSize().height-140);});
            replace(f,f.area.area.getText()+"new tail\n\n".repeat(25),() -> f.area.area.append("new tail\n\n".repeat(25)));
            edt(() -> assertThat(f.preview()).isEqualTo(f.area.previewScroll.getViewport().getViewSize().height-140));
            edt(() -> f.area.preview().image(0,new java.awt.image.BufferedImage(100,500,java.awt.image.BufferedImage.TYPE_INT_ARGB)));drain();
            edt(() -> assertThat(f.preview()).isEqualTo(f.area.previewScroll.getViewport().getViewSize().height-140));
        }
    }
    @Test void imageAndFontGeometryPreserveIndependentReadingPassage() throws Exception {
        try(var f=new Fixture("![image](image.png)\n\n"+TEXT)) {
            edt(() -> {f.area.scrollLink.linked(false);f.preview(f.py("# Target"));
                f.area.preview().image(0,new java.awt.image.BufferedImage(100,500,java.awt.image.BufferedImage.TYPE_INT_ARGB));});drain();
            edt(() -> {assertThat(f.preview()).isCloseTo(f.py("# Target"),within(2));assertThat(f.source()).isZero();
                var type=new dev.jasper.sdk.ui.TerminalTypography("Serif",22,java.util.List.of(),false,1.8);
                f.area.area.typography(type);f.area.preview().restyle(type,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),f.area.getFont(),1);
            });drain();
            edt(() -> {assertThat(f.preview()).isCloseTo(f.py("# Target"),within(2));assertThat(f.source()).isZero();});
        }
    }
    @Test void newerUserScrollSupersedesDeferredRestorationAndStaleMap() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> {f.preview(f.py("# Target"));f.area.scrollLink.geometryChanged();f.preview(f.py("int two"));});drain();
            edt(() -> assertThat(f.preview()).isCloseTo(f.py("int two"),within(2)));
            var prepared=PreviewPaneTest.prepare("prefix\n\n"+TEXT);
            edt(() -> {f.area.area.insert("prefix\n\n",0);f.preview(70);
                int source=f.source();f.area.scrollLink.geometryChanged();assertThat(f.source()).isEqualTo(source);
                f.area.preview().install(prepared,PreviewPaneTest.TYPE,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),f.area.getFont(),1);});
            PreviewPaneTest.await(f.area.preview(),prepared.result());
            edt(() -> {f.prepared=prepared;f.layout();f.area.scrollLink.install(2,prepared.result());});drain();
            edt(() -> assertThat(f.preview()).isEqualTo(70));
        }
    }
    @Test void enteringSplitFollowsSourceAndResizeKeepsPassage() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> {f.area.mode(MarkdownMode.SOURCE);f.source(f.sy("# Target"));f.area.mode(MarkdownMode.SPLIT);});drain();
            edt(() -> {assertThat(f.preview()).isCloseTo(f.py("# Target"),within(2));
                layout(f.area.scroll,150,140);layout(f.area.previewScroll,300,140);f.area.scrollLink.geometryChanged();});drain();
            edt(() -> {assertThat(f.preview()).isCloseTo(f.py("# Target"),within(2));assertThat(f.source()).isCloseTo(f.sy("# Target"),within(2));});
        }
    }
    @Test void foldedOffsetsUseVisibleFoldStartIncludingWrappedFirstLine() throws Exception {
        String code="class LongClassNameWithAHeaderThatWrapsAroundSeveralTimes {\n  int hiddenOne;\n  int hiddenTwo;\n}\n";
        try(var f=new Fixture("```java\n"+code+"```\n\n"+TEXT,a -> a.area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA))) {
            edt(() -> {f.area.area.getFoldManager().reparse();f.area.area.getFoldManager().getFold(0).setCollapsed(true);f.area.scrollLink.geometryChanged();});drain();
            edt(() -> {f.preview(f.py("  int hiddenTwo"));assertThat(f.source()).isCloseTo(f.sy("class Long"),within(2));
                f.preview(f.py("# Target"));assertThat(f.source()).isCloseTo(f.sy("# Target"),within(2));});
        }
    }
    @Test void wrappedParagraphInteriorInterpolatesBetweenRealPassageCoordinates() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> {int start=f.sy("A wrapped"),end=f.sy("> Quote");
                int pStart=f.py("A wrapped"),pEnd=f.py("> Quote");
                f.source((start+end)/2);assertThat(f.preview()).isCloseTo((pStart+pEnd)/2,within(2));
                f.preview((pStart+pEnd)/2+5);
                int expected=start+(int)((end-start)*((f.preview()-pStart)/(double)(pEnd-pStart)));
                assertThat(f.source()).isCloseTo(expected,within(2));
            });
        }
    }
    @Test void duplicateContainerRowsStillInterpolateAcrossTheFollowingGap() throws Exception {
        try(var f=new Fixture(TEXT)) {
            edt(() -> {int start=f.sy("> Quote"),end=f.sy("- nested first");
                int pStart=f.py("> Quote"),pEnd=f.py("- nested first");
                f.source((start+end)/2);assertThat(f.preview()).isCloseTo((pStart+pEnd)/2,within(2));
            });
        }
    }
    @Test void repeatedGeometryNotificationsDoNotDriftWithinANestedSpan() throws Exception {
        try(var f=new Fixture(TEXT)) {
            int[] original={0};
            edt(() -> {f.area.scrollLink.linked(false);original[0]=(f.py("> Quote")+f.py("- nested first"))/2;f.preview(original[0]);});
            for(int i=0;i<10;i++) {edt(f.area.scrollLink::geometryChanged);drain();}
            edt(() -> assertThat(f.preview()).isEqualTo(original[0]));
        }
    }
    @Test void oldQueuedGeometryAndDifferentAttachedResultCannotReviveInvalidMap() throws Exception {
        try(var f=new Fixture(TEXT)) {
            var other=PreviewPaneTest.prepare("different");
            edt(() -> {f.source(f.sy("# Target"));f.area.scrollLink.geometryChanged();f.area.scrollLink.invalidate();
                f.area.scrollLink.install(2,other.result());f.preview(80);});drain();
            edt(() -> assertThat(f.source()).isEqualTo(f.sy("# Target")));
        }
    }
    @Test void emptyAndUnmappedDocumentStayWithinBounds() throws Exception {
        try(var f=new Fixture("\n\n")) {edt(() -> {f.area.scrollLink.geometryChanged();assertThat(f.source()).isZero();assertThat(f.preview()).isZero();});}
    }
}
