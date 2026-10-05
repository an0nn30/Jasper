package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * The server's logins.
 * <ul>
 * <li>Passwords go through the shared {@link LoginThrottle}: an address that must wait is refused without its
 *     password being checked, and a wrong password counts.</li>
 * <li>Offered keys that are not authorized do not count, because agents offer several in turn. A locked-out address
 *     is still refused keys.</li>
 * <li>With a password login, keys are for that user; a keys-only server takes any user name.</li>
 * <li>A success is reported once, when the session authenticates, because sshd asks the key check twice per
 *     login.</li>
 * </ul>
 * Every method returns false rather than throwing, because sshd logs a stack trace for an exception.
 */
final class SftpLogins implements PasswordAuthenticator, PublickeyAuthenticator, SessionListener {
    private final ResolvedLogin login;
    private final KeyCheck keys;
    private final LoginThrottle throttle;
    private final ServerEvents events;

    SftpLogins(ResolvedLogin login, KeyCheck keys, LoginThrottle throttle, ServerEvents events) {
        this.login = login; this.keys = keys; this.throttle = throttle; this.events = events;
    }

    @Override public boolean authenticate(String username, String password, ServerSession session) {
        String remote = remote(session);
        if (login.isAnonymous() || throttle.retryAfter(remote).compareTo(Duration.ZERO) > 0) return false;
        char[] candidate = password == null ? new char[0] : password.toCharArray();
        try {
            if (login.matches(username, candidate)) return true;
        } finally {
            Arrays.fill(candidate, '\0');
        }
        throttle.failed(remote);
        events.loginFailed(remote, String.valueOf(username));
        return false;
    }

    @Override public boolean authenticate(String username, PublicKey key, ServerSession session) {
        if (throttle.lockedOut(remote(session))) return false;
        if (login.user().isPresent() && !login.user().get().equals(username)) return false;
        return keys.accepts(key);
    }

    @Override public void sessionCreated(Session session) { events.connected(remote(session)); }

    @Override public void sessionEvent(Session session, Event event) {
        if (event != Event.Authenticated) return;
        String remote = remote(session);
        throttle.succeeded(remote);
        events.loginSucceeded(remote, String.valueOf(session.getUsername()));
    }

    static String remote(Session session) {
        SocketAddress address = session instanceof ServerSession server ? server.getClientAddress() : null;
        return address instanceof InetSocketAddress inet && inet.getAddress() != null ? ServerAddresses.hostText(inet.getAddress()) : "";
    }
}
