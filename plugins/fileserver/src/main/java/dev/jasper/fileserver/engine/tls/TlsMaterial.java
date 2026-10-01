package dev.jasper.fileserver.engine.tls;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * The self-signed certificate HTTPS and FTPS servers present: an EC P-256 key and a certificate valid for ten years,
 * kept in one folder as {@code server.p12} (PKCS#12) with its random password in {@code password}, both readable by
 * the owner only where the file system allows. It is created on first use, and {@link #regenerate()} replaces it.
 * Running servers keep the context they started with.
 *
 * <p>Thread-safe. Every method does file I/O except {@link #cachedFingerprint()}, so call them off the UI thread.
 */
public final class TlsMaterial {
    static final String ALIAS = "jasper-file-server";
    static final Duration VALIDITY = Duration.ofDays(3650);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path directory;
    private final Clock clock;
    private final Supplier<String> hostName;
    private KeyStore store;
    private char[] password;
    private volatile String fingerprint;

    public TlsMaterial(Path directory, Clock clock, Supplier<String> hostName) {
        this.directory = directory;
        this.clock = clock;
        this.hostName = hostName;
    }

    /** A TLS context presenting the certificate, created if there is none yet. */
    public synchronized SSLContext context() throws EngineFailure {
        KeyStore keys = load(true);
        try {
            var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password);
            var context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException failure) {
            throw unreadable(failure);
        }
    }

    /** The certificate, created if there is none yet. */
    public synchronized X509Certificate certificate() throws EngineFailure {
        try { return (X509Certificate) load(true).getCertificate(ALIAS); }
        catch (GeneralSecurityException failure) { throw unreadable(failure); }
    }

    /** The existing certificate's fingerprint; empty when none was created yet. Never creates one. */
    public synchronized Optional<String> fingerprint() throws EngineFailure {
        return load(false) == null ? Optional.empty() : Optional.of(fingerprint);
    }

    /** The fingerprint already known in memory, without touching the disk. */
    public Optional<String> cachedFingerprint() { return Optional.ofNullable(fingerprint); }

    /** Replaces the certificate with a new one. */
    public synchronized void regenerate() throws EngineFailure {
        store = null;
        password = null;
        fingerprint = null;
        try {
            Files.deleteIfExists(directory.resolve("server.p12"));
            Files.deleteIfExists(directory.resolve("password"));
        } catch (IOException failure) {
            throw new EngineFailure("Could not remove the TLS certificate in " + directory + ": " + failure.getMessage(), failure);
        }
        create();
    }

    /** SHA-256 of the encoded certificate in uppercase hex pairs joined by colons. */
    public static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private KeyStore load(boolean createIfMissing) throws EngineFailure {
        if (store != null) return store;
        Path keystore = directory.resolve("server.p12"), secret = directory.resolve("password");
        if (Files.exists(keystore) && Files.exists(secret)) {
            try {
                char[] pass = Files.readString(secret, StandardCharsets.UTF_8).strip().toCharArray();
                KeyStore keys = KeyStore.getInstance("PKCS12");
                try (InputStream in = Files.newInputStream(keystore)) { keys.load(in, pass); }
                if (!(keys.getCertificate(ALIAS) instanceof X509Certificate certificate) || !(keys.getKey(ALIAS, pass) instanceof PrivateKey))
                    throw new GeneralSecurityException("The keystore has no key entry " + ALIAS);
                return remember(keys, pass, certificate);
            } catch (IOException | GeneralSecurityException failure) {
                throw unreadable(failure);
            }
        }
        return createIfMissing ? create() : null;
    }

    private KeyStore create() throws EngineFailure {
        try {
            Files.createDirectories(directory);
            ownerOnly(directory, "rwx------");
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            Instant now = clock.instant();
            var subject = new X500Name("CN=Jasper File Server");
            var builder = new JcaX509v3CertificateBuilder(subject, new BigInteger(64, RANDOM).add(BigInteger.ONE),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(VALIDITY)), subject, pair.getPublic());
            var names = new ArrayList<>(List.of(new GeneralName(GeneralName.dNSName, "localhost"), new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
            String host = hostName.get();
            if (host != null && !host.isBlank() && !host.equalsIgnoreCase("localhost")) names.add(new GeneralName(GeneralName.dNSName, host));
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(GeneralName[]::new)));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
            byte[] random = new byte[24];
            RANDOM.nextBytes(random);
            char[] pass = HexFormat.of().formatHex(random).toCharArray();
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(null, null);
            keys.setKeyEntry(ALIAS, pair.getPrivate(), pass, new Certificate[]{certificate});
            write(directory.resolve("password"), out -> out.write(new String(pass).getBytes(StandardCharsets.UTF_8)));
            write(directory.resolve("server.p12"), out -> keys.store(out, pass));
            return remember(keys, pass, certificate);
        } catch (IOException | GeneralSecurityException | OperatorCreationException failure) {
            throw new EngineFailure("Could not create the TLS certificate in " + directory + ": " + failure.getMessage(), failure);
        }
    }

    private KeyStore remember(KeyStore keys, char[] pass, X509Certificate certificate) throws CertificateEncodingException {
        store = keys;
        password = pass;
        fingerprint = fingerprint(certificate);
        return keys;
    }

    @FunctionalInterface private interface Writer { void write(OutputStream out) throws IOException, GeneralSecurityException; }

    /** Writes a file owner-only, through a temporary sibling moved into place. */
    private static void write(Path file, Writer content) throws IOException, GeneralSecurityException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.deleteIfExists(temp);
        Files.createFile(temp);
        ownerOnly(temp, "rw-------");
        try (OutputStream out = Files.newOutputStream(temp)) { content.write(out); }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void ownerOnly(Path path, String permissions) {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions)); }
        catch (UnsupportedOperationException | IOException notPosix) { /* Windows: the user's profile folder protects it */ }
    }

    private EngineFailure unreadable(Exception failure) {
        return new EngineFailure("The TLS certificate in " + directory + " can't be read", failure);
    }
}
