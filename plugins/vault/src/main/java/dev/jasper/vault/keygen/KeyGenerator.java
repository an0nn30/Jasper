package dev.jasper.vault.keygen;

import dev.jasper.vault.model.SshKey;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECNamedDomainParameters;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

/**
 * Writes {@code id_<algorithm>_<8 hex>} and its {@code .pub} into a 0700 directory. Ed25519 private keys are
 * in OpenSSH's own format; ECDSA and RSA in the traditional PEM forms OpenSSH reads. Private keys are
 * unencrypted: the vault is the protection, and an account may still record a passphrase set later.
 */
public final class KeyGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path directory;

    public KeyGenerator(Path directory) { this.directory = directory; }

    public SshKey generate(KeyAlgorithm algorithm, String name, String comment) throws IOException {
        UUID id = UUID.randomUUID();
        String base = "id_" + algorithm.id().replace('-', '_') + "_" + id.toString().substring(0, 8);
        Path privatePath = directory.resolve(base), publicPath = directory.resolve(base + ".pub");
        AsymmetricCipherKeyPair pair = pair(algorithm);
        Files.createDirectories(directory);
        permissions(directory, "rwx------");
        try {
            Files.writeString(privatePath, pem(algorithm, OpenSSHPrivateKeyUtil.encodePrivateKey(pair.getPrivate())), StandardCharsets.US_ASCII);
            permissions(privatePath, "rw-------");
            byte[] publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.getPublic());
            String line = algorithm.sshType() + " " + Base64.getEncoder().encodeToString(publicBlob)
                + (comment == null || comment.isBlank() ? "" : " " + comment.strip());
            Files.writeString(publicPath, line + "\n", StandardCharsets.US_ASCII);
            return new SshKey(id, name, algorithm.id(), fingerprint(publicBlob), comment == null ? "" : comment.strip(),
                privatePath.toAbsolutePath(), publicPath.toAbsolutePath(), Instant.now());
        } catch (IOException | RuntimeException failure) {
            for (Path path : java.util.List.of(privatePath, publicPath)) {
                try { Files.deleteIfExists(path); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    public void delete(SshKey key) throws IOException {
        Files.deleteIfExists(key.privatePath());
        Files.deleteIfExists(key.publicPath());
    }

    static String fingerprint(byte[] publicBlob) {
        try { return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicBlob)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static AsymmetricCipherKeyPair pair(KeyAlgorithm algorithm) {
        return switch (algorithm) {
            case ED25519 -> { var generator = new Ed25519KeyPairGenerator(); generator.init(new Ed25519KeyGenerationParameters(RANDOM)); yield generator.generateKeyPair(); }
            case ECDSA_P256 -> ec(SECObjectIdentifiers.secp256r1);
            case ECDSA_P384 -> ec(SECObjectIdentifiers.secp384r1);
            case RSA_3072 -> rsa(3072);
            case RSA_4096 -> rsa(4096);
        };
    }

    private static AsymmetricCipherKeyPair ec(ASN1ObjectIdentifier curve) {
        X9ECParameters x9 = SECNamedCurves.getByOID(curve);
        var generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(new ECNamedDomainParameters(curve, x9), RANDOM));
        return generator.generateKeyPair();
    }

    private static AsymmetricCipherKeyPair rsa(int bits) {
        var generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), RANDOM, bits, 100));
        return generator.generateKeyPair();
    }

    private static String pem(KeyAlgorithm algorithm, byte[] der) {
        String label = switch (algorithm) { case ED25519 -> "OPENSSH PRIVATE KEY"; case ECDSA_P256, ECDSA_P384 -> "EC PRIVATE KEY"; case RSA_3072, RSA_4096 -> "RSA PRIVATE KEY"; };
        String body = Base64.getMimeEncoder(70, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static void permissions(Path path, String posix) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posix));
    }
}
