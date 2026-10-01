package dev.jasper.fileserver.definition;

/** An inclusive range of ports, such as FTP's passive data ports. */
public record PortRange(int first, int last) {
    public PortRange {
        if (first < 1 || last > 65535 || first > last)
            throw new IllegalArgumentException("A port range runs from a lower to a higher port, 1 to 65535");
    }

    public static PortRange parse(String text) {
        int dash = text.indexOf('-');
        if (dash < 0) throw new IllegalArgumentException("A port range looks like 50000-50100");
        try { return new PortRange(Integer.parseInt(text.substring(0, dash).strip()), Integer.parseInt(text.substring(dash + 1).strip())); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("A port range looks like 50000-50100"); }
    }

    public String token() { return first + "-" + last; }
}
