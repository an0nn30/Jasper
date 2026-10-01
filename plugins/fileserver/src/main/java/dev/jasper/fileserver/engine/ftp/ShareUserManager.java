package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.time.Duration;
import java.util.Arrays;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.UserMetadata;

/**
 * The server's one login: the anonymous user, or the resolved user name and password. Every attempt goes through the
 * shared {@link LoginThrottle}. An address that must wait, or is locked out, is refused without its password being
 * checked, as HTTP answers 429. FtpServer adds its own half-second pause after each failure and drops the
 * connection after three. Nobody is an administrator, and users cannot be changed.
 */
final class ShareUserManager implements UserManager {
    private final ResolvedLogin login;
    private final boolean writable;
    private final LoginThrottle throttle;
    private final ServerEvents events;

    ShareUserManager(ResolvedLogin login, boolean writable, LoginThrottle throttle, ServerEvents events) {
        this.login = login; this.writable = writable; this.throttle = throttle; this.events = events;
    }

    private String expectedName() { return login.user().orElse(ANONYMOUS); }

    @Override public User getUserByName(String name) {
        return expectedName().equals(name) ? new ShareUser(name, "", writable) : null;
    }

    @Override public String[] getAllUserNames() { return new String[]{expectedName()}; }
    @Override public void delete(String name) throws FtpException { throw new FtpException("File Server's users cannot be changed"); }
    @Override public void save(User user) throws FtpException { throw new FtpException("File Server's users cannot be changed"); }
    @Override public boolean doesExist(String name) { return getUserByName(name) != null; }
    @Override public String getAdminName() { return ""; }
    @Override public boolean isAdmin(String name) { return false; }

    @Override public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        String remote = remote(authentication);
        if (throttle.retryAfter(remote).compareTo(Duration.ZERO) > 0) throw new AuthenticationFailedException("Too many failed logins; try again later");
        if (authentication instanceof AnonymousAuthentication && login.isAnonymous()) {
            events.loginSucceeded(remote, ANONYMOUS);
            return new ShareUser(ANONYMOUS, remote, writable);
        }
        if (authentication instanceof UsernamePasswordAuthentication attempt && !login.isAnonymous()) {
            String user = attempt.getUsername();
            char[] password = attempt.getPassword() == null ? new char[0] : attempt.getPassword().toCharArray();
            try {
                if (login.matches(user, password)) {
                    throttle.succeeded(remote);
                    events.loginSucceeded(remote, user);
                    return new ShareUser(user, remote, writable);
                }
            } finally {
                Arrays.fill(password, '\0');
            }
            throttle.failed(remote);
            events.loginFailed(remote, String.valueOf(user));
            throw new AuthenticationFailedException("Wrong user name or password");
        }
        throw new AuthenticationFailedException("This server does not accept that login");
    }

    private static String remote(Authentication authentication) {
        UserMetadata metadata = switch (authentication) {
            case AnonymousAuthentication anonymous -> anonymous.getUserMetadata();
            case UsernamePasswordAuthentication attempt -> attempt.getUserMetadata();
            default -> null;
        };
        return metadata == null || metadata.getInetAddress() == null ? "" : ServerAddresses.hostText(metadata.getInetAddress());
    }
}
