package dev.jasper.app.notificationui;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Dimension;
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

/**
 * Plain text word-wrapped to a fixed width and, with a line limit, cut with an ellipsis: the notification card's clamp
 * primitive (ruling G6), in a module typography role and semantic colour from the chrome KitContext. Painted with
 * {@code drawString}, so markup is never interpreted. EDT only.
 */
final class TextBlock extends JComponent {
    static final String ELLIPSIS = "…";
    private final KitContext kit;
    private Typography.Role role;
    private final SemanticColor color;
    private final int maxLines;
    private final SemanticColor surface;
    private String text = "";
    private int wrapWidth = 1;
    private List<String> lines = List.of();

    /** {@code maxLines} 0 means no limit. */
    TextBlock(KitContext kit, Typography.Role role, SemanticColor color, int maxLines) {
        this(kit, role, color, maxLines, null);
    }

    /** Optional consuming surface applies the central text floor without changing the shared color role. */
    TextBlock(KitContext kit, Typography.Role role, SemanticColor color, int maxLines, SemanticColor surface) {
        this.kit = kit;
        this.surface = surface;
        this.role = role;
        this.color = color;
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

    /** The whole text's width on one line. */
    int textWidth() { return getFont() == null ? 0 : measure(text); }

    @Override public void updateUI() {
        super.updateUI();
        // JComponent's constructor calls this before the fields exist; the constructor styles the text itself.
        if (kit != null) restyle();
    }

    /** Reads the role's font and colour again: the card calls this when the theme changes. */
    void restyle() {
        setFont(NotificationStyle.font(kit, role));
        setForeground(surface == null ? NotificationStyle.color(kit, color)
            : dev.jasper.ui.theme.SurfaceContrast.on(kit.theme().semantic(), color, surface, dev.jasper.ui.theme.SurfaceContrast.TEXT));
        relayout();
    }

    Typography.Role role() { return role; }

    /** Takes another role (a history title turns plain once read) and restyles. */
    void setRole(Typography.Role value) {
        if (value == role) return;
        role = value;
        restyle();
    }

    private int measure(String value) {
        return dev.jasper.ui.swing.NativeChromePainting.measureText(getFont(), value);
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
            dev.jasper.ui.swing.NativeChromePainting.prepareText(g);
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics metrics = g.getFontMetrics();
            int y = metrics.getAscent();
            for (String line : lines) {
                g.drawString(line, 0, y);
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
