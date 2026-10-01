package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP Basic auth against one login, through the shared throttle; a successful login is logged once per client and user. */
final class ThrottledAuthenticator extends Authenticator {
    private final String realm;
    private final ResolvedLogin login;
    private final LoginThrottle throttle;
    private final ServerEvents events;
    private final Set<String> welcomed = ConcurrentHashMap.newKeySet();

    ThrottledAuthenticator(String realm, ResolvedLogin login, LoginThrottle throttle, ServerEvents events) {
        this.realm = realm.replaceAll("[\"\\\\\\p{Cntrl}]", "'");
        this.login = login; this.throttle = throttle; this.events = events;
    }

    @Override public Result authenticate(HttpExchange exchange) {
        String remote = FileHandler.remote(exchange);
        if (throttle.lockedOut(remote)) return new Failure(429);
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return challenge(exchange);
        String user;
        char[] password;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) return challenge(exchange);
            user = decoded.substring(0, colon);
            password = decoded.substring(colon + 1).toCharArray();
        } catch (IllegalArgumentException malformed) {
            return challenge(exchange);
        }
        try {
            Thread.sleep(throttle.delayFor(remote));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Failure(503);
        }
        boolean matches = login.matches(user, password);
        Arrays.fill(password, '\0');
        if (matches) {
            throttle.succeeded(remote);
            if (welcomed.add(remote + "\n" + user)) events.loginSucceeded(remote, user);
            return new Success(new HttpPrincipal(user, realm));
        }
        throttle.failed(remote);
        events.loginFailed(remote, user);
        return challenge(exchange);
    }

    private Result challenge(HttpExchange exchange) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + realm + "\", charset=\"UTF-8\"");
        return new Retry(401);
    }
}
