package dev.jasper.app.contributions;

import java.util.Objects;

/** A stripe button's unread mark: how many, how urgent, and the words its tooltip and screen readers use. */
public record PanelBadge(int count, Tone tone, String description) {
    public enum Tone { ACCENT, ERROR }

    public PanelBadge {
        if (count < 1) throw new IllegalArgumentException("A badge counts at least one item");
        Objects.requireNonNull(tone, "tone");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("A badge needs a description");
    }
}
