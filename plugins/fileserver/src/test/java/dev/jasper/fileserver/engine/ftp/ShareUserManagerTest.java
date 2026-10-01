package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.UserMetadata;
import org.apache.ftpserver.usermanager.impl.WriteRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareUserManagerTest {
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();

    ShareUserManager manager(ResolvedLogin login, boolean writable) {
        return new ShareUserManager(login, writable, throttle, new ServerEvents(clock, events::add));
    }

    static UserMetadata from(String address) throws Exception {
        var metadata = new UserMetadata();
        metadata.setInetAddress(InetAddress.getByName(address));
        return metadata;
    }

    @Test void anAnonymousServerAcceptsOnlyAnonymous() throws Exception {
        var users = manager(ResolvedLogin.anonymous(), false);
        var user = (ShareUser) users.authenticate(new AnonymousAuthentication(from("10.0.0.2")));
        assertThat(user.getName()).isEqualTo("anonymous");
        assertThat(user.remote()).isEqualTo("10.0.0.2");
        assertThat(user.authorize(new WriteRequest())).as("read-only").isNull();
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2"))))
            .isInstanceOf(AuthenticationFailedException.class);
        assertThat(users.getUserByName("anonymous")).isNotNull();
        assertThat(users.getUserByName("dustin")).isNull();
        assertThat(users.getAllUserNames()).containsExactly("anonymous");
        assertThat(users.isAdmin("anonymous")).isFalse();
        assertThat(users.getAdminName()).isEmpty();
        assertThat(events).singleElement().isInstanceOf(ServerEvent.LoginSucceeded.class);
    }

    @Test void aPasswordServerChecksThroughTheThrottle() throws Exception {
        var users = manager(ResolvedLogin.password("dustin", "pw".toCharArray()), true);
        assertThatThrownBy(() -> users.authenticate(new AnonymousAuthentication(from("10.0.0.2")))).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "bad", from("10.0.0.2"))))
            .isInstanceOf(AuthenticationFailedException.class);
        assertThat(events).singleElement().isEqualTo(new ServerEvent.LoginFailed(clock.instant(), "10.0.0.2", "dustin"));
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2"))))
            .as("the address must wait before trying again").isInstanceOf(AuthenticationFailedException.class);
        assertThat(events).as("a refused attempt is not checked or logged").hasSize(1);
        var other = (ShareUser) users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.3")));
        assertThat(other.remote()).isEqualTo("10.0.0.3");
        clock.advance(Duration.ofSeconds(2));
        var user = (ShareUser) users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2")));
        assertThat(user.getName()).isEqualTo("dustin");
        assertThat(user.authorize(new WriteRequest())).as("writable").isNotNull();
        assertThat(throttle.retryAfter("10.0.0.2")).isZero();
        assertThat(users.getUserByName("dustin")).isNotNull();
        assertThat(users.getUserByName("anonymous")).isNull();
    }
}
