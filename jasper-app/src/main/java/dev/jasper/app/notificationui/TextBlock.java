package dev.jasper.app.notificationui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Plain text word-wrapped to a fixed width and, with a line limit, cut with an ellipsis. Painted with
 * {@code drawString}, so markup is never interpreted. EDT only.
 */
final class TextBlock extends JComponent {
    static final String ELLIPSIS = "…";
    private final String colorKey;
    private final boolean bold;
    private final int maxLines;
    private String text = "";
    private int wrapWidth = 1;
    private List<String> lines = List.of();

    /** {@code maxLines} 0 means no limit. */
    TextBlock(String colorKey, boolean bold, int maxLines) {
        this.colorKey = colorKey;
        this.bold = bold;
        this.maxLines = maxLines;
        setOpaque(false);
        setFocusable(false);
        restyle();
    }

    void setText(String value) {
        text = Objects.requireNonNull(value, "value");
        relayout();
    }

    String text() { return text; }

    void setWrapWidth(int pixels) {
        int width = Math.max(1, pixels);
        if (width == wrapWidth) return;
        wrapWidth = width;
        relayout();
    }

    List<String> lines() { return lines; }

    int lineHeight() { return getFontMetrics(getFont()).getHeight(); }

    int lastLineWidth() { return lines.isEmpty() ? 0 : measure(lines.getLast()); }

    @Override public void updateUI() {
        super.updateUI();
        if (colorKey != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) setFont(bold ? base.deriveFont(Font.BOLD) : base);
        setForeground(NotificationStyle.color(colorKey, Color.BLACK));
        relayout();
    }

    private int measure(String value) {
        return (int) Math.ceil(BasicGraphicsUtils.getStringWidth(this, getFontMetrics(getFont()), value));
    }

    private void relayout() {
        if (getFont() == null) return;
        lines = wrap(text, this::measure, wrapWidth, maxLines);
        revalidate();
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(wrapWidth, lines.size() * lineHeight()); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics metrics = g.getFontMetrics();
            int y = metrics.getAscent();
            for (String line : lines) {
                BasicGraphicsUtils.drawString(this, g, line, 0, y);
                y += metrics.getHeight();
            }
        } finally { g.dispose(); }
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
            @Override public AccessibleRole getAccessibleRole() { return AccessibleRole.LABEL; }
            @Override public String getAccessibleName() { return text; }
        };
        return accessibleContext;
    }

    /**
     * Words that fit {@code width} per line, a word wider than a line broken by character, explicit line breaks kept.
     * With {@code maxLines > 0}, cut text ends its last kept line with an ellipsis. Never splits a surrogate pair.
     */
    static List<String> wrap(String text, ToIntFunction<String> measure, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        int limit = maxLines > 0 ? maxLines + 1 : Integer.MAX_VALUE;
        for (String paragraph : text.split("\n", -1)) {
            if (lines.size() >= limit) break;
            int before = lines.size();
            var line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (rest.length() > 1 && measure.applyAsInt(rest) > width) {
                    int fit = fitting(rest, measure, width);
                    lines.add(rest.substring(0, fit));
                    rest = rest.substring(fit);
                }
                line.append(rest);
                if (lines.size() >= limit) break;
            }
            if (!line.isEmpty() || lines.size() == before) lines.add(line.toString());
        }
        if (maxLines <= 0 || lines.size() <= maxLines) return lines;
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        String last = kept.getLast();
        while (!last.isEmpty() && measure.applyAsInt(last + ELLIPSIS) > width) last = dropLast(last);
        kept.set(maxLines - 1, last.stripTrailing() + ELLIPSIS);
        return kept;
    }

    /** How many leading characters of {@code word} fit, at least one, never ending on half a surrogate pair. */
    private static int fitting(String word, ToIntFunction<String> measure, int width) {
        int fit = 1;
        while (fit < word.length() && measure.applyAsInt(word.substring(0, fit + 1)) <= width) fit++;
        if (fit < word.length() && Character.isHighSurrogate(word.charAt(fit - 1))) fit = fit > 1 ? fit - 1 : fit + 1;
        return fit;
    }

    private static String dropLast(String value) {
        int end = value.length() - 1;
        if (end > 0 && Character.isLowSurrogate(value.charAt(end)) && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
