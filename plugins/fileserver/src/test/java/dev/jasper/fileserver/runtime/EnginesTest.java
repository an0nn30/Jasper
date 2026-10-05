package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.ftp.FtpEngine;
import dev.jasper.fileserver.engine.http.HttpEngine;
import dev.jasper.fileserver.engine.sftp.HostKey;
import dev.jasper.fileserver.engine.sftp.SftpEngine;
import dev.jasper.fileserver.engine.tftp.TftpEngine;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EnginesTest {
    @Test void theFactoryBuildsEveryEngine(@TempDir Path folder) throws Exception {
        var factory = Engines.factory(new LoginThrottle(new TestClock()), List::of, new TlsMaterial(folder.resolve("tls"), new TestClock(), () -> ""),
            () -> new PortRange(50000, 50100), new HostKey(folder.resolve("hostkey")));
        var events = new ServerEvents(new TestClock(), e -> { });
        var share = new ShareRoot(folder, true);
        assertThat(Engines.AVAILABLE).containsExactlyInAnyOrder(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP);
        assertThat(factory.create(ServerDefinition.create("h", Protocol.HTTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(HttpEngine.class);
        assertThat(factory.create(ServerDefinition.create("f", Protocol.FTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(FtpEngine.class);
        assertThat(factory.create(ServerDefinition.create("s", Protocol.SFTP, folder), share, ResolvedLogin.password("u", "p".toCharArray()), events))
            .isInstanceOf(SftpEngine.class);
        assertThat(factory.create(ServerDefinition.create("t", Protocol.TFTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(TftpEngine.class);
    }
}
