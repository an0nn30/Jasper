package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The frame title template (spec 10.4).
 *
 * @param template a template over {@code {context}}, {@code {session}} and {@code {directory}}
 */
public record TitleFormat(String template) {
    /** The template is required. */
    public TitleFormat { Objects.requireNonNull(template); }
}
