package dev.jasper.vault.keygen;

/** The algorithms the generator offers; {@code id} is stored in {@link dev.jasper.vault.model.SshKey#algorithm()}. */
public enum KeyAlgorithm {
    ED25519("ed25519", "Ed25519 (recommended)", "ssh-ed25519"),
    ECDSA_P256("ecdsa-p256", "ECDSA P-256", "ecdsa-sha2-nistp256"),
    ECDSA_P384("ecdsa-p384", "ECDSA P-384", "ecdsa-sha2-nistp384"),
    RSA_3072("rsa-3072", "RSA 3072", "ssh-rsa"),
    RSA_4096("rsa-4096", "RSA 4096", "ssh-rsa");

    private final String id, label, sshType;

    KeyAlgorithm(String id, String label, String sshType) { this.id = id; this.label = label; this.sshType = sshType; }

    public String id() { return id; }
    public String label() { return label; }
    /** The type word that starts a public-key line. */
    public String sshType() { return sshType; }
    @Override public String toString() { return label; }
}
