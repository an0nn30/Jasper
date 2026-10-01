package dev.jasper.vault.testsupport;

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
    /** A host composite remains installed until this control's semantic color actually changes. */
    public void assertStableSemanticStyle(JComponent component, String token) {
        paint(component);
        Color initial = component.getForeground();
        var composite = Map.of("foreground", initial, "background", new Color(0x102030));
        component.putClientProperty("FlatLaf.style", composite);
        var events = new java.util.concurrent.atomic.AtomicInteger();
        java.beans.PropertyChangeListener listener = event -> events.incrementAndGet();
        component.addPropertyChangeListener("FlatLaf.style", listener);
        try {
            for (int i = 0; i < 3; i++) paint(component);
            org.assertj.core.api.Assertions.assertThat(events.get()).as("unchanged paints must not republish a plugin style").isZero();
            org.assertj.core.api.Assertions.assertThat(component.getClientProperty("FlatLaf.style")).isSameAs(composite);
            component.updateUI();
            component.setForeground(new Color(0x654321));
            paint(component);
            org.assertj.core.api.Assertions.assertThat(component.getForeground()).isEqualTo(initial);
            org.assertj.core.api.Assertions.assertThat(events.get()).as("delegate recovery uses the effective setter without replacing the composite").isZero();
            org.assertj.core.api.Assertions.assertThat(component.getClientProperty("FlatLaf.style")).isSameAs(composite);
            Color next = new Color(0x123456);
            put("Jasper.workspace." + token, next);
            paint(component);
            org.assertj.core.api.Assertions.assertThat(component.getForeground()).isEqualTo(next);
            org.assertj.core.api.Assertions.assertThat(events.get()).as("a changed semantic color publishes once").isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(((Map<?, ?>) component.getClientProperty("FlatLaf.style")).get("foreground")).isEqualTo(next);
        } finally { component.removePropertyChangeListener("FlatLaf.style", listener); }
    }
    @Override public void close() { previous.forEach(UIManager::put); }
}
