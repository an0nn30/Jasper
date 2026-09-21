package dev.jasper.terminal.view;

import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.internal.emulation.EmulationFixture;
import dev.jasper.terminal.internal.emulation.FakeConnector;
import dev.jasper.terminal.internal.rendering.TerminalPainter;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import com.sun.management.ThreadMXBean;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;

/** Opt-in headless comparison: no PTY, shell, native window, or timing assertion. */
public final class TerminalRefactorMeasurement {
    private TerminalRefactorMeasurement() { }
    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperty("java.runtime.version") + " / " + System.getProperty("os.arch"));
        measure("plain", "\033[31mRED\033[0m plain\r\n");
        measure("mixed", "\033[32m\u754c\033[0m \uD83D\uDE00 \033[1mstyled\033[0m\r\n");
    }
    private static void measure(String name, String line) throws Exception {
        FakeConnector input = new FakeConnector();
        try (TerminalSession session = EmulationFixture.unstarted(input,150,45,10_000)) {
            session.internalAccess().startReading();
            input.feed(line.repeat(10_045) + "DONE");
            Await.until(() -> session.internalAccess().snapshot().lineText(44).contains("DONE"), "measurement fixture loaded");
            SwingUtilities.invokeAndWait(() -> {
                FontSet fonts = new FontSet("JetBrains Mono",14f,List.of(),true);
                TerminalPainter painter = new TerminalPainter(fonts,Palette.jasperDark());
                BufferedImage image = new BufferedImage(150*fonts.cellWidth(),45*fonts.cellHeight(),BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    Runnable capture = session.internalAccess()::snapshot;
                    Runnable paint = () -> painter.paint(graphics,session.internalAccess().snapshot(),
                        new TerminalPainter.CursorLook(CursorStyle.BLOCK,false,false),List.of(),image.getWidth(),image.getHeight());
                    sample(name+" capture",capture);
                    sample(name+" capture+paint",paint);
                } finally { graphics.dispose(); }
            });
        }
    }
    private static void sample(String name,Runnable operation) {
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean allocation = memory.isThreadAllocatedMemorySupported();
        if (allocation) memory.setThreadAllocatedMemoryEnabled(true);
        for (int i=0;i<1000;i++) operation.run();
        double[] time = new double[5], bytes = new double[5];
        for (int batch=0;batch<5;batch++) {
            long beforeBytes = allocation ? memory.getCurrentThreadAllocatedBytes() : 0;
            long before = System.nanoTime();
            for (int i=0;i<1000;i++) operation.run();
            time[batch]=(System.nanoTime()-before)/1_000_000.0/1000;
            bytes[batch]=allocation ? (memory.getCurrentThreadAllocatedBytes()-beforeBytes)/1000.0 : Double.NaN;
        }
        Arrays.sort(time); Arrays.sort(bytes);
        System.out.printf("%s: ms/op %.4f [%.4f..%.4f]; bytes/op %.0f%n",name,time[2],time[0],time[4],bytes[2]);
    }
}
