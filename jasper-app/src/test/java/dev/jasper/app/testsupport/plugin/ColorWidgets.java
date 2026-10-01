package dev.jasper.app.testsupport.plugin;

import java.awt.Color;
import javax.swing.JPanel;

public final class ColorWidgets {
    private ColorWidgets() {}
    public static JPanel[] panels() {
        return new JPanel[]{new JPanel() {
            @Override public Color getBackground() { return super.getBackground(); }
            @Override public void setBackground(Color value) { super.setBackground(value); }
        }, new HiddenPanel()};
    }
}
class HiddenPanel extends JPanel {
    @Override public Color getForeground() { return super.getForeground(); }
    @Override public void setForeground(Color value) { super.setForeground(value); }
    @Override public Color getBackground() { return super.getBackground(); }
    @Override public void setBackground(Color value) { super.setBackground(value); }
}
