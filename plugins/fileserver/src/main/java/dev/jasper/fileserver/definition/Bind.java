package dev.jasper.fileserver.definition;

/** Where a server listens: this computer only, every network, or one network interface. */
public sealed interface Bind {
    record Loopback() implements Bind { }
    record All() implements Bind { }
    record Interface(String name) implements Bind {
        public Interface {
            if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace))
                throw new IllegalArgumentException("A network interface needs a name without spaces");
        }
    }

    Bind LOOPBACK = new Loopback();
    Bind ALL = new All();

    default String token() {
        return switch (this) {
            case Loopback loopback -> "loopback";
            case All all -> "all";
            case Interface named -> "interface:" + named.name();
        };
    }

    default String label() {
        return switch (this) {
            case Loopback loopback -> "This computer only";
            case All all -> "All networks";
            case Interface named -> "Interface " + named.name();
        };
    }

    /** Whether two servers bound this way would compete for the same port. */
    default boolean overlaps(Bind other) {
        return this instanceof All || other instanceof All || equals(other);
    }

    static Bind parse(String token) {
        String text = token.strip();
        if (text.equals("loopback")) return LOOPBACK;
        if (text.equals("all")) return ALL;
        if (text.startsWith("interface:")) return new Interface(text.substring("interface:".length()));
        throw new IllegalArgumentException("'bind' must be \"loopback\", \"all\" or \"interface:<name>\"");
    }
}
