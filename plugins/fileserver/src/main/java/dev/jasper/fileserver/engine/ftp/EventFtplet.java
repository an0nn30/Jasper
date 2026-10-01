package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

/**
 * Reports connections, enforces Require TLS before FtpServer runs a command, and ends each transfer with its command's
 * reply: a 226 commits an upload and completes a download, and anything else abandons the transfer. FtpServer calls
 * these methods on the session's own thread. A command that throws skips {@code afterCommand}; its transfer ends
 * with the next one, or when the client leaves.
 */
final class EventFtplet extends DefaultFtplet {
    static final int PROTECTION_REQUIRED = 521;
    private static final Set<String> LOGIN = Set.of("USER", "PASS");
    private static final Set<String> DATA = Set.of("RETR", "STOR", "STOU", "APPE", "LIST", "NLST", "MLSD");
    private static final Set<String> TRANSFERS = Set.of("RETR", "STOR", "STOU", "APPE");
    private final ServerEvents events;
    private final boolean requireTls;

    EventFtplet(ServerEvents events, boolean requireTls) { this.events = events; this.requireTls = requireTls; }

    @Override public FtpletResult onConnect(FtpSession session) {
        events.connected(remote(session));
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult onDisconnect(FtpSession session) {
        if (session.getFileSystemView() instanceof ShareView view) view.finish(false);
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException {
        if (!requireTls) return FtpletResult.DEFAULT;
        String command = command(request);
        if (LOGIN.contains(command) && !session.isSecure()) {
            session.write(new DefaultFtpReply(FtpReply.REPLY_530_NOT_LOGGED_IN, "This server requires TLS: use AUTH TLS before logging in"));
            return FtpletResult.SKIP;
        }
        if (DATA.contains(command) && !session.getDataConnection().isSecure()) {
            session.write(new DefaultFtpReply(PROTECTION_REQUIRED, "This server requires TLS: use PROT P before transferring"));
            return FtpletResult.SKIP;
        }
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply) {
        if (TRANSFERS.contains(command(request)) && session.getFileSystemView() instanceof ShareView view)
            view.finish(reply != null && reply.getCode() == FtpReply.REPLY_226_CLOSING_DATA_CONNECTION);
        return FtpletResult.DEFAULT;
    }

    private static String command(FtpRequest request) {
        return request.getCommand() == null ? "" : request.getCommand().toUpperCase(Locale.ROOT);
    }

    private static String remote(FtpSession session) {
        InetSocketAddress address = session.getClientAddress();
        return address == null || address.getAddress() == null ? "" : ServerAddresses.hostText(address.getAddress());
    }
}
