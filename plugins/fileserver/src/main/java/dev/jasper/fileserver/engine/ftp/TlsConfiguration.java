package dev.jasper.fileserver.engine.ftp;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.apache.ftpserver.ssl.ClientAuth;
import org.apache.ftpserver.ssl.SslConfiguration;

/** FtpServer's TLS settings over the shared certificate's context: TLS 1.2, FtpServer's default, and no client certificates. */
final class TlsConfiguration implements SslConfiguration {
    private final SSLContext context;

    TlsConfiguration(SSLContext context) { this.context = context; }

    @Override public SSLSocketFactory getSocketFactory() { return context.getSocketFactory(); }
    @Override public SSLContext getSSLContext() { return context; }
    @Override public SSLContext getSSLContext(String protocol) { return context; }
    @Override public String[] getEnabledCipherSuites() { return null; }
    @Override public String getEnabledProtocol() { return DEFAULT_ENABLED_PROTOCOL; }
    @Override public ClientAuth getClientAuth() { return ClientAuth.NONE; }
}
