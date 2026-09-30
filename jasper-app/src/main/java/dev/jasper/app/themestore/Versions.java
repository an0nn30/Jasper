package dev.jasper.app.themestore;

/** IntelliJ-style dotted plugin versions. Pure. */
final class Versions {
    private Versions() { }

    /**
     * Whether {@code candidate} is newer than {@code installed}. Parts split on {@code .} and {@code -}; numbers compare
     * as numbers, a number beats a word ({@code 1.0.0} is newer than {@code 1.0.0-beta}), words compare ignoring case, and
     * a missing part counts as 0.
     */
    static boolean newer(String candidate, String installed) {
        if (candidate.equals(installed)) return false;
        String[] a = candidate.split("[.-]");
        String[] b = installed.split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int order = compare(i < a.length ? a[i] : "0", i < b.length ? b[i] : "0");
            if (order != 0) return order > 0;
        }
        return false;
    }

    private static int compare(String x, String y) {
        boolean xNumber = x.matches("\\d{1,18}");
        boolean yNumber = y.matches("\\d{1,18}");
        if (xNumber && yNumber) return Long.compare(Long.parseLong(x), Long.parseLong(y));
        if (xNumber != yNumber) return xNumber ? 1 : -1;
        return x.compareToIgnoreCase(y);
    }
}
