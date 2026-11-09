package dev.jasper.browser.permissions;

import java.util.Optional;

/** What the user chose for a site's permission (spec section 12): only an explicit Allow or Block is stored. */
public enum Decision {
    /** The site may. */
    ALLOWED("allowed", "Allowed"),
    /** The site may not. */
    BLOCKED("blocked", "Blocked");

    private final String token, label;

    Decision(String token, String label) {
        this.token = token;
        this.label = label;
    }

    /**
     * Its token, as the {@code permissions} table and Jasper's pages write it.
     *
     * @return {@code allowed} or {@code blocked}
     */
    public String token() {
        return token;
    }

    /**
     * Its label, as the permissions page and the menu button show it.
     *
     * @return "Allowed" or "Blocked"
     */
    public String label() {
        return label;
    }

    /**
     * The decision whose token is {@code token}.
     *
     * @param token a token, as {@link #token()} writes it
     * @return the decision; empty for any other text
     */
    public static Optional<Decision> ofToken(String token) {
        for (Decision decision : values()) if (decision.token.equals(token)) return Optional.of(decision);
        return Optional.empty();
    }
}
