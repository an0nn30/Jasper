package dev.jasper.app.appearance;

import com.formdev.flatlaf.ui.FlatButtonBorder;
import com.formdev.flatlaf.ui.FlatButtonUI;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;

/** Jasper's ordinary Swing buttons retain FlatLaf behavior with compact form sizing and branded text. */
public final class BrandedButtonUI extends FlatButtonUI {
    private BrandedButtonUI() { super(false); }

    /** Form-control logical height, scaled by the application look and feel. */
    public static final int HEIGHT = 24;
    /** Form-control logical label size. */
    public static final float FONT_SIZE = 12f;

    /** Called by Swing when installing or refreshing the application theme. */
    public static ComponentUI createUI(JComponent component) { return new BrandedButtonUI(); }

    @Override public void installUI(JComponent component) {
        super.installUI(component);
        if (component.getFont() instanceof UIResource) {
            var base = javax.swing.UIManager.getFont("Label.font");
            component.setFont(new FontUIResource(base.deriveFont(Font.PLAIN, base.getSize2D())));
        }
    }

    @Override public Dimension getPreferredSize(JComponent component) {
        Dimension size = super.getPreferredSize(component);
        // Explicit custom borders and tool-bar roles include small status/rail controls.
        if (size != null && component.getBorder() instanceof FlatButtonBorder && !(component.getParent() instanceof javax.swing.JToolBar)
            && !"toolBarButton".equals(component.getClientProperty("JButton.buttonType")))
            size.height = Math.max(size.height, UIScale.scale(HEIGHT));
        return size;
    }
}
