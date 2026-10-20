package dev.jasper.editor.api;

import java.util.regex.Pattern;

/** Validation shared by providers accepting decoded, slash-separated relative file paths. */
public final class RelativePaths {
    private RelativePaths() { }

    /**
     * Validates an already decoded path; percent signs and plus signs are literal filename characters.
     * Dot segments may leave the source directory and are normalized by the provider.
     *
     * @param path a nonempty relative file path, with {@code /} separators
     * @return the unchanged path
     * @throws IllegalArgumentException for absolute/drive paths, URI schemes, queries, fragments,
     *     backslashes, control characters or unpaired surrogates
     */
    public static String requireValid(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0
                || path.indexOf('?') >= 0 || path.indexOf('#') >= 0
                || Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:").matcher(path).lookingAt())
            throw new IllegalArgumentException("A decoded relative file path is required");
        for (int offset = 0; offset < path.length();) {
            int codePoint = path.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF)
                throw new IllegalArgumentException("A relative file path cannot contain controls or unpaired surrogates");
            offset += Character.charCount(codePoint);
        }
        return path;
    }
}
