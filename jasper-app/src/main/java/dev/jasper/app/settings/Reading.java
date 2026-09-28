package dev.jasper.app.settings;

import java.util.List;
import java.util.Objects;

/**
 * What a {@link SettingKind} made of one TOML value: the value to use and any problems. An issue that
 * {@linkplain Issue#rejectsFile() rejects the file} means the value had the wrong TOML type.
 *
 * @param value the value to use, the default when the file's value was unusable
 * @param issues problems found, in the order found
 */
public record Reading<T>(T value, List<Issue> issues) {
    /** How serious an issue is. */
    public enum Severity { WARNING, ERROR }

    /**
     * One problem.
     *
     * @param subPath the key below the setting's own key where it was found; empty for the key itself
     * @param severity how serious it is
     * @param message plain text for the user
     * @param rejectsFile whether the whole file must be rejected (a wrong TOML type)
     */
    public record Issue(List<String> subPath, Severity severity, String message, boolean rejectsFile) {
        public Issue {
            subPath = List.copyOf(subPath);
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
        }
    }

    public Reading {
        issues = List.copyOf(issues);
    }

    static <T> Reading<T> ok(T value) { return new Reading<>(value, List.of()); }

    static <T> Reading<T> wrongType(T fallback, String expected) {
        return new Reading<>(fallback, List.of(new Issue(List.of(), Severity.ERROR,
            "Expected " + expected + "; configuration was not applied.", true)));
    }

    static <T> Reading<T> replaced(T value, Severity severity, String message) {
        return new Reading<>(value, List.of(new Issue(List.of(), severity, message, false)));
    }

    /** Whether any issue rejects the whole file. */
    public boolean rejectsFile() { return issues.stream().anyMatch(Issue::rejectsFile); }
}
