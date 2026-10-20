package dev.jasper.vault.ui;

import java.util.List;
import java.util.Locale;

/** Lock Vault's default shortcut, which the plugin registers and the classic toolbar spells out beside its button. */
public final class LockShortcut {
    /** The default, in the configuration syntax, for every platform. */
    public static final String BINDING = "cmd+shift+l";

    private LockShortcut() { }

    /**
     * {@code binding} as the platform writes it: symbols on macOS; elsewhere spelled out under the SDK's rule for one
     * default, where cmd means Ctrl+Shift and gains Alt beside shift, so {@code cmd+shift+l} reads "Ctrl+Alt+Shift+L". A
     * plugin cannot read the live binding (plan 10b Rulings 8), so a rebinding leaves the caption at the default.
     */
    static String caption(String binding, boolean mac) {
        List<String> tokens = List.of(binding.toLowerCase(Locale.ROOT).split("\\+"));
        boolean cmd = tokens.contains("cmd"), translated = cmd && !mac;
        boolean ctrl = tokens.contains("ctrl") || translated, shift = tokens.contains("shift") || translated;
        boolean alt = tokens.contains("alt") || translated && tokens.contains("shift");
        String key = tokens.getLast().toUpperCase(Locale.ROOT);
        return mac
            ? (ctrl ? "\u2303" : "") + (alt ? "\u2325" : "") + (shift ? "\u21E7" : "") + (cmd ? "\u2318" : "") + key
            : (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + (shift ? "Shift+" : "") + key;
    }
}
