package dev.jasper.sdk.files;

/**
 * How a source appears in the chooser.
 *
 * @param id      starts with the plugin's id and a dot; unique; never contains {@code |}
 * @param heading the navigation-pane section title, such as "Remote"
 * @since 0.10.0
 */
public record FileSourceSpec(String id, String heading) {
    /** Validates the id and heading. */
    public FileSourceSpec {
        if (id == null || id.isBlank() || id.contains("|")) throw new IllegalArgumentException("A source needs an id without |: " + id);
        if (heading == null || heading.isBlank()) throw new IllegalArgumentException("A source needs a heading");
    }
}
