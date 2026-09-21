package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Comma-separated comparators that must all hold; blank text admits every version. */
record VersionRange(List<Bound> bounds) {
    static final VersionRange ANY = new VersionRange(List.of());

    enum Op {
        GE(">="), LE("<="), GT(">"), LT("<"), EQ("=");
        final String symbol;
        Op(String symbol) { this.symbol = symbol; }
    }

    record Bound(Op op, Version version) {
        boolean admits(Version candidate) {
            int order = candidate.compareTo(version);
            return switch (op) {
                case GE -> order >= 0; case LE -> order <= 0; case GT -> order > 0; case LT -> order < 0; case EQ -> order == 0;
            };
        }
        @Override public String toString() { return op.symbol + version; }
    }

    VersionRange { bounds = List.copyOf(bounds); }

    static VersionRange parse(String text) {
        if (text == null || text.isBlank()) return ANY;
        List<Bound> bounds = new ArrayList<>();
        for (String raw : text.split(",", -1)) {
            String part = raw.strip();
            Op found = null;
            for (Op op : Op.values()) if (part.startsWith(op.symbol)) { found = op; break; }
            if (found == null) throw new IllegalArgumentException("Invalid version range: " + text);
            bounds.add(new Bound(found, Version.parse(part.substring(found.symbol.length()))));
        }
        return new VersionRange(bounds);
    }

    boolean contains(Version candidate) { return bounds.stream().allMatch(bound -> bound.admits(candidate)); }

    @Override public String toString() {
        return bounds.isEmpty() ? "any" : bounds.stream().map(Bound::toString).collect(Collectors.joining(", "));
    }
}
