package dev.jasper.vault.ui;

import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.Platform;

/**
 * The vault's copies to Jasper's clipboard, each confirmed by a card in the Vault copies group that names the entry,
 * never the copied text. A copy that fails is Jasper's to report, so it gets no card. UI thread only.
 */
public final class CopyCards {
    /** The notification group of the cards. */
    public static final String GROUP = "dev.jasper.vault.copies";
    private static final int MAX_TITLE = 200;

    private final Platform platform;
    private final NotificationGroup cards;

    private CopyCards(Platform platform, NotificationGroup cards) { this.platform = platform; this.cards = cards; }

    /** Declares the Vault copies group, cards by default; declaring it again returns the same group. */
    public static CopyCards declare(PluginContext context) {
        return new CopyCards(context.platform(), context.notifications().group(new GroupSpec(GROUP, "Vault copies", DisplayMode.CARD)));
    }

    /** A secret copy, such as a password: Jasper clears it from the clipboard after 30 seconds. */
    public void secret(String what, String name, String text) {
        if (platform.copySecret(text)) cards.info(title(what, name), "Copied to Clipboard for 30 seconds");
    }

    /** An ordinary copy, such as a public key, which stays on the clipboard. */
    public void plain(String what, String name, String text) {
        if (platform.copy(text)) cards.info(title(what, name), "");
    }

    private static String title(String what, String name) {
        String title = "Copied the " + what + " for " + name;
        if (title.length() <= MAX_TITLE) return title;
        int end = MAX_TITLE - 1;
        if (Character.isHighSurrogate(title.charAt(end - 1))) end--;
        return title.substring(0, end) + "\u2026";
    }
}
