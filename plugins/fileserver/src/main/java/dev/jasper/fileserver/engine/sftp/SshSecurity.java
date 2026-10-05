package dev.jasper.fileserver.engine.sftp;

import java.security.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Makes sshd use this plugin's own BouncyCastle provider. Each plugin loads its own BouncyCastle classes, and a
 * JVM-wide provider named "BC" may belong to another plugin's loader and hand out keys of the wrong classes. Remote
 * does the same. Also quiets sshd's INFO logging (a line per login); warnings still reach the log.
 */
final class SshSecurity {
    /** Held so the logger's level is not lost to garbage collection. */
    private static final Logger SSHD_LOG = Logger.getLogger("org.apache.sshd");

    static {
        SSHD_LOG.setLevel(Level.WARNING);
        SecurityUtils.registerSecurityProvider(new BouncyCastleSecurityProviderRegistrar() {
            private final Provider local = new BouncyCastleProvider();
            @Override public boolean isNamedProviderUsed() { return false; }
            @Override public Provider getSecurityProvider() { return local; }
        });
    }

    private SshSecurity() { }

    /** Runs the registration once; later calls do nothing. */
    static void ensure() { /* the class initialiser did the work */ }
}
