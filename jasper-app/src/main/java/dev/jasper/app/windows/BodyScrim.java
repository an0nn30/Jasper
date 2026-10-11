package dev.jasper.app.windows;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.platform.WindowBody;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

/** One effective scrim per registered root body; native modality owns input exclusion. */
final class BodyScrim implements AutoCloseable {
    private static final Object KEY = new Object();
    private final Shared shared;
    private boolean closed;
    private BodyScrim(Shared shared) { this.shared = shared; shared.leases++; }
    static BodyScrim acquire(WindowBody body) {
        var existing = body.root().getClientProperty(KEY);
        Shared shared;
        if (existing instanceof Shared value && value.body == body) shared = value;
        else {
            if (existing instanceof Shared stale) stale.remove();
            shared = new Shared(body);body.root().putClientProperty(KEY, shared);
            body.root().getLayeredPane().add(shared.layer, Integer.valueOf(JLayeredPane.MODAL_LAYER));
        }
        var lease = new BodyScrim(shared); lease.layout(); return lease;
    }
    void layout() {
        if (closed) return;
        Rectangle bounds = shared.body.boundsInRoot().orElse(new Rectangle());
        shared.layer.setBounds(SwingUtilities.convertRectangle(shared.body.root(), bounds, shared.body.root().getLayeredPane()));
        shared.layer.repaint();
    }
    @Override public void close() { if (!closed) { closed = true; if (--shared.leases == 0) shared.remove(); } }
    private static final class Shared {
        final WindowBody body; int leases;
        final JComponent layer = new JComponent() {
            @Override public boolean contains(int x, int y) { return false; }
            @Override protected void paintComponent(Graphics g) {
                g.setColor(ColorMath.opacity(ThemeZone.color(this, SemanticColor.SURFACE_SCRIM), .35));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        Shared(WindowBody body) { this.body = body; }
        void remove() {
            body.root().getLayeredPane().remove(layer); body.root().getLayeredPane().repaint();
            if (body.root().getClientProperty(KEY) == this) body.root().putClientProperty(KEY, null);
        }
    }
}
