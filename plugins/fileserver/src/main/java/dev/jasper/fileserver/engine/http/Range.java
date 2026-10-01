package dev.jasper.fileserver.engine.http;

import java.util.Optional;

/** One satisfiable byte range of a file. */
record Range(long start, long length) {
    /** A range past the end of the file. */
    static final class Unsatisfiable extends Exception {
        Unsatisfiable() { super(null, null, false, false); }
    }

    /** Empty for no header, several ranges or a header this server ignores: the whole file is sent then. */
    static Optional<Range> parse(String header, long size) throws Unsatisfiable {
        if (header == null) return Optional.empty();
        String value = header.strip();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) return Optional.empty();
        String spec = value.substring(6).strip();
        if (spec.contains(",")) return Optional.empty();
        int dash = spec.indexOf('-');
        if (dash < 0) return Optional.empty();
        String first = spec.substring(0, dash).strip(), last = spec.substring(dash + 1).strip();
        try {
            if (first.isEmpty()) {
                if (last.isEmpty()) return Optional.empty();
                long suffix = Long.parseLong(last);
                if (suffix <= 0 || size == 0) throw new Unsatisfiable();
                long length = Math.min(suffix, size);
                return Optional.of(new Range(size - length, length));
            }
            long start = Long.parseLong(first);
            if (start < 0) return Optional.empty();
            if (start >= size) throw new Unsatisfiable();
            long end = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
            if (end < start) return Optional.empty();
            return Optional.of(new Range(start, end - start + 1));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
