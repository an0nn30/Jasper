package dev.jasper.fileserver.definition;

import java.util.Locale;

/** The protocols File Server knows; FS-1 serves HTTP only. */
public enum Protocol {
    HTTP("HTTP", "http", 8080), FTP("FTP", "ftp", 2121), SFTP("SFTP", "sftp", 2222), TFTP("TFTP", "tftp", 6969);

    private final String label, scheme;
    private final int defaultPort;

    Protocol(String label, String scheme, int defaultPort) { this.label = label; this.scheme = scheme; this.defaultPort = defaultPort; }

    public String label() { return label; }
    public String scheme() { return scheme; }
    public String token() { return scheme; }
    public int defaultPort() { return defaultPort; }
    /** HTTP and FTP may serve without a login; TFTP has none. */
    public boolean allowsAnonymous() { return this == HTTP || this == FTP || this == TFTP; }
    public boolean hasLogin() { return this != TFTP; }
    public boolean allowsTls() { return this == HTTP || this == FTP; }

    /** A port a normal user can bind: below 1024, 8000 higher (21 becomes 8021); otherwise this protocol's default. */
    public int suggestedPort(int requested) { return requested < 1024 ? requested + 8000 : defaultPort; }

    public static Protocol parse(String token) {
        String lower = token.strip().toLowerCase(Locale.ROOT);
        for (Protocol protocol : values()) if (protocol.scheme.equals(lower)) return protocol;
        throw new IllegalArgumentException("'protocol' must be \"http\", \"ftp\", \"sftp\" or \"tftp\"");
    }
}
