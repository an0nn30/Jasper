package dev.jasper.browser.page;

import java.util.Objects;
import java.util.Optional;

/**
 * A load of a page's main frame that failed, as Chromium reported it, in plain types.
 *
 * @param url the address that failed, as Chromium gave it
 * @param code Chromium's name for the error, such as {@code ERR_NAME_NOT_RESOLVED}; empty for an error it does not name
 * @param certificate the certificate Chromium could not verify, which Jasper refused for this address, or empty
 */
public record LoadFailure(String url, String code, Optional<ServerCertificate> certificate) {
    /** Rejects nulls. */
    public LoadFailure {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(certificate, "certificate");
    }
}
