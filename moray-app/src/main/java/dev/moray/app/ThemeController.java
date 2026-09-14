package dev.moray.app;

import dev.moray.terminal.Palette;
import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.SwingUtilities;

/** EDT-owned Swing look and feel; terminal colors do not follow desktop appearance. */
final class ThemeController {
    private static final ResolvedTheme TERMINAL = new ResolvedTheme(BuiltinTheme.CLASSIC_DARK,
        new Palette(Color.WHITE, Color.BLACK, Color.WHITE, new Color(0x555555),
            List.of(Color.BLACK, new Color(0xcd0000), new Color(0x00cd00), new Color(0xcdcd00),
                new Color(0x0000ee), new Color(0xcd00cd), new Color(0x00cdcd), new Color(0xe5e5e5),
                new Color(0x7f7f7f), Color.RED, Color.GREEN, Color.YELLOW,
                new Color(0x5c5cff), Color.MAGENTA, Color.CYAN, Color.WHITE)));
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private UiLookAndFeel selected;
    private String warning = "";

    ThemeController() { selectLaf(UiLookAndFeel.METAL); }

    ResolvedTheme current() { requireEdt(); return TERMINAL; }

    String selectLaf(UiLookAndFeel next) {
        requireEdt(); Objects.requireNonNull(next);
        if (next == selected && warning.isEmpty()) return warning;
        warning = next.install();
        selected = next;
        for (WindowContent owner : List.copyOf(owners)) owner.applyTheme(TERMINAL, true);
        return warning;
    }

    void register(WindowContent owner) {
        requireEdt();
        if (owners.add(Objects.requireNonNull(owner))) owner.applyTheme(TERMINAL, true);
    }

    void unregister(WindowContent owner) { requireEdt(); owners.remove(owner); }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Look and feel operations require the EDT");
    }
}
