package dev.jasper.app.plugins;

/** A one-to-three component numeric version; missing components are zero and pre-release tags are rejected. */
record Version(int major, int minor, int patch) implements Comparable<Version> {
    static Version parse(String text) {
        String[] parts = text == null ? new String[0] : text.strip().split("\\.", -1);
        if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("Invalid version: " + text);
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,8}")) throw new IllegalArgumentException("Invalid version: " + text);
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return new Version(numbers[0], numbers[1], numbers[2]);
    }

    @Override public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result != 0 ? result : Integer.compare(patch, other.patch);
    }

    @Override public String toString() { return major + "." + minor + "." + patch; }
}
