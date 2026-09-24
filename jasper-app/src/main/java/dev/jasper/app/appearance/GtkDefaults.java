package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The JDK's GTK look and feel plus aliases for Jasper-owned paint, sampled from the installed desktop theme. */
final class GtkDefaults {
    static final String LOOK_AND_FEEL = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
    static final String PANEL_BACKGROUND = "panel.background";
    static final String LABEL_FOREGROUND = "label.foreground";
    static final String LIST_SELECTION_BACKGROUND = "list.selectionBackground";
    static final String LIST_SELECTION_FOREGROUND = "list.selectionForeground";
    static final String TEXT_BACKGROUND = "text.background";
    static final String TEXT_FOREGROUND = "text.foreground";
    static final String TEXT_CARET = "text.caret";
    static final String TEXT_SELECTION_BACKGROUND = "text.selectionBackground";
    // GTK's synth delegates publish few colour defaults; these system colours stand behind the samples.
    private static final Map<String, String> SYSTEM = Map.of(PANEL_BACKGROUND, "control", LABEL_FOREGROUND, "controlText",
        LIST_SELECTION_BACKGROUND, "textHighlight", LIST_SELECTION_FOREGROUND, "textHighlightText",
        TEXT_BACKGROUND, "text", TEXT_FOREGROUND, "textText", TEXT_CARET, "textText", TEXT_SELECTION_BACKGROUND, "textHighlight");
    private GtkDefaults() {}

    /** Installs GTK or throws; the class is Linux-only and module-internal, so it is named, never referenced. */
    static boolean install() {
        try { UIManager.setLookAndFeel(LOOK_AND_FEEL); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException("this Java runtime has no GTK look and feel", failure); }
        catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException("GTK is not available on this desktop", failure); }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the GTK look and feel could not be loaded", failure);
        } catch (LinkageError | InternalError failure) {
            throw new IllegalStateException("the GTK look and feel could not be loaded", failure);
        }
        decorate(UIManager.getLookAndFeelDefaults(), sample());
        return true;
    }

    /** Colours the installed delegates gave throwaway components, with the LAF's system colours behind them. */
    static Function<String, Color> sample() {
        var panel = new JPanel(); var label = new JLabel(); var list = new JList<String>(); var text = new JTextArea();
        Map<String, Color> sampled = new HashMap<>();
        sampled.put(PANEL_BACKGROUND, panel.getBackground());
        sampled.put(LABEL_FOREGROUND, label.getForeground());
        sampled.put(LIST_SELECTION_BACKGROUND, list.getSelectionBackground());
        sampled.put(LIST_SELECTION_FOREGROUND, list.getSelectionForeground());
        sampled.put(TEXT_BACKGROUND, text.getBackground());
        sampled.put(TEXT_FOREGROUND, text.getForeground());
        sampled.put(TEXT_CARET, text.getCaretColor());
        sampled.put(TEXT_SELECTION_BACKGROUND, text.getSelectionColor());
        return key -> {
            Color color = sampled.get(key);
            return color != null ? color : UIManager.getColor(SYSTEM.get(key));
        };
    }

    static void decorate(UIDefaults defaults, Function<String, Color> sample) {
        Color panel = orElse(sample.apply(PANEL_BACKGROUND), new Color(0xf6f5f4));
        boolean dark = GtkPalette.dark(panel);
        Color foreground = orElse(sample.apply(LABEL_FOREGROUND), dark ? Color.WHITE : Color.BLACK);
        Color muted = blend(foreground, panel, 0.55);
        Color border = blend(foreground, panel, 0.2);
        Color selection = orElse(sample.apply(LIST_SELECTION_BACKGROUND), new Color(0x3584e4));
        Color selectionForeground = orElse(sample.apply(LIST_SELECTION_FOREGROUND), Color.WHITE);
        defaults.put("Jasper.nativeChrome", true);
        defaults.put("Jasper.gtk", true);
        put(defaults, panel, "Jasper.titleBackground", "Jasper.paletteBackground", "Jasper.tabSelectedBackground");
        put(defaults, foreground, "Jasper.titleForeground", "Jasper.chromeForeground", "Jasper.tabSelectedForeground",
            "Jasper.paletteForeground");
        put(defaults, muted, "Jasper.titleInactiveForeground", "Jasper.mutedForeground", "Jasper.paletteMutedForeground");
        put(defaults, border, "Jasper.titleSeparator", "Jasper.splitDivider", "Component.borderColor", "Jasper.paletteBorder");
        put(defaults, selection, "Jasper.paletteAccent", "Component.focusedBorderColor", "Jasper.paletteSelectionBackground");
        put(defaults, selectionForeground, "Jasper.paletteSelectionForeground");
        // Jasper Light / Jasper Dark status colours, so status text reads on either theme.
        put(defaults, new Color(dark ? 0xa8c58d : 0x50a14f), "Jasper.runningForeground");
        put(defaults, new Color(dark ? 0xa8c58d : 0x28752a), "Jasper.configSuccessForeground");
        put(defaults, new Color(dark ? 0xe5c07b : 0x805900), "Jasper.configWarningForeground");
        put(defaults, new Color(dark ? 0xff858d : 0xb42332), "Jasper.configErrorForeground");
        put(defaults, new Color(dark ? 0xc75450 : 0xdb5860), "Actions.Red");
        put(defaults, new Color(dark ? 0xf0a732 : 0xeda200), "Actions.Yellow");
        put(defaults, new Color(dark ? 0x499c54 : 0x59a869), "Actions.Green");
        putIfPresent(defaults, GtkPalette.BACKGROUND, sample.apply(TEXT_BACKGROUND));
        putIfPresent(defaults, GtkPalette.FOREGROUND, sample.apply(TEXT_FOREGROUND));
        putIfPresent(defaults, GtkPalette.CARET, sample.apply(TEXT_CARET));
        putIfPresent(defaults, GtkPalette.SELECTION, sample.apply(TEXT_SELECTION_BACKGROUND));
    }

    private static void put(UIDefaults defaults, Color color, String... keys) {
        // Plain colours: a UIResource would be replaced by the delegate on the next updateUI.
        Color plain = new Color(color.getRed(), color.getGreen(), color.getBlue());
        for (String key : keys) defaults.put(key, plain);
    }
    private static void putIfPresent(UIDefaults defaults, String key, Color color) { if (color != null) put(defaults, color, key); }
    private static Color orElse(Color color, Color fallback) { return color != null ? color : fallback; }
    private static Color blend(Color a, Color b, double weight) {
        return new Color((int) Math.round(a.getRed() * weight + b.getRed() * (1 - weight)),
            (int) Math.round(a.getGreen() * weight + b.getGreen() * (1 - weight)),
            (int) Math.round(a.getBlue() * weight + b.getBlue() * (1 - weight)));
    }
}
