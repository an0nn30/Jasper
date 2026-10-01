package dev.jasper.editor.testsupport;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;

/** Scoped palette sentinels and lightweight paint helpers; callers run on the EDT. */
public final class ZoneColors implements AutoCloseable {
    private final Map<String, Object> previous = new HashMap<>();
    public final JPanel workspace = zone("workspace"), chrome = zone("chrome");
    public ZoneColors() {
        put("Jasper.workspace.dark", Boolean.TRUE);
        String[] names = {"background", "text", "textMuted", "border", "accent", "success", "warning", "error"};
        String[] keys = {"Panel.background", "Label.foreground", "Jasper.mutedForeground", "Component.borderColor",
            "Component.accentColor", "Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground"};
        for (int i = 0; i < names.length; i++) {
            put("Jasper.workspace." + names[i], new Color(0x203040 + i * 0x050301));
            put(keys[i], new Color(0x807060 + i * 0x030105));
        }
    }
    public void put(String key, Object value) { if (!previous.containsKey(key)) previous.put(key, UIManager.get(key)); UIManager.put(key, value); }
    private static JPanel zone(String zone) { var panel = new JPanel(); panel.putClientProperty("Jasper.zone", zone); return panel; }
    public static java.util.List<JLabel> labels(Component component) {
        var result = new ArrayList<JLabel>();
        if (component instanceof JLabel label) result.add(label);
        if (component instanceof Container container) for (Component child : container.getComponents()) result.addAll(labels(child));
        return result;
    }
    public static Color rule(JComponent component, int x, int y) {
        var image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { component.getBorder().paintBorder(component, graphics, 0, 0, 80, 40); }
        finally { graphics.dispose(); }
        return new Color(image.getRGB(x, y));
    }
    public static void paint(JComponent component) {
        component.setSize(320, 100); component.doLayout();
        var image = new BufferedImage(320, 100, BufferedImage.TYPE_INT_RGB); var graphics = image.createGraphics();
        try { component.paint(graphics); } finally { graphics.dispose(); }
    }
    @Override public void close() { previous.forEach(UIManager::put); }
}
