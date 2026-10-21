package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownLimits;
import dev.jasper.editor.markdown.MarkdownMode;
import java.awt.EventQueue;
import java.awt.AWTEvent;
import java.awt.Font;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Explicit headless acceptance measurements; timing is reported, never a machine-dependent assertion. */
@EnabledIfEnvironmentVariable(named="JASPER_MARKDOWN_PERFORMANCE", matches="true")
class MarkdownPerformanceTest {
    static final class Dispatches extends EventQueue {
        long maximum; int count;
        @Override protected void dispatchEvent(AWTEvent event) {
            long start=System.nanoTime();
            try {super.dispatchEvent(event);} finally {maximum=Math.max(maximum,System.nanoTime()-start);count++;}
        }
        void remove(){pop();}
    }
    static void edt(Runnable action) throws Exception {SwingUtilities.invokeAndWait(action);}
    static void measure(String label,Runnable action) {
        long start=System.nanoTime();action.run();
        System.out.printf("PERF %s %.3f ms heap=%.1f MiB%n",label,(System.nanoTime()-start)/1e6,
            (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576.0);
    }
    void exercise(String name,String source,int copies) throws Exception {
        var dispatches=new Dispatches();Toolkit.getDefaultToolkit().getSystemEventQueue().push(dispatches);
        var areas=new ArrayList<DocumentArea>();
        var peak=new java.util.concurrent.atomic.AtomicLong();
        Thread memory=Thread.ofPlatform().daemon().start(() -> {
            try {while(!Thread.currentThread().isInterrupted()) {
                peak.accumulateAndGet(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(),Math::max);
                Thread.sleep(10);
            }} catch(InterruptedException stopped) {Thread.currentThread().interrupt();}
        });
        try {
            for(int copy=0;copy<copies;copy++) {
                long start=System.nanoTime();var prepared=PreviewPaneTest.prepare(source);
                System.out.printf("PERF %s prepare %.3f ms source=%d html=%d anchors=%d%n",name,(System.nanoTime()-start)/1e6,source.length(),prepared.result().html().length(),prepared.result().blocks().size());
                int index=copy;
                edt(() -> measure(name+" source setup "+index,() -> {
                    var area=new DocumentArea();areas.add(area);area.setFont(new Font("Serif",0,16));
                    area.area.typography(PreviewPaneTest.TYPE);area.area.setText(source);area.area.setCaretPosition(0);area.area.setLineWrap(true);area.mode(MarkdownMode.SPLIT);
                    area.preview().install(prepared,PreviewPaneTest.TYPE,PreviewPaneTest.palette(false),EditorThemeTest.syntax(1),area.getFont(),1);
                }));
                var area=areas.getLast();long install=System.nanoTime();
                while(true){boolean[] pending={false};edt(() -> {assertThat(area.preview().installFailure()).isEmpty();pending[0]=area.preview().installing();});if(!pending[0])break;Thread.sleep(5);}
                System.out.printf("PERF %s install %d %.3f ms%n",name,copy,(System.nanoTime()-install)/1e6);
                edt(() -> measure(name+" source cold layout "+index,() -> layout(area.scroll,360,300)));
                edt(() -> measure(name+" preview cold layout "+index,() -> layout(area.previewScroll,420,300)));
                edt(() -> measure(name+" map install "+index,() -> area.scrollLink.install(1,prepared.result())));
                awaitGeometry(area);
                long mark=System.nanoTime();edt(area.scrollLink::geometryChanged);awaitGeometry(area);
                System.out.printf("PERF %s geometry rebuild %.3f ms%n",name,(System.nanoTime()-mark)/1e6);
            }
        } finally {
            edt(() -> areas.forEach(DocumentArea::close));
            memory.interrupt();memory.join();
            System.out.printf("PERF %s EDT dispatches=%d maximum=%.3f ms sampled-peak-heap=%.1f MiB%n",name,dispatches.count,dispatches.maximum/1e6,peak.get()/1048576.0);
            edt(dispatches::remove);
        }
    }
    static void layout(javax.swing.JScrollPane scroll,int width,int height) {
        scroll.setSize(width,height);scroll.doLayout();scroll.getViewport().doLayout();
    }
    static void awaitGeometry(DocumentArea area) throws Exception {
        var queued=ScrollLink.class.getDeclaredField("queued");queued.setAccessible(true);
        while(true) {boolean[] busy={false};edt(() -> {try{busy[0]=queued.getBoolean(area.scrollLink);}catch(IllegalAccessException e){throw new AssertionError(e);}});if(!busy[0])return;Thread.sleep(1);}
    }
    @Test void maximumAnchors() throws Exception {exercise("50000 anchors","x\n\n".repeat(50000),1);}
    @Test void denseInline() throws Exception {exercise("dense inline","**a** *b* `c` ".repeat(10000),1);}
    @Test void giantLine() throws Exception {exercise("2MiB giant line","x".repeat(MarkdownLimits.SOURCE_UNITS),1);}
    @Test void multiplePreviews() throws Exception {exercise("three previews","A representative paragraph with **style** and `code`.\n\n".repeat(3000),3);}
}
