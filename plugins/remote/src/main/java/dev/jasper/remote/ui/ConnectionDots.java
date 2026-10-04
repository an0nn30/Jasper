package dev.jasper.remote.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Ellipse2D;
import java.util.function.LongSupplier;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;

/** EDT-owned connection motion: a soft dot splits, bounces and gathers again. */
final class ConnectionDots extends JPanel {
    private static final long CYCLE_NANOS = 1_600_000_000L;
    private final LongSupplier clock;
    final Timer timer;
    private boolean working;
    private long started;

    ConnectionDots() { this(System::nanoTime); }

    ConnectionDots(LongSupplier clock) {
        this.clock = clock;
        setOpaque(false);
        setFocusable(false);
        getAccessibleContext().setAccessibleName("Connecting");
        timer = new Timer(16, event -> repaint());
        timer.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0)
                syncTimer();
        });
    }

    void setWorking(boolean working) {
        this.working = working;
        syncTimer();
        repaint();
    }

    private void syncTimer() {
        if (working && isShowing()) {
            if (!timer.isRunning()) {
                started = clock.getAsLong();
                timer.start();
            }
        } else timer.stop();
    }

    @Override public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private double scale() {
        var font = UIManager.getFont("Label.font");
        return font == null ? 1 : font.getSize2D() / 13.0;
    }

    @Override public Dimension getPreferredSize() {
        double scale = scale();
        return new Dimension((int) Math.ceil(88 * scale), (int) Math.ceil(36 * scale));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color accent = UIManager.getColor("ProgressBar.foreground");
            if (accent == null) accent = UIManager.getColor("Label.foreground");
            g.setColor(accent == null ? Color.GRAY : accent);
            g.translate(getWidth() / 2.0, getHeight() / 2.0);
            double scale = Math.min(scale(), Math.min(getWidth() / 88.0, getHeight() / 36.0));
            g.scale(scale, scale);
            double phase = timer.isRunning() ? Math.floorMod(clock.getAsLong() - started, CYCLE_NANOS) / (double) CYCLE_NANOS : 0;
            double spread = (1 - Math.cos(2 * Math.PI * phase)) / 2;
            double radius = 7 - 2.5 * spread;
            for (int dot = -1; dot <= 1; dot++) {
                double wave = Math.sin(4 * Math.PI * phase + dot * .8);
                double lift = -5 * spread * wave;
                double stretch = 1 + .22 * spread * Math.cos(4 * Math.PI * phase + dot * .8);
                double width = radius / stretch, height = radius * stretch;
                g.fill(new Ellipse2D.Double(dot * 23 * spread - width, lift - height, width * 2, height * 2));
            }
        } finally { g.dispose(); }
    }
}
