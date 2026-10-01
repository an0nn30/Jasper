package dev.jasper.fileserver.engine.ftp;

import java.util.ArrayList;
import java.util.List;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;

/**
 * The server's one user after a login, remembering the client address it logged in from. FtpServer's {@code USER}
 * command needs the concurrent-login authority, and transfers need the transfer-rate one; 0 means unlimited. The
 * idle time 0 leaves the listener's timeout in force.
 */
final class ShareUser extends BaseUser {
    private final String remote;

    ShareUser(String name, String remote, boolean writable) {
        this.remote = remote;
        setName(name);
        setHomeDirectory("/");
        setEnabled(true);
        setMaxIdleTime(0);
        var authorities = new ArrayList<Authority>(List.of(new ConcurrentLoginPermission(0, 0), new TransferRatePermission(0, 0)));
        if (writable) authorities.add(new WritePermission());
        setAuthorities(authorities);
    }

    String remote() { return remote; }
}
