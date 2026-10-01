package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.http.HttpEngine;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Which protocols this version serves, and the factory that builds their engines. */
public final class Engines {
    public static final Set<Protocol> AVAILABLE = Set.of(Protocol.HTTP);

    private Engines() { }

    public static ServerRegistry.EngineFactory factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls) {
        return (definition, share, login, events) -> switch (definition.protocol()) {
            case HTTP -> new HttpEngine(definition, share, login, events, throttle, nics, tls);
            case FTP, SFTP, TFTP -> throw new EngineFailure(definition.protocol().label() + " servers are not available in this version of File Server");
        };
    }
}
