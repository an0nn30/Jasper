# Credential Vault Plan 6a — Core and API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bundled `dev.jasper.vault` plugin that stores accounts, SSH keys and notes in one Argon2id/AES-GCM file bound to the device, locks and unlocks, generates SSH keys, and serves other plugins through a per-consumer `VaultApi` with grants — with the create, unlock, grant and picker dialogs as its only UI.

**Architecture:** Pure layers with no UI dependency (`crypto`, `model`, `store`, `lock`, `keygen`) under a `service` layer that owns the request queue (one prompt serves every waiter; cancelling the last waiter dismisses it) and hands each consumer a `VaultApi` view. `VaultPlugin` wires those layers to the SDK: background executor for Argon2 and file I/O, UI executor for completion, dialogs for the prompts, actions and a lock-state topic. Every secret is a `byte[]`/`char[]` that is zeroed after use.

**Tech Stack:** Java 25 (JBR), `jasper-sdk` 0.7 (`compileOnly`), BouncyCastle `bcprov-jdk18on:1.85.2` (bundled, lightweight API only: Argon2, key generation, OpenSSH encoding), JDK `javax.crypto` AES/GCM, JUnit 6 + AssertJ, `jasper-sdk-testkit` (`FakePluginHost`).

**Spec:** `docs/superpowers/specs/2026-09-22-jasper-vault-design.md`

## Global Constraints

- The plugin compiles against `jasper-sdk` and its bundled `bcprov` only; no app or terminal imports (`verifyPluginArchitecture`).
- Plugin id `dev.jasper.vault`; exported package `dev.jasper.vault.api`; sdk range `>=0.7, <0.8`.
- File: `<data>/vault.jv` = `"JASPERVLT"` (9 bytes) | version `u16` LE = 1 | flags `u8` (bit 0 = device-bound) | salt 16 | nonce 12 | ciphertext; header is the GCM AAD; tag 128 bits.
- KDF: Argon2id, 65536 KiB, 3 iterations, 4 lanes, 32-byte key, over `password || deviceSecret` (password alone when unbound).
- No `String` ever holds a secret; every `byte[]`/`char[]` secret is zeroed in `finally`.
- All `VaultApi` methods are called on the UI thread and their futures complete on the UI thread; Argon2 and file I/O run on `context.background()`.
- Never launch the GUI; tests are headless; commit per task with the `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer; never commit on `main` (work in `.worktrees/vault-6a`, branch `claude/vault-6a`).
- No raw control, private-use or surrogate characters in source.
- Run `./gradlew :jasper-plugin-vault:test -q` per task; `./gradlew check -q` before the final commit.

---

### Task 1: Module, descriptor and the crypto primitives

**Files:**
- Create: `plugins/vault/build.gradle.kts`, `plugins/vault/src/main/resources/plugin.toml`, `plugins/vault/src/main/resources/settings.toml`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/package-info.java`, `.../vault/crypto/package-info.java`, `.../vault/crypto/SecureBytes.java`, `.../vault/crypto/KeyDerivation.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/crypto/SecureBytesTest.java`, `KeyDerivationTest.java`

**Interfaces:**
- Produces: `SecureBytes.utf8(char[]) -> byte[]`, `SecureBytes.chars(byte[]) -> char[]`, `SecureBytes.zero(byte[])`, `SecureBytes.zero(char[])`, `SecureBytes.concat(byte[], byte[]) -> byte[]`; `KeyDerivation.derive(byte[] password, byte[] deviceSecretOrNull, byte[] salt16) -> byte[32]`; constants `KeyDerivation.KEY_LENGTH = 32`, `SALT_LENGTH = 16`.

- [ ] **Step 1: Create the module files**

`plugins/vault/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. BouncyCastle is
// bundled for Argon2id and OpenSSH key encoding: stagePlugins copies the runtime classpath beside
// the plugin jar, and PluginClassLoader loads it. Only the lightweight API is used; no JCA provider.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

`plugins/vault/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.vault"
name = "Credential Vault"
version = "0.1.0"
entry = "dev.jasper.vault.VaultPlugin"
sdk = ">=0.7, <0.8"
description = "Accounts, SSH keys and secure notes in one encrypted file bound to this device; other plugins fetch credentials under per-plugin grants."
vendor = "Jasper"
capabilities = []
exports = ["dev.jasper.vault.api"]
```

`plugins/vault/src/main/resources/settings.toml`:

```toml
# Settings for Credential Vault. Jasper reads this file live.
# auto_lock_minutes = 15            # lock after this many minutes without keyboard or mouse activity; 0 never locks automatically
# keys_directory = ""               # where generated SSH keys go; empty means plugins/dev.jasper.vault/data/keys
# bind_new_vaults_to_device = true  # the default state of "Bind to this device" when creating a vault
```

`plugins/vault/src/main/java/dev/jasper/vault/package-info.java`:

```java
/** The Credential Vault plugin: {@link dev.jasper.vault.VaultPlugin} wires the pure layers to the SDK. */
package dev.jasper.vault;
```

`plugins/vault/src/main/java/dev/jasper/vault/crypto/package-info.java`:

```java
/** Key derivation, the file format and the cipher. Pure functions over byte arrays; no I/O, no UI. */
package dev.jasper.vault.crypto;
```

- [ ] **Step 2: Write the failing tests**

`plugins/vault/src/test/java/dev/jasper/vault/crypto/SecureBytesTest.java`:

```java
package dev.jasper.vault.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecureBytesTest {
    @Test void utf8RoundTripsAndZeroes() {
        char[] text = "pässword é".toCharArray();
        byte[] bytes = SecureBytes.utf8(text);
        assertThat(bytes).isEqualTo("pässword é".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        char[] back = SecureBytes.chars(bytes);
        assertThat(back).isEqualTo(text);
        SecureBytes.zero(bytes);
        SecureBytes.zero(back);
        assertThat(bytes).containsOnly((byte) 0);
        assertThat(back).containsOnly((char) 0);
    }

    @Test void concatCopiesBothOperands() {
        byte[] a = {1, 2}, b = {3};
        assertThat(SecureBytes.concat(a, b)).containsExactly(1, 2, 3);
        assertThat(SecureBytes.concat(a, null)).containsExactly(1, 2);
    }

    @Test void zeroToleratesNull() {
        SecureBytes.zero((byte[]) null);
        SecureBytes.zero((char[]) null);
    }
}
```

`plugins/vault/src/test/java/dev/jasper/vault/crypto/KeyDerivationTest.java`:

```java
package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyDerivationTest {
    static final byte[] SALT = new byte[16];
    static { Arrays.fill(SALT, (byte) 7); }

    @Test void isDeterministicAndThirtyTwoBytes() {
        byte[] first = KeyDerivation.derive("pw".getBytes(), null, SALT);
        byte[] second = KeyDerivation.derive("pw".getBytes(), null, SALT);
        assertThat(first).hasSize(KeyDerivation.KEY_LENGTH).isEqualTo(second);
    }

    @Test void deviceSecretSaltAndPasswordAllChangeTheKey() {
        byte[] base = KeyDerivation.derive("pw".getBytes(), null, SALT);
        byte[] device = new byte[32]; Arrays.fill(device, (byte) 1);
        assertThat(KeyDerivation.derive("pw".getBytes(), device, SALT)).isNotEqualTo(base);
        assertThat(KeyDerivation.derive("pX".getBytes(), null, SALT)).isNotEqualTo(base);
        byte[] otherSalt = new byte[16]; otherSalt[0] = 1;
        assertThat(KeyDerivation.derive("pw".getBytes(), null, otherSalt)).isNotEqualTo(base);
    }

    @Test void rejectsAWrongSaltLength() {
        assertThatThrownBy(() -> KeyDerivation.derive("pw".getBytes(), null, new byte[8]))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("16");
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure, `SecureBytes` and `KeyDerivation` missing.

- [ ] **Step 4: Implement**

`plugins/vault/src/main/java/dev/jasper/vault/crypto/SecureBytes.java`:

```java
package dev.jasper.vault.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Helpers that keep secrets in arrays: UTF-8 both ways with the intermediate buffers zeroed, and zeroing. */
public final class SecureBytes {
    private SecureBytes() { }

    /** The UTF-8 bytes of {@code text}; the caller owns and zeroes the result. */
    public static byte[] utf8(char[] text) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(text));
        byte[] out = new byte[encoded.remaining()];
        encoded.get(out);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        return out;
    }

    /** The characters of UTF-8 {@code bytes}; the caller owns and zeroes the result. */
    public static char[] chars(byte[] bytes) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] out = new char[decoded.remaining()];
        decoded.get(out);
        if (decoded.hasArray()) Arrays.fill(decoded.array(), (char) 0);
        return out;
    }

    /** {@code first || second}; a null {@code second} contributes nothing. The caller zeroes the result. */
    public static byte[] concat(byte[] first, byte[] second) {
        if (second == null) return Arrays.copyOf(first, first.length);
        byte[] out = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    /** Overwrites with zeros; null is ignored. */
    public static void zero(byte[] secret) { if (secret != null) Arrays.fill(secret, (byte) 0); }

    /** Overwrites with zeros; null is ignored. */
    public static void zero(char[] secret) { if (secret != null) Arrays.fill(secret, (char) 0); }
}
```

`plugins/vault/src/main/java/dev/jasper/vault/crypto/KeyDerivation.java`:

```java
package dev.jasper.vault.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id with the spec's parameters: 64 MiB, 3 iterations, 4 lanes, a 32-byte key from a 16-byte salt. */
public final class KeyDerivation {
    public static final int KEY_LENGTH = 32;
    public static final int SALT_LENGTH = 16;
    static final int MEMORY_KIB = 65536;
    static final int ITERATIONS = 3;
    static final int LANES = 4;

    private KeyDerivation() { }

    /**
     * The key for {@code password || deviceSecret} ({@code deviceSecret} may be null for an unbound
     * vault). Takes about a quarter of a second; never call it on the UI thread. The caller zeroes the result.
     */
    public static byte[] derive(byte[] password, byte[] deviceSecret, byte[] salt) {
        if (salt.length != SALT_LENGTH) throw new IllegalArgumentException("The salt must be " + SALT_LENGTH + " bytes, not " + salt.length);
        byte[] material = SecureBytes.concat(password, deviceSecret);
        try {
            var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_KIB).withIterations(ITERATIONS).withParallelism(LANES)
                .withSalt(salt).build();
            var generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] key = new byte[KEY_LENGTH];
            generator.generateBytes(material, key);
            return key;
        } finally {
            SecureBytes.zero(material);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS (7 tests). `settings.gradle.kts` discovers `plugins/vault` as `:jasper-plugin-vault` automatically.

- [ ] **Step 6: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): start the Credential Vault plugin with Argon2id key derivation and secret helpers"
```

---

### Task 2: File format and cipher

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/crypto/VaultFileFormat.java`, `VaultCipher.java`, `WrongPasswordException.java`, `CorruptVaultException.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/crypto/VaultFileFormatTest.java`, `VaultCipherTest.java`

**Interfaces:**
- Consumes: `KeyDerivation`, `SecureBytes`.
- Produces: `VaultFileFormat.Header(boolean bound, byte[] salt, byte[] nonce)` with `byte[] encode()`; `VaultFileFormat.Parsed(Header header, byte[] ciphertext)`; `VaultFileFormat.parse(byte[]) -> Parsed` (throws `CorruptVaultException`); `VaultFileFormat.assemble(Header, byte[] ciphertext) -> byte[]`; `VaultFileFormat.NONCE_LENGTH = 12`; `VaultCipher.seal(byte[] key, byte[] salt, boolean bound, byte[] plaintext) -> byte[] file` (random nonce); `VaultCipher.open(byte[] key, Parsed) -> byte[] plaintext` (throws `WrongPasswordException` on a bad tag); `VaultCipher.randomSalt() -> byte[16]`.

- [ ] **Step 1: Write the failing tests**

`plugins/vault/src/test/java/dev/jasper/vault/crypto/VaultFileFormatTest.java`:

```java
package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultFileFormatTest {
    static final byte[] SALT = new byte[16], NONCE = new byte[12];
    static { Arrays.fill(SALT, (byte) 5); Arrays.fill(NONCE, (byte) 9); }

    @Test void roundTripsHeaderAndCiphertext() {
        var header = new VaultFileFormat.Header(true, SALT, NONCE);
        byte[] file = VaultFileFormat.assemble(header, new byte[] {1, 2, 3});
        assertThat(file).startsWith("JASPERVLT".getBytes()).hasSize(9 + 2 + 1 + 16 + 12 + 3);
        assertThat(file[9]).isEqualTo((byte) 1);
        assertThat(file[10]).isEqualTo((byte) 0);
        assertThat(file[11]).as("flags: bound").isEqualTo((byte) 1);
        VaultFileFormat.Parsed parsed = VaultFileFormat.parse(file);
        assertThat(parsed.header()).isEqualTo(header);
        assertThat(parsed.ciphertext()).containsExactly(1, 2, 3);
        assertThat(parsed.header().encode()).isEqualTo(Arrays.copyOf(file, VaultFileFormat.HEADER_LENGTH));
        assertThat(new VaultFileFormat.Header(false, SALT, NONCE).encode()[11]).isEqualTo((byte) 0);
    }

    @Test void rejectsWrongMagicVersionAndLengths() {
        byte[] file = VaultFileFormat.assemble(new VaultFileFormat.Header(false, SALT, NONCE), new byte[16]);
        byte[] magic = file.clone(); magic[0] = 'X';
        assertThatThrownBy(() -> VaultFileFormat.parse(magic)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("not a Jasper vault");
        byte[] version = file.clone(); version[9] = 2;
        assertThatThrownBy(() -> VaultFileFormat.parse(version)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("version 2");
        assertThatThrownBy(() -> VaultFileFormat.parse(Arrays.copyOf(file, 20))).isInstanceOf(CorruptVaultException.class);
        assertThatThrownBy(() -> new VaultFileFormat.Header(false, new byte[3], NONCE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VaultFileFormat.Header(false, SALT, new byte[3])).isInstanceOf(IllegalArgumentException.class);
    }
}
```

`plugins/vault/src/test/java/dev/jasper/vault/crypto/VaultCipherTest.java`:

```java
package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultCipherTest {
    static final byte[] KEY = new byte[32], OTHER = new byte[32], SALT = new byte[16];
    static { Arrays.fill(KEY, (byte) 1); Arrays.fill(OTHER, (byte) 2); }

    @Test void sealsAndOpensWithAFreshNoncePerCall() {
        byte[] first = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        byte[] second = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        assertThat(first).isNotEqualTo(second);
        VaultFileFormat.Parsed parsed = VaultFileFormat.parse(first);
        assertThat(parsed.header().bound()).isTrue();
        assertThat(parsed.header().salt()).isEqualTo(SALT);
        assertThat(VaultCipher.open(KEY, parsed)).isEqualTo("hello".getBytes());
        assertThat(VaultCipher.open(KEY, VaultFileFormat.parse(second))).isEqualTo("hello".getBytes());
    }

    @Test void aWrongKeyOrATouchedHeaderFailsTheTag() {
        byte[] file = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        assertThatThrownBy(() -> VaultCipher.open(OTHER, VaultFileFormat.parse(file))).isInstanceOf(WrongPasswordException.class);
        byte[] unbound = file.clone(); unbound[11] = 0;
        assertThatThrownBy(() -> VaultCipher.open(KEY, VaultFileFormat.parse(unbound))).as("flags are authenticated").isInstanceOf(WrongPasswordException.class);
        byte[] body = file.clone(); body[body.length - 1] ^= 1;
        assertThatThrownBy(() -> VaultCipher.open(KEY, VaultFileFormat.parse(body))).isInstanceOf(WrongPasswordException.class);
    }

    @Test void randomSaltsDiffer() {
        assertThat(VaultCipher.randomSalt()).hasSize(16).isNotEqualTo(VaultCipher.randomSalt());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`WrongPasswordException.java`:

```java
package dev.jasper.vault.crypto;

/** The authentication tag failed: a wrong password, a foreign device secret or a modified file. */
public final class WrongPasswordException extends RuntimeException {
    public WrongPasswordException() { super("The password is wrong or the vault file was modified"); }
}
```

`CorruptVaultException.java`:

```java
package dev.jasper.vault.crypto;

/** The file is not a vault this version can read. */
public final class CorruptVaultException extends RuntimeException {
    public CorruptVaultException(String message) { super(message); }
}
```

`VaultFileFormat.java`:

```java
package dev.jasper.vault.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * {@code "JASPERVLT" | version u16 LE | flags u8 | salt 16 | nonce 12 | ciphertext}. The header bytes are the
 * cipher's additional authenticated data, so the flags cannot be changed without the key.
 */
public final class VaultFileFormat {
    static final byte[] MAGIC = "JASPERVLT".getBytes(StandardCharsets.US_ASCII);
    static final int VERSION = 1;
    public static final int NONCE_LENGTH = 12;
    static final int FLAG_BOUND = 1;
    public static final int HEADER_LENGTH = MAGIC.length + 2 + 1 + KeyDerivation.SALT_LENGTH + NONCE_LENGTH;

    private VaultFileFormat() { }

    /** The authenticated header; {@code bound} means the key includes the device secret. */
    public record Header(boolean bound, byte[] salt, byte[] nonce) {
        public Header {
            if (salt.length != KeyDerivation.SALT_LENGTH) throw new IllegalArgumentException("The salt must be " + KeyDerivation.SALT_LENGTH + " bytes");
            if (nonce.length != NONCE_LENGTH) throw new IllegalArgumentException("The nonce must be " + NONCE_LENGTH + " bytes");
            salt = salt.clone(); nonce = nonce.clone();
        }
        public byte[] encode() {
            ByteBuffer out = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
            out.put(MAGIC).putShort((short) VERSION).put((byte) (bound ? FLAG_BOUND : 0)).put(salt).put(nonce);
            return out.array();
        }
        @Override public boolean equals(Object other) {
            return other instanceof Header header && bound == header.bound && Arrays.equals(salt, header.salt) && Arrays.equals(nonce, header.nonce);
        }
        @Override public int hashCode() { return 31 * (31 * Boolean.hashCode(bound) + Arrays.hashCode(salt)) + Arrays.hashCode(nonce); }
        @Override public String toString() { return "Header[bound=" + bound + "]"; }
    }

    /** A parsed file: header plus ciphertext (tag included). */
    public record Parsed(Header header, byte[] ciphertext) { }

    public static byte[] assemble(Header header, byte[] ciphertext) {
        byte[] head = header.encode();
        byte[] out = Arrays.copyOf(head, head.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, out, head.length, ciphertext.length);
        return out;
    }

    public static Parsed parse(byte[] file) {
        if (file.length < HEADER_LENGTH) throw new CorruptVaultException("The vault file is truncated");
        if (!Arrays.equals(file, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) throw new CorruptVaultException("The file is not a Jasper vault");
        ByteBuffer in = ByteBuffer.wrap(file, MAGIC.length, HEADER_LENGTH - MAGIC.length).order(ByteOrder.LITTLE_ENDIAN);
        int version = Short.toUnsignedInt(in.getShort());
        if (version != VERSION) throw new CorruptVaultException("The vault file is version " + version + "; this plugin reads version " + VERSION);
        int flags = Byte.toUnsignedInt(in.get());
        byte[] salt = new byte[KeyDerivation.SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        in.get(salt).get(nonce);
        return new Parsed(new Header((flags & FLAG_BOUND) != 0, salt, nonce), Arrays.copyOfRange(file, HEADER_LENGTH, file.length));
    }
}
```

`VaultCipher.java`:

```java
package dev.jasper.vault.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM over the plaintext with the file header as additional authenticated data. */
public final class VaultCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAG_BITS = 128;

    private VaultCipher() { }

    public static byte[] randomSalt() { byte[] salt = new byte[KeyDerivation.SALT_LENGTH]; RANDOM.nextBytes(salt); return salt; }

    /** A complete file with a fresh nonce. Zeroes nothing: the caller owns {@code key} and {@code plaintext}. */
    public static byte[] seal(byte[] key, byte[] salt, boolean bound, byte[] plaintext) {
        byte[] nonce = new byte[VaultFileFormat.NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        var header = new VaultFileFormat.Header(bound, salt, nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header.encode());
            return VaultFileFormat.assemble(header, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM is unavailable", failure);
        }
    }

    /** The plaintext, which the caller zeroes; {@link WrongPasswordException} when the tag fails. */
    public static byte[] open(byte[] key, VaultFileFormat.Parsed parsed) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, parsed.header().nonce()));
            cipher.updateAAD(parsed.header().encode());
            return cipher.doFinal(parsed.ciphertext());
        } catch (AEADBadTagException failure) {
            throw new WrongPasswordException();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM is unavailable", failure);
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): authenticated file format and AES-GCM cipher"
```

---

### Task 3: Model and binary codec

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/model/package-info.java`, `Vault.java`, `Account.java`, `Auth.java`, `SshKey.java`, `Note.java`, `Grant.java`, `VaultCodec.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/model/VaultCodecTest.java`

**Interfaces:**
- Consumes: `SecureBytes`.
- Produces: `Vault` (mutable: `accounts()`, `keys()`, `notes()`, `grants()` live lists/sets; `Optional<Account> account(UUID)`, `Optional<SshKey> key(UUID)`, `void remove(UUID id)` removes an account or key and its grants, `void zero()`); `Account(UUID id, String name, String username, Auth auth, Instant created, Instant updated)`; sealed `Auth` { `Password(char[] password)`, `Key(Path keyPath, char[] passphrase)`, `KeyAndPassword(Path keyPath, char[] passphrase, char[] password)` } with `zero()`; `SshKey(UUID id, String name, String algorithm, String fingerprint, String comment, Path privatePath, Path publicPath, Instant created)`; `Note(UUID id, String name, char[] text, Instant updated)`; `Grant(String pluginId, UUID credentialId)`; `VaultCodec.encode(Vault) -> byte[]`, `VaultCodec.decode(byte[]) -> Vault` (throws `CorruptVaultException`).

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.vault.model;

import dev.jasper.vault.crypto.CorruptVaultException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultCodecTest {
    static Vault sample() {
        var vault = new Vault();
        vault.accounts().add(new Account(UUID.randomUUID(), "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        vault.accounts().add(new Account(UUID.randomUUID(), "bastion", "ops", new Auth.Key(Path.of("/keys/id_ed25519"), null), Instant.ofEpochSecond(3), Instant.ofEpochSecond(4)));
        vault.accounts().add(new Account(UUID.randomUUID(), "both", "root", new Auth.KeyAndPassword(Path.of("/keys/rsa"), "pp".toCharArray(), "pw".toCharArray()), Instant.ofEpochSecond(5), Instant.ofEpochSecond(6)));
        vault.keys().add(new SshKey(UUID.randomUUID(), "laptop", "ed25519", "SHA256:abc", "me@laptop", Path.of("/keys/a"), Path.of("/keys/a.pub"), Instant.ofEpochSecond(7)));
        vault.notes().add(new Note(UUID.randomUUID(), "wifi", "hunter2 é".toCharArray(), Instant.ofEpochSecond(8)));
        vault.grants().add(new Grant("dev.jasper.ssh", vault.accounts().getFirst().id()));
        return vault;
    }

    @Test void roundTripsEveryKind() {
        Vault original = sample();
        Vault copy = VaultCodec.decode(VaultCodec.encode(original));
        assertThat(copy.accounts()).hasSize(3);
        assertThat(copy.accounts().get(0).auth()).isInstanceOf(Auth.Password.class);
        assertThat(((Auth.Password) copy.accounts().get(0).auth()).password()).isEqualTo("s3cret".toCharArray());
        assertThat(copy.accounts().get(0)).usingRecursiveComparison().ignoringFields("auth").isEqualTo(original.accounts().get(0));
        Auth.Key key = (Auth.Key) copy.accounts().get(1).auth();
        assertThat(key.keyPath()).isEqualTo(Path.of("/keys/id_ed25519"));
        assertThat(key.passphrase()).isNull();
        Auth.KeyAndPassword both = (Auth.KeyAndPassword) copy.accounts().get(2).auth();
        assertThat(both.passphrase()).isEqualTo("pp".toCharArray());
        assertThat(both.password()).isEqualTo("pw".toCharArray());
        assertThat(copy.keys()).singleElement().isEqualTo(original.keys().getFirst());
        assertThat(copy.notes().getFirst().text()).isEqualTo("hunter2 é".toCharArray());
        assertThat(copy.notes().getFirst().name()).isEqualTo("wifi");
        assertThat(copy.grants()).containsExactlyElementsOf(original.grants());
    }

    @Test void anEmptyVaultRoundTrips() {
        Vault copy = VaultCodec.decode(VaultCodec.encode(new Vault()));
        assertThat(copy.accounts()).isEmpty();
        assertThat(copy.grants()).isEmpty();
    }

    @Test void rejectsAnUnknownVersion() {
        byte[] bytes = VaultCodec.encode(new Vault());
        bytes[1] = 9;
        assertThatThrownBy(() -> VaultCodec.decode(bytes)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> VaultCodec.decode(new byte[] {0, 1, 0})).isInstanceOf(CorruptVaultException.class);
    }

    @Test void zeroClearsEverySecretAndRemoveTakesGrantsAlong() {
        Vault vault = sample();
        UUID first = vault.accounts().getFirst().id();
        assertThat(vault.account(first)).isPresent();
        vault.remove(first);
        assertThat(vault.account(first)).isEmpty();
        assertThat(vault.grants()).as("the grant for the removed account is gone").isEmpty();
        char[] password = ((Auth.KeyAndPassword) vault.accounts().get(1).auth()).password();
        char[] note = vault.notes().getFirst().text();
        vault.zero();
        assertThat(password).containsOnly((char) 0);
        assertThat(note).containsOnly((char) 0);
        assertThat(vault.accounts()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`model/package-info.java`:

```java
/** The decrypted vault: accounts, SSH keys, notes and grants, and their binary encoding. Secrets are arrays. */
package dev.jasper.vault.model;
```

`Auth.java`:

```java
package dev.jasper.vault.model;

import dev.jasper.vault.crypto.SecureBytes;
import java.nio.file.Path;
import java.util.Objects;

/** How an account authenticates. Secret arrays are owned by the record; {@link #zero()} clears them. */
public sealed interface Auth {
    void zero();

    record Password(char[] password) implements Auth {
        public Password { Objects.requireNonNull(password, "password"); }
        @Override public void zero() { SecureBytes.zero(password); }
    }

    /** {@code passphrase} is null when the key has none. */
    record Key(Path keyPath, char[] passphrase) implements Auth {
        public Key { Objects.requireNonNull(keyPath, "keyPath"); }
        @Override public void zero() { SecureBytes.zero(passphrase); }
    }

    /** {@code passphrase} is null when the key has none. */
    record KeyAndPassword(Path keyPath, char[] passphrase, char[] password) implements Auth {
        public KeyAndPassword { Objects.requireNonNull(keyPath, "keyPath"); Objects.requireNonNull(password, "password"); }
        @Override public void zero() { SecureBytes.zero(passphrase); SecureBytes.zero(password); }
    }
}
```

`Account.java`:

```java
package dev.jasper.vault.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A login: a username with a password, a key, or both. */
public record Account(UUID id, String name, String username, Auth auth, Instant created, Instant updated) {
    public Account {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("An account needs a name");
        username = username == null ? "" : username;
    }
}
```

`SshKey.java`:

```java
package dev.jasper.vault.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A standalone SSH key the vault generated or imported; the private key stays on disk at {@code privatePath}. */
public record SshKey(UUID id, String name, String algorithm, String fingerprint, String comment, Path privatePath, Path publicPath, Instant created) {
    public SshKey {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(privatePath, "privatePath");
        Objects.requireNonNull(publicPath, "publicPath"); Objects.requireNonNull(created, "created");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
        algorithm = algorithm == null ? "" : algorithm; fingerprint = fingerprint == null ? "" : fingerprint; comment = comment == null ? "" : comment;
    }
}
```

`Note.java`:

```java
package dev.jasper.vault.model;

import dev.jasper.vault.crypto.SecureBytes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A secure note; never handed to other plugins. */
public record Note(UUID id, String name, char[] text, Instant updated) {
    public Note {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(text, "text"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A note needs a name");
    }
    public void zero() { SecureBytes.zero(text); }
}
```

`Grant.java`:

```java
package dev.jasper.vault.model;

import java.util.Objects;
import java.util.UUID;

/** "Always allow": {@code pluginId} may fetch {@code credentialId} without a prompt. */
public record Grant(String pluginId, UUID credentialId) {
    public Grant { Objects.requireNonNull(pluginId, "pluginId"); Objects.requireNonNull(credentialId, "credentialId"); }
}
```

`Vault.java`:

```java
package dev.jasper.vault.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The decrypted contents. Mutable, edited on the UI thread only, saved by the lock manager after each edit. */
public final class Vault {
    private final List<Account> accounts = new ArrayList<>();
    private final List<SshKey> keys = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final Set<Grant> grants = new LinkedHashSet<>();

    public List<Account> accounts() { return accounts; }
    public List<SshKey> keys() { return keys; }
    public List<Note> notes() { return notes; }
    public Set<Grant> grants() { return grants; }

    public Optional<Account> account(UUID id) { return accounts.stream().filter(account -> account.id().equals(id)).findFirst(); }
    public Optional<SshKey> key(UUID id) { return keys.stream().filter(key -> key.id().equals(id)).findFirst(); }

    /** Removes the account or key with {@code id} (zeroing an account's secrets) and every grant for it. */
    public void remove(UUID id) {
        accounts.removeIf(account -> { if (!account.id().equals(id)) return false; account.auth().zero(); return true; });
        keys.removeIf(key -> key.id().equals(id));
        notes.removeIf(note -> { if (!note.id().equals(id)) return false; note.zero(); return true; });
        grants.removeIf(grant -> grant.credentialId().equals(id));
    }

    /** Zeroes every secret and empties the vault; called at lock. */
    public void zero() {
        accounts.forEach(account -> account.auth().zero());
        notes.forEach(Note::zero);
        accounts.clear(); keys.clear(); notes.clear(); grants.clear();
    }
}
```

`VaultCodec.java`:

```java
package dev.jasper.vault.model;

import dev.jasper.vault.crypto.CorruptVaultException;
import dev.jasper.vault.crypto.SecureBytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Version 1: {@code u16 version}, then accounts, keys, notes and grants, each as {@code i32 count} and
 * length-prefixed fields. Strings are {@code i32 length + UTF-8}; secrets the same with {@code -1} for
 * null, and the intermediate byte arrays are zeroed. Instants are epoch seconds.
 */
public final class VaultCodec {
    static final int VERSION = 1;
    private static final int MAX_LENGTH = 1 << 24;

    private VaultCodec() { }

    public static byte[] encode(Vault vault) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeShort(VERSION);
            out.writeInt(vault.accounts().size());
            for (Account account : vault.accounts()) {
                uuid(out, account.id()); string(out, account.name()); string(out, account.username());
                switch (account.auth()) {
                    case Auth.Password password -> { out.writeByte(0); secret(out, password.password()); }
                    case Auth.Key key -> { out.writeByte(1); string(out, key.keyPath().toString()); secret(out, key.passphrase()); }
                    case Auth.KeyAndPassword both -> { out.writeByte(2); string(out, both.keyPath().toString()); secret(out, both.passphrase()); secret(out, both.password()); }
                }
                out.writeLong(account.created().getEpochSecond()); out.writeLong(account.updated().getEpochSecond());
            }
            out.writeInt(vault.keys().size());
            for (SshKey key : vault.keys()) {
                uuid(out, key.id()); string(out, key.name()); string(out, key.algorithm()); string(out, key.fingerprint()); string(out, key.comment());
                string(out, key.privatePath().toString()); string(out, key.publicPath().toString()); out.writeLong(key.created().getEpochSecond());
            }
            out.writeInt(vault.notes().size());
            for (Note note : vault.notes()) { uuid(out, note.id()); string(out, note.name()); secret(out, note.text()); out.writeLong(note.updated().getEpochSecond()); }
            out.writeInt(vault.grants().size());
            for (Grant grant : vault.grants()) { string(out, grant.pluginId()); uuid(out, grant.credentialId()); }
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return bytes.toByteArray();
    }

    public static Vault decode(byte[] bytes) {
        var vault = new Vault();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readUnsignedShort();
            if (version != VERSION) throw new CorruptVaultException("The vault contents are version " + version + "; this plugin reads version " + VERSION);
            int accounts = count(in);
            for (int i = 0; i < accounts; i++) {
                UUID id = uuid(in); String name = string(in), username = string(in);
                Auth auth = switch (in.readByte()) {
                    case 0 -> new Auth.Password(secret(in));
                    case 1 -> new Auth.Key(Path.of(string(in)), secretOrNull(in));
                    case 2 -> new Auth.KeyAndPassword(Path.of(string(in)), secretOrNull(in), secret(in));
                    default -> throw new CorruptVaultException("Unknown authentication kind");
                };
                vault.accounts().add(new Account(id, name, username, auth, Instant.ofEpochSecond(in.readLong()), Instant.ofEpochSecond(in.readLong())));
            }
            int keys = count(in);
            for (int i = 0; i < keys; i++)
                vault.keys().add(new SshKey(uuid(in), string(in), string(in), string(in), string(in), Path.of(string(in)), Path.of(string(in)), Instant.ofEpochSecond(in.readLong())));
            int notes = count(in);
            for (int i = 0; i < notes; i++) vault.notes().add(new Note(uuid(in), string(in), secret(in), Instant.ofEpochSecond(in.readLong())));
            int grants = count(in);
            for (int i = 0; i < grants; i++) vault.grants().add(new Grant(string(in), uuid(in)));
            return vault;
        } catch (EOFException truncated) {
            vault.zero();
            throw new CorruptVaultException("The vault contents are truncated");
        } catch (IOException | IllegalArgumentException failure) {
            vault.zero();
            throw new CorruptVaultException("The vault contents are unreadable: " + failure.getMessage());
        }
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }

    private static void string(DataOutputStream out, String text) throws IOException {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length); out.write(utf8);
    }

    private static String string(DataInputStream in) throws IOException { return new String(in.readNBytes(count(in)), StandardCharsets.UTF_8); }

    private static void secret(DataOutputStream out, char[] text) throws IOException {
        if (text == null) { out.writeInt(-1); return; }
        byte[] utf8 = SecureBytes.utf8(text);
        try { out.writeInt(utf8.length); out.write(utf8); } finally { SecureBytes.zero(utf8); }
    }

    private static char[] secretOrNull(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) return null;
        if (length < 0 || length > MAX_LENGTH) throw new CorruptVaultException("Bad length");
        byte[] utf8 = in.readNBytes(length);
        if (utf8.length != length) throw new EOFException();
        try { return SecureBytes.chars(utf8); } finally { SecureBytes.zero(utf8); }
    }

    private static char[] secret(DataInputStream in) throws IOException {
        char[] value = secretOrNull(in);
        if (value == null) throw new CorruptVaultException("A required secret is missing");
        return value;
    }

    private static int count(DataInputStream in) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > MAX_LENGTH) throw new CorruptVaultException("Bad length");
        return value;
    }
}
```

Note: `ByteArrayOutputStream` keeps a copy of the plaintext; `encode`'s caller (the lock manager) zeroes the returned array, and the stream's buffer is unreachable garbage — accepted, the same as the cipher's internal buffers.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): the vault model and its binary codec"
```

---

### Task 4: Vault file I/O and the device secret

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/store/package-info.java`, `VaultFile.java`, `DeviceSecretStore.java`, `FileStore.java`, `KeychainStore.java`, `DeviceSecrets.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/store/VaultFileTest.java`, `FileStoreTest.java`, `KeychainStoreTest.java`, `DeviceSecretsTest.java`

**Interfaces:**
- Produces: `VaultFile(Path)` with `Path path()`, `boolean exists()`, `byte[] read() throws IOException`, `void write(byte[]) throws IOException` (temp file + atomic move, 0600); `DeviceSecretStore` { `Optional<byte[]> read() throws IOException`, `void write(byte[]) throws IOException`, `void delete() throws IOException`, `String description()` } implemented by `FileStore(Path)` and `KeychainStore(String osName, Function<KeychainStore.Command, KeychainStore.Output> tool, Path windowsFile)`; `KeychainStore.forPlatform(Path dataDirectory)`; `KeychainStore.Command(List<String> arguments, String stdin)`, `KeychainStore.Output(int exit, String stdout)`; `DeviceSecrets(KeychainStore keychain, FileStore file)` with `byte[] getOrCreate() throws IOException`, `Optional<byte[]> existing() throws IOException`, `String source()`; `DeviceSecrets.SECRET_LENGTH = 32`.

- [ ] **Step 1: Write the failing tests**

`VaultFileTest.java`:

```java
package dev.jasper.vault.store;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultFileTest {
    @Test void writesAtomicallyAndReadsBack(@TempDir Path dir) throws Exception {
        var file = new VaultFile(dir.resolve("vault.jv"));
        assertThat(file.exists()).isFalse();
        file.write(new byte[] {1, 2, 3});
        assertThat(file.exists()).isTrue();
        assertThat(file.read()).containsExactly(1, 2, 3);
        file.write(new byte[] {4});
        assertThat(file.read()).containsExactly(4);
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file left").containsExactly(dir.resolve("vault.jv")); }
        if (Files.getFileStore(dir).supportsFileAttributeView("posix"))
            assertThat(Files.getPosixFilePermissions(file.path())).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    }

    @Test void createsMissingParents(@TempDir Path dir) throws Exception {
        var file = new VaultFile(dir.resolve("deep/er/vault.jv"));
        file.write(new byte[] {9});
        assertThat(file.read()).containsExactly(9);
    }
}
```

`FileStoreTest.java`:

```java
package dev.jasper.vault.store;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FileStoreTest {
    @Test void roundTripsAndDeletes(@TempDir Path dir) throws Exception {
        var store = new FileStore(dir.resolve("device.secret"));
        assertThat(store.read()).isEmpty();
        byte[] secret = new byte[32]; secret[0] = 42;
        store.write(secret);
        assertThat(store.read()).contains(secret);
        assertThat(store.description()).isEqualTo("file (no keychain found)");
        store.delete();
        assertThat(store.read()).isEmpty();
    }
}
```

`KeychainStoreTest.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeychainStoreTest {
    static final byte[] SECRET = new byte[32];
    static { SECRET[1] = 7; }
    static final String B64 = Base64.getEncoder().encodeToString(SECRET);

    /** A scripted tool: records commands, answers from a map keyed by the first two arguments. */
    static final class Tool implements java.util.function.Function<KeychainStore.Command, KeychainStore.Output> {
        final List<KeychainStore.Command> calls = new ArrayList<>();
        final java.util.Map<String, KeychainStore.Output> answers = new java.util.HashMap<>();
        @Override public KeychainStore.Output apply(KeychainStore.Command command) {
            calls.add(command);
            return answers.getOrDefault(command.arguments().get(0) + " " + command.arguments().get(1), new KeychainStore.Output(1, ""));
        }
    }

    @Test void macOsUsesSecurityGenericPasswords(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        var store = new KeychainStore("Mac OS X", tool, dir.resolve("device.dpapi"));
        tool.answers.put("security find-generic-password", new KeychainStore.Output(44, "security: SecKeychainSearchCopyNext: The specified item could not be found in the keychain."));
        assertThat(store.read()).as("exit 44 = not found").isEmpty();
        tool.answers.put("security find-generic-password", new KeychainStore.Output(0, B64 + "\n"));
        assertThat(store.read()).contains(SECRET);
        tool.answers.put("security add-generic-password", new KeychainStore.Output(0, ""));
        store.write(SECRET);
        store.delete();
        assertThat(tool.calls.get(0).arguments()).containsExactly("security", "find-generic-password", "-s", KeychainStore.SERVICE, "-a", KeychainStore.ACCOUNT, "-w");
        assertThat(tool.calls.get(2).arguments()).containsExactly("security", "add-generic-password", "-U", "-s", KeychainStore.SERVICE, "-a", KeychainStore.ACCOUNT, "-w", B64);
        assertThat(tool.calls.get(3).arguments()).startsWith("security", "delete-generic-password");
        assertThat(store.description()).isEqualTo("keychain");
    }

    @Test void linuxUsesSecretToolWithTheSecretOnStdin(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        var store = new KeychainStore("Linux", tool, dir.resolve("device.dpapi"));
        tool.answers.put("secret-tool lookup", new KeychainStore.Output(0, B64));
        tool.answers.put("secret-tool store", new KeychainStore.Output(0, ""));
        assertThat(store.read()).contains(SECRET);
        store.write(SECRET);
        assertThat(tool.calls.get(1).arguments()).startsWith("secret-tool", "store", "--label=" + KeychainStore.SERVICE);
        assertThat(tool.calls.get(1).stdin()).isEqualTo(B64);
    }

    @Test void windowsProtectsAFileWithDpapi(@TempDir Path dir) throws Exception {
        var tool = new Tool();
        Path file = dir.resolve("device.dpapi");
        var store = new KeychainStore("Windows 11", tool, file);
        assertThat(store.read()).as("no file yet: no tool call").isEmpty();
        assertThat(tool.calls).isEmpty();
        tool.answers.put("powershell -NoProfile", new KeychainStore.Output(0, B64));
        store.write(SECRET);
        assertThat(tool.calls.get(0).arguments().get(3)).contains("ProtectedData]::Protect", B64, file.toString().replace("'", "''"));
        Files.writeString(file, "x");
        assertThat(store.read()).contains(SECRET);
        store.delete();
        assertThat(file).doesNotExist();
    }

    @Test void aFailingToolIsAnIOException(@TempDir Path dir) {
        var tool = new Tool();
        tool.answers.put("security find-generic-password", new KeychainStore.Output(36, "security: SecKeychainSearchCopyNext: failed"));
        var store = new KeychainStore("Mac OS X", tool, dir.resolve("device.dpapi"));
        assertThatThrownBy(store::read).isInstanceOf(IOException.class).hasMessageContaining("security");
        assertThatThrownBy(() -> store.write(SECRET)).isInstanceOf(IOException.class);
    }

    /** Opt in with -Djasper.vault.keychainTest=true: touches the real keychain under a throwaway service name. */
    @Test void realKeychainRoundTrip(@TempDir Path dir) throws Exception {
        assumeTrue(Boolean.getBoolean("jasper.vault.keychainTest"));
        var store = new KeychainStore(System.getProperty("os.name"), KeychainStore.processTool(), dir.resolve("device.dpapi"), KeychainStore.SERVICE + " test");
        try {
            store.write(SECRET);
            assertThat(store.read()).contains(SECRET);
        } finally {
            store.delete();
        }
        assertThat(store.read()).isEmpty();
    }
}
```

`DeviceSecretsTest.java`:

```java
package dev.jasper.vault.store;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class DeviceSecretsTest {
    @Test void prefersTheKeychainAndCreatesOnce(@TempDir Path dir) throws Exception {
        var tool = new KeychainStoreTest.Tool();
        var keychain = new KeychainStore("Linux", tool, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        tool.answers.put("secret-tool store", new KeychainStore.Output(0, ""));
        assertThat(secrets.existing()).isEmpty();
        byte[] created = secrets.getOrCreate();
        assertThat(created).hasSize(32);
        String stored = tool.calls.getLast().stdin();
        tool.answers.put("secret-tool lookup", new KeychainStore.Output(0, stored));
        assertThat(secrets.getOrCreate()).isEqualTo(created);
        assertThat(secrets.source()).isEqualTo("keychain");
        assertThat(dir.resolve("device.secret")).doesNotExist();
    }

    @Test void fallsBackToTheFileWhenTheToolIsMissing(@TempDir Path dir) throws Exception {
        var keychain = new KeychainStore("Linux", command -> { throw new java.io.UncheckedIOException(new java.io.IOException("secret-tool: not found")); }, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        byte[] created = secrets.getOrCreate();
        assertThat(dir.resolve("device.secret")).exists();
        assertThat(secrets.existing()).contains(created);
        assertThat(secrets.source()).isEqualTo("file (no keychain found)");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`store/package-info.java`:

```java
/** Files on disk: the encrypted vault (atomic writes) and the device secret in the platform keychain or a file. */
package dev.jasper.vault.store;
```

`VaultFile.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/** The encrypted vault on disk. Writes go to a sibling temp file that is moved into place. */
public final class VaultFile {
    private final Path path;

    public VaultFile(Path path) { this.path = path; }

    public Path path() { return path; }
    public boolean exists() { return Files.isRegularFile(path); }
    public byte[] read() throws IOException { return Files.readAllBytes(path); }

    public void write(byte[] bytes) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, bytes);
        ownerOnly(temp);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void ownerOnly(Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
}
```

`DeviceSecretStore.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.util.Optional;

/** Where the 32-byte device secret lives: the platform keychain ({@link KeychainStore}) or a file ({@link FileStore}). */
public interface DeviceSecretStore {
    Optional<byte[]> read() throws IOException;
    void write(byte[] secret) throws IOException;
    void delete() throws IOException;
    /** Shown in the manager's status line: {@code keychain} or {@code file (no keychain found)}. */
    String description();
}
```

`FileStore.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The fallback: {@code data/device.secret}, owner-readable only. */
public final class FileStore implements DeviceSecretStore {
    private final Path file;

    public FileStore(Path file) { this.file = file; }

    @Override public Optional<byte[]> read() throws IOException {
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    @Override public void write(byte[] secret) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, secret);
        VaultFile.ownerOnly(file);
    }

    @Override public void delete() throws IOException { Files.deleteIfExists(file); }
    @Override public String description() { return "file (no keychain found)"; }
}
```

`KeychainStore.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The platform's own tool, run as a process: macOS {@code security} generic passwords, Linux
 * {@code secret-tool}, Windows PowerShell with DPAPI over a file in the data directory. The 32 bytes
 * travel base64-encoded. A missing or failing tool is an {@link IOException}; {@link DeviceSecrets} then
 * falls back to the file store.
 */
public final class KeychainStore implements DeviceSecretStore {
    public static final String SERVICE = "Jasper Credential Vault";
    public static final String ACCOUNT = "device-secret";
    private static final int TIMEOUT_SECONDS = 15;

    /** One tool invocation; {@code stdin} is written to the process when not null. */
    public record Command(List<String> arguments, String stdin) { }
    /** What the tool produced; stderr is merged into {@code stdout}. */
    public record Output(int exit, String stdout) { }

    private enum Platform { MAC, LINUX, WINDOWS }

    private final Platform platform;
    private final Function<Command, Output> tool;
    private final Path windowsFile;
    private final String service;

    public KeychainStore(String osName, Function<Command, Output> tool, Path windowsFile) { this(osName, tool, windowsFile, SERVICE); }

    /** {@code service} names the keychain item; tests use a throwaway one. */
    public KeychainStore(String osName, Function<Command, Output> tool, Path windowsFile, String service) {
        String os = osName.toLowerCase(Locale.ROOT);
        this.platform = os.contains("mac") || os.contains("darwin") ? Platform.MAC : os.contains("win") ? Platform.WINDOWS : Platform.LINUX;
        this.tool = tool; this.windowsFile = windowsFile; this.service = service;
    }

    /** The real thing: the platform tool as a child process, with a bounded wait. */
    public static KeychainStore forPlatform(Path dataDirectory) {
        return new KeychainStore(System.getProperty("os.name", ""), processTool(), dataDirectory.resolve("device.dpapi"));
    }

    static Function<Command, Output> processTool() {
        return command -> {
            try {
                Process process = new ProcessBuilder(command.arguments()).redirectErrorStream(true).start();
                try (var stdin = process.getOutputStream()) { if (command.stdin() != null) stdin.write(command.stdin().getBytes(StandardCharsets.UTF_8)); }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException(command.arguments().get(0) + " did not finish"); }
                return new Output(process.exitValue(), output);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(new IOException("interrupted"));
            }
        };
    }

    @Override public Optional<byte[]> read() throws IOException {
        return switch (platform) {
            case MAC -> {
                Output out = run(List.of("security", "find-generic-password", "-s", service, "-a", ACCOUNT, "-w"), null);
                if (out.exit() == 44) yield Optional.empty();   // errSecItemNotFound
                yield Optional.of(decode(require(out, "security")));
            }
            case LINUX -> {
                Output out = run(List.of("secret-tool", "lookup", "service", key(), "account", ACCOUNT), null);
                if (out.exit() == 1 && out.stdout().isBlank()) yield Optional.empty();
                yield Optional.of(decode(require(out, "secret-tool")));
            }
            case WINDOWS -> {
                if (!Files.isRegularFile(windowsFile)) yield Optional.empty();
                Output out = run(powershell("[Convert]::ToBase64String([System.Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes('"
                    + quote(windowsFile) + "'), $null, 'CurrentUser'))"), null);
                yield Optional.of(decode(require(out, "powershell")));
            }
        };
    }

    @Override public void write(byte[] secret) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(secret);
        switch (platform) {
            case MAC -> require(run(List.of("security", "add-generic-password", "-U", "-s", service, "-a", ACCOUNT, "-w", encoded), null), "security");
            case LINUX -> require(run(List.of("secret-tool", "store", "--label=" + service, "service", key(), "account", ACCOUNT), encoded), "secret-tool");
            case WINDOWS -> require(run(powershell("[IO.File]::WriteAllBytes('" + quote(windowsFile) + "', [System.Security.Cryptography.ProtectedData]::Protect([Convert]::FromBase64String('"
                + encoded + "'), $null, 'CurrentUser'))"), null), "powershell");
        }
    }

    @Override public void delete() throws IOException {
        switch (platform) {
            case MAC -> run(List.of("security", "delete-generic-password", "-s", service, "-a", ACCOUNT), null);
            case LINUX -> run(List.of("secret-tool", "clear", "service", key(), "account", ACCOUNT), null);
            case WINDOWS -> Files.deleteIfExists(windowsFile);
        }
    }

    @Override public String description() { return "keychain"; }

    private String key() { return service.toLowerCase(Locale.ROOT).replace(' ', '-'); }
    private static List<String> powershell(String script) { return List.of("powershell", "-NoProfile", "-Command", "Add-Type -AssemblyName System.Security; " + script); }
    private static String quote(Path path) { return path.toString().replace("'", "''"); }

    private Output run(List<String> arguments, String stdin) throws IOException {
        try { return tool.apply(new Command(arguments, stdin)); }
        catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    private static String require(Output out, String toolName) throws IOException {
        if (out.exit() != 0) throw new IOException(toolName + " failed (exit " + out.exit() + "): " + out.stdout().strip());
        return out.stdout().strip();
    }

    private static byte[] decode(String base64) throws IOException {
        try { return Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException bad) { throw new IOException("The stored device secret is not base64"); }
    }
}
```

`DeviceSecrets.java`:

```java
package dev.jasper.vault.store;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * The device secret: read from the keychain, else from the file; created in the keychain when the
 * tool works, else in the file. {@link #source()} says which one answered last.
 */
public final class DeviceSecrets {
    public static final int SECRET_LENGTH = 32;
    private final KeychainStore keychain;
    private final FileStore file;
    private String source = "keychain";

    public DeviceSecrets(KeychainStore keychain, FileStore file) { this.keychain = keychain; this.file = file; }

    /** The secret if one exists in either store. The caller zeroes it. */
    public Optional<byte[]> existing() throws IOException {
        Optional<byte[]> fromKeychain;
        try { fromKeychain = keychain.read(); source = keychain.description(); }
        catch (IOException unavailable) { fromKeychain = Optional.empty(); source = file.description(); }
        if (fromKeychain.isPresent()) return fromKeychain;
        Optional<byte[]> fromFile = file.read();
        if (fromFile.isPresent()) source = file.description();
        return fromFile;
    }

    /** The secret, created on first use. The caller zeroes it. */
    public byte[] getOrCreate() throws IOException {
        Optional<byte[]> present = existing();
        if (present.isPresent()) return present.get();
        byte[] secret = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(secret);
        try { keychain.write(secret); source = keychain.description(); }
        catch (IOException unavailable) { file.write(secret); source = file.description(); }
        return secret;
    }

    public String source() { return source; }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS; `realKeychainRoundTrip` is skipped (assumption).

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): atomic vault file and the device secret in the keychain or a file"
```

---

### Task 5: Lock manager and inactivity timer

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/lock/package-info.java`, `LockManager.java`, `ForeignDeviceException.java`, `InactivityTimer.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/api/package-info.java`, `api/LockState.java` (the rest of the API package arrives in Task 6)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/lock/LockManagerTest.java`, `InactivityTimerTest.java`

**Interfaces:**
- Consumes: `VaultFile`, `DeviceSecrets`, `KeyDerivation`, `VaultCipher`, `VaultFileFormat`, `VaultCodec`, `Vault`.
- Produces: `LockState { NO_VAULT, LOCKED, UNLOCKED }`; `LockManager(VaultFile, DeviceSecrets, Executor background, Executor ui, Consumer<LockState> listener)` with `state()`, `vault()` (throws `IllegalStateException` when locked), `boolean bound()`, `String deviceSecretSource()`, `CompletableFuture<Void> create(char[] password, boolean bind)`, `CompletableFuture<Void> unlock(char[] password)` (fails with `WrongPasswordException`, `ForeignDeviceException`, `CorruptVaultException`, `IOException`), `void lock()`, `CompletableFuture<Void> save()`, `CompletableFuture<Void> changePassword(char[] current, char[] replacement)`; every `char[]` passed in is zeroed by the manager; futures complete on the UI executor. `InactivityTimer(LongSupplier clockMillis)` with `touch()`, `setTimeout(Duration)` (ZERO = never), `timeout()`, `expired()`, `remaining()`.

- [ ] **Step 1: Write the failing tests**

`LockManagerTest.java`:

```java
package dev.jasper.vault.lock;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.crypto.VaultFileFormat;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LockManagerTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    final List<LockState> states = new ArrayList<>();

    /** Device secrets with no keychain: the file store under {@code dir}. */
    static DeviceSecrets secrets(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("no secret-tool")); }, dir.resolve("x"));
        return new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
    }

    LockManager manager(Path dir) { return new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets(dir), queued, Runnable::run, states::add); }

    void runBackground() { while (!background.isEmpty()) background.poll().run(); }

    static Throwable cause(CompletableFuture<?> future) {
        try { future.join(); return null; }
        catch (CompletionException failure) { return failure.getCause(); }
    }

    @Test void createsLocksAndUnlocksWithTheEdits(@TempDir Path dir) throws Exception {
        LockManager manager = manager(dir);
        assertThat(manager.state()).isEqualTo(LockState.NO_VAULT);
        assertThatThrownBy(manager::vault).isInstanceOf(IllegalStateException.class);
        char[] password = "hunter2".toCharArray();
        CompletableFuture<Void> created = manager.create(password, true);
        assertThat(manager.state()).as("nothing before the background work").isEqualTo(LockState.NO_VAULT);
        runBackground();
        assertThat(created).isCompleted();
        assertThat(password).as("the manager zeroes what it was given").containsOnly((char) 0);
        assertThat(manager.state()).isEqualTo(LockState.UNLOCKED);
        assertThat(manager.bound()).isTrue();
        assertThat(states).containsExactly(LockState.UNLOCKED);
        assertThat(manager.deviceSecretSource()).isEqualTo("file (no keychain found)");
        byte[] first = new VaultFile(dir.resolve("vault.jv")).read();
        assertThat(VaultFileFormat.parse(first).header().bound()).isTrue();

        UUID id = UUID.randomUUID();
        manager.vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("pw".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        CompletableFuture<Void> saved = manager.save();
        runBackground();
        assertThat(saved).isCompleted();
        byte[] second = new VaultFile(dir.resolve("vault.jv")).read();
        assertThat(VaultFileFormat.parse(second).header().nonce()).as("a fresh nonce per write").isNotEqualTo(VaultFileFormat.parse(first).header().nonce());
        assertThat(VaultFileFormat.parse(second).header().salt()).as("the salt lasts the session").isEqualTo(VaultFileFormat.parse(first).header().salt());

        char[] secret = ((Auth.Password) manager.vault().accounts().getFirst().auth()).password();
        manager.lock();
        assertThat(manager.state()).isEqualTo(LockState.LOCKED);
        assertThat(secret).containsOnly((char) 0);
        assertThat(states).containsExactly(LockState.UNLOCKED, LockState.LOCKED);
        manager.lock();
        assertThat(states).as("locking twice publishes once").hasSize(2);

        CompletableFuture<Void> wrong = manager.unlock("nope".toCharArray());
        runBackground();
        assertThat(cause(wrong)).isInstanceOf(WrongPasswordException.class);
        assertThat(manager.state()).isEqualTo(LockState.LOCKED);
        CompletableFuture<Void> unlocked = manager.unlock("hunter2".toCharArray());
        runBackground();
        assertThat(unlocked).isCompleted();
        assertThat(manager.vault().account(id)).isPresent();
        assertThat(((Auth.Password) manager.vault().account(id).get().auth()).password()).isEqualTo("pw".toCharArray());
    }

    @Test void aBoundVaultWithoutItsDeviceSecretIsForeign(@TempDir Path dir) throws Exception {
        LockManager here = manager(dir);
        here.create("pw".toCharArray(), true);
        runBackground();
        var elsewhere = new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets(dir.resolve("other")), queued, Runnable::run, states::add);
        CompletableFuture<Void> attempt = elsewhere.unlock("pw".toCharArray());
        runBackground();
        assertThat(cause(attempt)).isInstanceOf(ForeignDeviceException.class).hasMessageContaining("another machine");

        LockManager portable = manager(dir.resolve("portable"));
        portable.create("pw".toCharArray(), false);
        runBackground();
        assertThat(portable.bound()).isFalse();
        var anywhere = new LockManager(new VaultFile(dir.resolve("portable/vault.jv")), secrets(dir.resolve("elsewhere")), queued, Runnable::run, states::add);
        CompletableFuture<Void> opened = anywhere.unlock("pw".toCharArray());
        runBackground();
        assertThat(opened).isCompleted();
    }

    @Test void changePasswordChecksTheOldOneAndRekeys(@TempDir Path dir) throws Exception {
        LockManager manager = manager(dir);
        manager.create("old".toCharArray(), true);
        runBackground();
        byte[] before = new VaultFile(dir.resolve("vault.jv")).read();
        CompletableFuture<Void> refused = manager.changePassword("wrong".toCharArray(), "new".toCharArray());
        runBackground();
        assertThat(cause(refused)).isInstanceOf(WrongPasswordException.class);
        CompletableFuture<Void> changed = manager.changePassword("old".toCharArray(), "new".toCharArray());
        runBackground();
        assertThat(changed).isCompleted();
        assertThat(VaultFileFormat.parse(new VaultFile(dir.resolve("vault.jv")).read()).header().salt()).isNotEqualTo(VaultFileFormat.parse(before).header().salt());
        manager.lock();
        CompletableFuture<Void> old = manager.unlock("old".toCharArray());
        runBackground();
        assertThat(cause(old)).isInstanceOf(WrongPasswordException.class);
        CompletableFuture<Void> fresh = manager.unlock("new".toCharArray());
        runBackground();
        assertThat(fresh).isCompleted();
    }

    @Test void guardsTheStateMachine(@TempDir Path dir) {
        LockManager manager = manager(dir);
        assertThat(cause(manager.unlock("pw".toCharArray()))).isInstanceOf(IllegalStateException.class);
        assertThat(cause(manager.save())).isInstanceOf(IllegalStateException.class);
        assertThat(cause(manager.changePassword("a".toCharArray(), "b".toCharArray()))).isInstanceOf(IllegalStateException.class);
        manager.create("pw".toCharArray(), false);
        runBackground();
        assertThat(cause(manager.create("pw".toCharArray(), false))).isInstanceOf(IllegalStateException.class);
    }
}
```

`InactivityTimerTest.java`:

```java
package dev.jasper.vault.lock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InactivityTimerTest {
    long now = 1_000_000;

    @Test void expiresAfterTheTimeoutWithoutActivity() {
        var timer = new InactivityTimer(() -> now);
        timer.setTimeout(Duration.ofMinutes(2));
        assertThat(timer.expired()).isFalse();
        now += Duration.ofMinutes(1).toMillis();
        assertThat(timer.remaining()).isEqualTo(Duration.ofMinutes(1));
        timer.touch();
        now += Duration.ofMinutes(1).toMillis() + 59_000;
        assertThat(timer.expired()).isFalse();
        now += 1_000;
        assertThat(timer.expired()).isTrue();
        assertThat(timer.remaining()).isEqualTo(Duration.ZERO);
    }

    @Test void zeroMeansNever() {
        var timer = new InactivityTimer(() -> now);
        timer.setTimeout(Duration.ZERO);
        now += Duration.ofDays(3).toMillis();
        assertThat(timer.expired()).isFalse();
        assertThat(timer.remaining()).isEqualTo(Duration.ZERO);
        assertThat(timer.timeout()).isEqualTo(Duration.ZERO);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`api/package-info.java`:

```java
/**
 * The contract other plugins compile against: {@link dev.jasper.vault.api.VaultApi}, its descriptors,
 * credentials and lock states. Exported by the plugin descriptor; nothing else in the plugin is.
 */
package dev.jasper.vault.api;
```

`api/LockState.java`:

```java
package dev.jasper.vault.api;

/** Whether a vault file exists and whether it is open. */
public enum LockState { NO_VAULT, LOCKED, UNLOCKED }
```

`lock/package-info.java`:

```java
/** The single owner of the decrypted vault and the derived key; the inactivity clock. */
package dev.jasper.vault.lock;
```

`ForeignDeviceException.java`:

```java
package dev.jasper.vault.lock;

/** A device-bound vault whose device secret this machine does not have. */
public final class ForeignDeviceException extends RuntimeException {
    public ForeignDeviceException() { super("This vault was created on another machine and is bound to it; its device secret is not on this one"); }
}
```

`LockManager.java`:

```java
package dev.jasper.vault.lock;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.crypto.KeyDerivation;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.crypto.VaultCipher;
import dev.jasper.vault.crypto.VaultFileFormat;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.model.Vault;
import dev.jasper.vault.model.VaultCodec;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.VaultFile;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Owns the open {@link Vault}, the derived key and the salt for one unlock session. Key derivation and
 * file I/O run on {@code background}; every future completes and every listener call happens on {@code ui},
 * which is also the only thread that may call these methods.
 */
public final class LockManager {
    private final VaultFile file;
    private final DeviceSecrets secrets;
    private final Executor background, ui;
    private final Consumer<LockState> listener;
    private Vault vault;
    private byte[] key, salt;
    private boolean bound;
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    private record Opened(Vault vault, byte[] key, byte[] salt, boolean bound) { }
    private record Rekey(byte[] key, byte[] salt) { }

    public LockManager(VaultFile file, DeviceSecrets secrets, Executor background, Executor ui, Consumer<LockState> listener) {
        this.file = file; this.secrets = secrets; this.background = background; this.ui = ui; this.listener = listener;
    }

    public LockState state() { return vault != null ? LockState.UNLOCKED : file.exists() ? LockState.LOCKED : LockState.NO_VAULT; }
    public boolean bound() { return bound; }
    public String deviceSecretSource() { return secrets.source(); }

    /** The open vault; edit it on the UI thread and call {@link #save()}. */
    public Vault vault() {
        if (vault == null) throw new IllegalStateException("The vault is locked");
        return vault;
    }

    public CompletableFuture<Void> create(char[] password, boolean bind) {
        byte[] material = SecureBytes.utf8(password);
        SecureBytes.zero(password);
        if (state() != LockState.NO_VAULT) { SecureBytes.zero(material); return CompletableFuture.failedFuture(new IllegalStateException("A vault already exists")); }
        return onBackground(() -> {
            byte[] device = bind ? secrets.getOrCreate() : null;
            try {
                byte[] fresh = VaultCipher.randomSalt();
                return new Opened(new Vault(), KeyDerivation.derive(material, device, fresh), fresh, bind);
            } finally { SecureBytes.zero(device); SecureBytes.zero(material); }
        }).thenCompose(opened -> { install(opened); return save(); });
    }

    public CompletableFuture<Void> unlock(char[] password) {
        byte[] material = SecureBytes.utf8(password);
        SecureBytes.zero(password);
        if (state() != LockState.LOCKED) { SecureBytes.zero(material); return CompletableFuture.failedFuture(new IllegalStateException("No locked vault to unlock")); }
        return onBackground(() -> {
            VaultFileFormat.Parsed parsed = VaultFileFormat.parse(file.read());
            byte[] device = parsed.header().bound() ? secrets.existing().orElseThrow(ForeignDeviceException::new) : null;
            byte[] derived = null;
            try {
                derived = KeyDerivation.derive(material, device, parsed.header().salt());
                byte[] plain = VaultCipher.open(derived, parsed);
                try { return new Opened(VaultCodec.decode(plain), derived, parsed.header().salt(), parsed.header().bound()); }
                finally { SecureBytes.zero(plain); }
            } catch (RuntimeException failure) {
                SecureBytes.zero(derived);
                throw failure;
            } finally { SecureBytes.zero(device); SecureBytes.zero(material); }
        }).thenAccept(this::install);
    }

    /** Zeroes everything and publishes {@link LockState#LOCKED}; a no-op when already locked. */
    public void lock() {
        if (vault == null) return;
        vault.zero();
        SecureBytes.zero(key); SecureBytes.zero(salt);
        vault = null; key = null; salt = null;
        listener.accept(state());
    }

    /** Encrypts the open vault now (on the UI thread) and writes it in the background, writes in order. */
    public CompletableFuture<Void> save() {
        if (vault == null) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
        byte[] plain = VaultCodec.encode(vault);
        byte[] bytes;
        try { bytes = VaultCipher.seal(key, salt, bound, plain); } finally { SecureBytes.zero(plain); }
        lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> onBackground(() -> { file.write(bytes); return null; }));
        return lastWrite;
    }

    public CompletableFuture<Void> changePassword(char[] current, char[] replacement) {
        byte[] old = SecureBytes.utf8(current), fresh = SecureBytes.utf8(replacement);
        SecureBytes.zero(current); SecureBytes.zero(replacement);
        if (vault == null) { SecureBytes.zero(old); SecureBytes.zero(fresh); return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked")); }
        byte[] currentKey = key.clone(), currentSalt = salt.clone();
        boolean isBound = bound;
        return onBackground(() -> {
            byte[] device = isBound ? secrets.existing().orElseThrow(ForeignDeviceException::new) : null;
            try {
                byte[] check = KeyDerivation.derive(old, device, currentSalt);
                boolean matches = MessageDigest.isEqual(check, currentKey);
                SecureBytes.zero(check);
                if (!matches) throw new WrongPasswordException();
                byte[] newSalt = VaultCipher.randomSalt();
                return new Rekey(KeyDerivation.derive(fresh, device, newSalt), newSalt);
            } finally { SecureBytes.zero(device); SecureBytes.zero(old); SecureBytes.zero(fresh); SecureBytes.zero(currentKey); }
        }).thenCompose(rekey -> {
            if (vault == null) { SecureBytes.zero(rekey.key()); return CompletableFuture.failedFuture(new IllegalStateException("The vault was locked meanwhile")); }
            SecureBytes.zero(key); SecureBytes.zero(salt);
            key = rekey.key(); salt = rekey.salt();
            return save();
        });
    }

    private void install(Opened opened) {
        vault = opened.vault(); key = opened.key(); salt = opened.salt(); bound = opened.bound();
        listener.accept(LockState.UNLOCKED);
    }

    /** Runs {@code work} on the background executor and completes the result on the UI executor. */
    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); }
            catch (Throwable failure) { ui.execute(() -> result.completeExceptionally(failure)); return; }
            ui.execute(() -> result.complete(value));
        });
        return result;
    }
}
```

`InactivityTimer.java`:

```java
package dev.jasper.vault.lock;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Time since the last keyboard or mouse event against a timeout; {@link Duration#ZERO} disables it. */
public final class InactivityTimer {
    private final LongSupplier clockMillis;
    private long lastActivity;
    private Duration timeout = Duration.ofMinutes(15);

    public InactivityTimer(LongSupplier clockMillis) { this.clockMillis = clockMillis; touch(); }

    public void touch() { lastActivity = clockMillis.getAsLong(); }
    public void setTimeout(Duration value) { timeout = value.isNegative() ? Duration.ZERO : value; }
    public Duration timeout() { return timeout; }

    public boolean expired() { return !timeout.isZero() && clockMillis.getAsLong() - lastActivity >= timeout.toMillis(); }

    /** Time left before {@link #expired()}, or ZERO when disabled or already due. */
    public Duration remaining() {
        if (timeout.isZero()) return Duration.ZERO;
        long left = timeout.toMillis() - (clockMillis.getAsLong() - lastActivity);
        return left <= 0 ? Duration.ZERO : Duration.ofMillis(left);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): lock manager owning the open vault and key, and the inactivity timer"
```

---

### Task 6: The consumer API and the request service

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/api/Kind.java`, `CredentialDescriptor.java`, `Credential.java`, `VaultApi.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/service/package-info.java`, `UnlockPrompt.java`, `GrantPrompt.java`, `PickPrompt.java`, `VaultService.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/api/CredentialTest.java`, `plugins/vault/src/test/java/dev/jasper/vault/service/VaultServiceTest.java`

**Interfaces:**
- Consumes: `LockManager`, `Vault`, `Account`, `Auth`, `SshKey`, `Grant`, `LockState`, SDK `PluginInfo`, `WindowHandle`, `Topic`.
- Produces: the API (spec section 4) — `VaultApi`, `Kind`, `CredentialDescriptor(UUID id, String name, String subtitle, Kind kind)`, `Credential` (constructor `Credential(UUID, String name, Kind, String usernameOrNull, char[] passwordOrNull, Path keyPathOrNull, char[] passphraseOrNull)`, arrays owned); `VaultService(LockManager, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock, Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice)` with `VaultApi forConsumer(PluginInfo)`, `CompletableFuture<Boolean> requestUnlock(WindowHandle)`, `List<CredentialDescriptor> descriptors()`, `void lockStateChanged(LockState)`; prompts: `UnlockPrompt` (`owner()`, `submit(char[])`, `cancel()`, `busy()`, `error()`, `onError(Consumer<String>)`, `onDismiss(Runnable)`), `GrantPrompt` (`grant()`, `consumerName()`, `descriptor()`, `owner()`, `answer(Decision)`, `cancel()`, `onDismiss`), `GrantPrompt.Decision { ALLOW_ONCE, ALWAYS, DENY }`, `PickPrompt` (`owner()`, `choices()`, `choose(UUID)`, `cancel()`, `onDismiss`).

- [ ] **Step 1: Write the failing tests**

`api/CredentialTest.java`:

```java
package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CredentialTest {
    @Test void closeZeroesAndBlocksEveryAccessor() {
        char[] password = "pw".toCharArray(), passphrase = "pp".toCharArray();
        var credential = new Credential(UUID.randomUUID(), "prod", Kind.ACCOUNT_KEY_AND_PASSWORD, "deploy", password, Path.of("/k"), passphrase);
        assertThat(credential.username()).contains("deploy");
        assertThat(credential.password()).isSameAs(password);
        assertThat(credential.keyPath()).contains(Path.of("/k"));
        credential.close();
        assertThat(password).containsOnly((char) 0);
        assertThat(passphrase).containsOnly((char) 0);
        assertThatThrownBy(credential::password).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(credential::username).isInstanceOf(IllegalStateException.class);
        credential.close();
    }

    @Test void aBareKeyHasNoUsernameOrPassword() {
        var credential = new Credential(UUID.randomUUID(), "laptop", Kind.SSH_KEY, null, null, Path.of("/k"), null);
        assertThat(credential.username()).isEmpty();
        assertThat(credential.password()).isNull();
        assertThat(credential.passphrase()).isNull();
        assertThat(credential.toString()).doesNotContain("pw").contains("laptop");
    }
}
```

`service/VaultServiceTest.java`:

```java
package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultServiceTest {
    static final PluginInfo SSH = new PluginInfo("dev.jasper.ssh", "SSH", "0.1.0", Set.of());
    static final PluginInfo OTHER = new PluginInfo("dev.jasper.other", "Other", "0.1.0", Set.of());
    static final UUID PROD = UUID.randomUUID(), KEY = UUID.randomUUID();

    /** A window handle is only an owner here. */
    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final List<UnlockPrompt> unlocks = new ArrayList<>();
    final List<GrantPrompt> grants = new ArrayList<>();
    final List<PickPrompt> picks = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final List<LockState> states = new ArrayList<>();
    final WindowHandle window = window();
    LockManager lock;
    VaultService service;

    /** Inline executors: every future completes before the call returns. */
    VaultService service(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets, Runnable::run, Runnable::run, state -> { states.add(state); if (service != null) service.lockStateChanged(state); });
        service = new VaultService(lock, () -> Optional.of(window), unlocks::add, grants::add, picks::add, notices::add);
        return service;
    }

    /** A vault with one password account and one key, unlocked, password {@code pw}. */
    void populate() {
        lock.create("pw".toCharArray(), false).join();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().keys().add(new SshKey(KEY, "laptop", "ed25519", "SHA256:abc", "", Path.of("/k"), Path.of("/k.pub"), Instant.EPOCH));
        lock.save().join();
    }

    @Test void descriptorsCarryNoSecretsAndNeedNoGrant(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        assertThat(api.lockState()).isEqualTo(LockState.NO_VAULT);
        assertThat(api.credentials()).isEmpty();
        populate();
        assertThat(api.lockState()).isEqualTo(LockState.UNLOCKED);
        assertThat(api.credentials()).containsExactly(
            new CredentialDescriptor(PROD, "prod", "deploy", Kind.ACCOUNT_PASSWORD),
            new CredentialDescriptor(KEY, "laptop", "SHA256:abc", Kind.SSH_KEY));
        assertThat(api.ensureUnlocked(window)).isCompletedWithValue(true);
        assertThat(unlocks).isEmpty();
    }

    @Test void aGrantPromptServesEveryWaiterAndAlwaysIsRemembered(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        CompletableFuture<Optional<Credential>> first = api.credential(PROD), second = api.credential(PROD);
        assertThat(grants).as("one prompt for one (plugin, credential) pair").hasSize(1);
        GrantPrompt prompt = grants.getFirst();
        assertThat(prompt.consumerName()).isEqualTo("SSH");
        assertThat(prompt.descriptor().name()).isEqualTo("prod");
        assertThat(prompt.owner()).isSameAs(window);
        assertThat(first).isNotDone();
        boolean[] dismissed = {false};
        prompt.onDismiss(() -> dismissed[0] = true);
        prompt.answer(GrantPrompt.Decision.ALWAYS);
        assertThat(dismissed[0]).isTrue();
        try (Credential a = first.join().orElseThrow(); Credential b = second.join().orElseThrow()) {
            assertThat(a.password()).isEqualTo("s3cret".toCharArray()).isNotSameAs(b.password());
            assertThat(a.username()).contains("deploy");
            assertThat(a.kind()).isEqualTo(Kind.ACCOUNT_PASSWORD);
        }
        assertThat(((Auth.Password) lock.vault().account(PROD).get().auth()).password()).as("the vault's copy is untouched by close").isEqualTo("s3cret".toCharArray());
        assertThat(lock.vault().grants()).containsExactly(new Grant("dev.jasper.ssh", PROD));
        assertThat(api.credential(PROD)).as("granted: no prompt").isDone();
        assertThat(grants).hasSize(1);

        VaultApi other = service.forConsumer(OTHER);
        CompletableFuture<Optional<Credential>> denied = other.credential(PROD);
        assertThat(grants).as("another plugin gets its own prompt").hasSize(2);
        grants.get(1).answer(GrantPrompt.Decision.DENY);
        assertThat(denied.join()).isEmpty();
        assertThat(lock.vault().grants()).hasSize(1);

        CompletableFuture<Optional<Credential>> once = other.credential(KEY);
        grants.get(2).answer(GrantPrompt.Decision.ALLOW_ONCE);
        try (Credential key = once.join().orElseThrow()) {
            assertThat(key.kind()).isEqualTo(Kind.SSH_KEY);
            assertThat(key.keyPath()).contains(Path.of("/k"));
            assertThat(key.username()).isEmpty();
        }
        assertThat(other.credential(KEY)).as("once means once").isNotDone();
        assertThat(api.credential(UUID.randomUUID())).as("unknown id").isCompletedWithValue(Optional.empty());
    }

    @Test void oneUnlockPromptServesEveryRequesterAndReportsErrors(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.vault().grants().add(new Grant("dev.jasper.ssh", PROD));
        lock.save().join();
        lock.lock();
        VaultApi other = service.forConsumer(OTHER);
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = other.ensureUnlocked(window);
        assertThat(unlocks).hasSize(1);
        UnlockPrompt prompt = unlocks.getFirst();
        List<String> errors = new ArrayList<>();
        prompt.onError(errors::add);
        prompt.submit("wrong".toCharArray());
        assertThat(errors).containsExactly("Wrong password");
        assertThat(prompt.error()).contains("Wrong password");
        assertThat(fetch).isNotDone();
        prompt.submit("pw".toCharArray());
        assertThat(ensure).isCompletedWithValue(true);
        try (Credential credential = fetch.join().orElseThrow()) { assertThat(credential.name()).isEqualTo("prod"); }
        assertThat(grants).as("the stored grant needed no prompt").isEmpty();
        assertThat(states).containsExactly(LockState.UNLOCKED, LockState.LOCKED, LockState.UNLOCKED);
    }

    @Test void cancellingTheLastWaiterDismissesThePrompt(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.lock();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = service.forConsumer(OTHER).ensureUnlocked(window);
        UnlockPrompt prompt = unlocks.getFirst();
        boolean[] dismissed = {false};
        prompt.onDismiss(() -> dismissed[0] = true);
        fetch.cancel(false);
        assertThat(dismissed[0]).as("another requester still waits").isFalse();
        ensure.cancel(false);
        assertThat(dismissed[0]).isTrue();
        api.credential(PROD);
        assertThat(unlocks).as("a new request opens a new prompt").hasSize(2);
        unlocks.get(1).cancel();
        assertThat(unlocks.get(1).busy()).isFalse();
    }

    @Test void userCancelAnswersFalseAndEmpty(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.lock();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = api.ensureUnlocked(window);
        unlocks.getFirst().cancel();
        assertThat(ensure).isCompletedWithValue(false);
        assertThat(fetch).isCompletedWithValue(Optional.empty());
        assertThat(service.requestUnlock(window)).isNotDone();
        assertThat(unlocks).hasSize(2);
    }

    @Test void pickIsAOneTimeGrant(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        CompletableFuture<Optional<CredentialDescriptor>> pick = api.pick(window);
        assertThat(picks).hasSize(1);
        assertThat(picks.getFirst().choices()).extracting(CredentialDescriptor::name).containsExactly("prod", "laptop");
        picks.getFirst().choose(KEY);
        assertThat(pick.join()).map(CredentialDescriptor::id).contains(KEY);
        assertThat(api.credential(KEY)).as("the pick granted this fetch").isDone();
        assertThat(api.credential(KEY)).as("only once").isNotDone();
        CompletableFuture<Optional<CredentialDescriptor>> cancelled = api.pick(window);
        boolean[] dismissed = {false};
        picks.get(1).onDismiss(() -> dismissed[0] = true);
        cancelled.cancel(false);
        assertThat(dismissed[0]).isTrue();
        assertThat(api.pick(window)).isNotDone();
        picks.get(2).cancel();
        assertThat(picks.get(2).settled()).isTrue();
    }

    @Test void noVaultAnswersFalseWithANoticeAndLockingCancelsPrompts(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        assertThat(api.ensureUnlocked(window)).isCompletedWithValue(false);
        assertThat(api.credential(PROD)).isCompletedWithValue(Optional.empty());
        assertThat(api.pick(window)).isCompletedWithValue(Optional.empty());
        assertThat(notices).hasSize(3).allSatisfy(notice -> assertThat(notice).contains("Create a vault"));
        assertThat(unlocks).isEmpty();
        populate();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Optional<CredentialDescriptor>> pick = api.pick(window);
        lock.lock();
        assertThat(fetch).isCompletedWithValue(Optional.empty());
        assertThat(pick).isCompletedWithValue(Optional.empty());
        assertThat(grants.getFirst().settled()).isTrue();
    }

    @Test void withoutAnyWindowARequestFails(@TempDir Path dir) {
        service(dir);
        service = new VaultService(lock, Optional::empty, unlocks::add, grants::add, picks::add, notices::add);
        populate();
        lock.lock();
        assertThat(service.forConsumer(SSH).credential(PROD)).isCompletedWithValue(Optional.empty());
        assertThat(notices).singleElement().satisfies(notice -> assertThat(notice).contains("window"));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement the API package**

`api/Kind.java`:

```java
package dev.jasper.vault.api;

/** What a credential carries: an account's password, key, both, or a standalone SSH key. */
public enum Kind { ACCOUNT_PASSWORD, ACCOUNT_KEY, ACCOUNT_KEY_AND_PASSWORD, SSH_KEY }
```

`api/CredentialDescriptor.java`:

```java
package dev.jasper.vault.api;

import java.util.Objects;
import java.util.UUID;

/** A credential without its secret: the username for an account, the fingerprint for a key, as {@code subtitle}. */
public record CredentialDescriptor(UUID id, String name, String subtitle, Kind kind) {
    public CredentialDescriptor {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind");
        subtitle = subtitle == null ? "" : subtitle;
    }
}
```

`api/Credential.java`:

```java
package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The caller's own copy of a secret. Fetch it just before use and {@link #close()} right after: closing
 * zeroes the arrays and every later accessor throws {@link IllegalStateException}. The vault keeps nothing
 * about it, so an auto-lock never invalidates a credential already in hand.
 */
public final class Credential implements AutoCloseable {
    private final UUID id;
    private final String name;
    private final Kind kind;
    private final String username;
    private final char[] password;
    private final Path keyPath;
    private final char[] passphrase;
    private boolean closed;

    /** Created by the vault; the arrays become this credential's to zero. */
    public Credential(UUID id, String name, Kind kind, String username, char[] password, Path keyPath, char[] passphrase) {
        this.id = Objects.requireNonNull(id, "id"); this.name = Objects.requireNonNull(name, "name"); this.kind = Objects.requireNonNull(kind, "kind");
        this.username = username; this.password = password; this.keyPath = keyPath; this.passphrase = passphrase;
    }

    public UUID id() { open(); return id; }
    public String name() { open(); return name; }
    public Kind kind() { open(); return kind; }
    /** Empty for a bare SSH key. */
    public Optional<String> username() { open(); return Optional.ofNullable(username); }
    /** Null unless the kind has a password. Not a copy: it is zeroed by {@link #close()}. */
    public char[] password() { open(); return password; }
    public Optional<Path> keyPath() { open(); return Optional.ofNullable(keyPath); }
    /** Null when the key has no passphrase or the kind has no key. */
    public char[] passphrase() { open(); return passphrase; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (password != null) Arrays.fill(password, (char) 0);
        if (passphrase != null) Arrays.fill(passphrase, (char) 0);
    }

    private void open() { if (closed) throw new IllegalStateException("The credential is closed"); }

    @Override public String toString() { return "Credential[" + name + ", " + kind + (closed ? ", closed" : "") + "]"; }
}
```

`api/VaultApi.java`:

```java
package dev.jasper.vault.api;

import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Credential Vault as another plugin sees it. Obtain it with {@code services().require(VaultApi.class)}
 * after declaring {@code requires = [{ id = "dev.jasper.vault", version = ">=0.1" }]}. Every method is called on
 * the UI thread and returns at once; every future completes on the UI thread. Cancelling a future withdraws
 * that request, and a prompt nobody waits for any more closes.
 */
public interface VaultApi {
    /** Every lock transition, including the lock at plugin stop. */
    Topic<LockState> LOCK_STATE_CHANGED = Topic.of("dev.jasper.vault.lock-state", LockState.class);

    LockState lockState();

    /** Prompts for the master password over {@code owner} when locked; {@code true} once unlocked, {@code false} on cancel or without a vault. */
    CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner);

    /** Every account and key, without secrets; empty unless unlocked. Needs no grant. */
    List<CredentialDescriptor> credentials();

    /**
     * The secret for {@code id}: unlocks first when needed and asks the user to allow this plugin once or
     * always unless a grant exists. Empty when denied, cancelled, or the id is unknown. Close the result.
     */
    CompletableFuture<Optional<Credential>> credential(UUID id);

    /** The user's choice from a picker over {@code owner}; the choice is a one-time grant for this plugin. */
    CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner);
}
```

- [ ] **Step 4: Implement the service package**

`service/package-info.java`:

```java
/**
 * The request queue behind {@link dev.jasper.vault.api.VaultApi}: one prompt per unlock, per
 * (plugin, credential) grant and per pick; the last cancelled waiter dismisses a prompt. UI thread only.
 */
package dev.jasper.vault.service;
```

`service/UnlockPrompt.java`:

```java
package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.crypto.CorruptVaultException;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.lock.ForeignDeviceException;
import dev.jasper.vault.lock.LockManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** The one master-password prompt; the dialog drives it and every requester waits on it. */
public final class UnlockPrompt {
    private final WindowHandle owner;
    private final LockManager lock;
    private final Runnable onSettled;
    private final List<CompletableFuture<Boolean>> waiters = new ArrayList<>();
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private Consumer<String> errorListener = message -> { };
    private String error;
    private boolean busy, settled;

    UnlockPrompt(WindowHandle owner, LockManager lock, Runnable onSettled) { this.owner = owner; this.lock = lock; this.onSettled = onSettled; }

    public WindowHandle owner() { return owner; }
    public boolean busy() { return busy; }
    public boolean settled() { return settled; }
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** The dialog shows what the last attempt said. */
    public void onError(Consumer<String> listener) { errorListener = listener; }
    /** The dialog closes itself here. */
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    CompletableFuture<Boolean> attach() {
        var waiter = new CompletableFuture<Boolean>();
        waiters.add(waiter);
        waiter.whenComplete((ignored, failure) -> { if (waiter.isCancelled()) detach(waiter); });
        return waiter;
    }

    private void detach(CompletableFuture<Boolean> waiter) {
        waiters.remove(waiter);
        if (waiters.isEmpty() && !settled && !busy) finish(false);
    }

    /** Tries {@code password} (zeroed by the lock manager); on success every waiter gets {@code true}. */
    public void submit(char[] password) {
        if (busy || settled) { java.util.Arrays.fill(password, (char) 0); return; }
        busy = true; error = null;
        lock.unlock(password).whenComplete((ignored, failure) -> {
            busy = false;
            if (settled) return;
            if (failure == null) { finish(true); return; }
            Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null ? wrapped.getCause() : failure;
            error = switch (cause) {
                case WrongPasswordException wrong -> "Wrong password";
                case ForeignDeviceException foreign -> foreign.getMessage();
                case CorruptVaultException corrupt -> corrupt.getMessage();
                default -> "Could not open the vault: " + cause.getMessage();
            };
            errorListener.accept(error);
            if (waiters.isEmpty()) finish(false);   // every requester left while the attempt ran
        });
    }

    /** The user closed the dialog: every waiter gets {@code false}. */
    public void cancel() { if (!settled) finish(false); }

    private void finish(boolean unlocked) {
        settled = true;
        onSettled.run();
        dismissListeners.forEach(Runnable::run);
        for (CompletableFuture<Boolean> waiter : List.copyOf(waiters)) waiter.complete(unlocked);
        waiters.clear();
    }
}
```

`service/GrantPrompt.java`:

```java
package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.model.Grant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** "Allow SSH to use deploy@prod?" — one per (plugin, credential) pair, shared by every waiting fetch. */
public final class GrantPrompt {
    public enum Decision { ALLOW_ONCE, ALWAYS, DENY }

    private final Grant grant;
    private final String consumerName;
    private final CredentialDescriptor descriptor;
    private final WindowHandle owner;
    private final BiConsumer<GrantPrompt, Decision> onAnswer;
    private final List<CompletableFuture<Optional<Credential>>> waiters = new ArrayList<>();
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private boolean settled;

    GrantPrompt(Grant grant, String consumerName, CredentialDescriptor descriptor, WindowHandle owner, BiConsumer<GrantPrompt, Decision> onAnswer) {
        this.grant = grant; this.consumerName = consumerName; this.descriptor = descriptor; this.owner = owner; this.onAnswer = onAnswer;
    }

    public Grant grant() { return grant; }
    public String consumerName() { return consumerName; }
    public CredentialDescriptor descriptor() { return descriptor; }
    public WindowHandle owner() { return owner; }
    public boolean settled() { return settled; }
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    public void answer(Decision decision) { if (!settled) onAnswer.accept(this, decision); }
    /** Closing the dialog denies. */
    public void cancel() { answer(Decision.DENY); }

    void attach(CompletableFuture<Optional<Credential>> waiter) {
        waiters.add(waiter);
        waiter.whenComplete((ignored, failure) -> {
            if (!waiter.isCancelled()) return;
            waiters.remove(waiter);
            if (waiters.isEmpty() && !settled) onAnswer.accept(this, Decision.DENY);
        });
    }

    /** Completes every waiter with its own value and closes the dialog. */
    void settle(Supplier<Optional<Credential>> perWaiter) {
        settled = true;
        dismissListeners.forEach(Runnable::run);
        for (CompletableFuture<Optional<Credential>> waiter : List.copyOf(waiters)) if (!waiter.isDone()) waiter.complete(perWaiter.get());
        waiters.clear();
    }
}
```

`service/PickPrompt.java`:

```java
package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.CredentialDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The account picker for one {@code pick} call. */
public final class PickPrompt {
    private final WindowHandle owner;
    private final List<CredentialDescriptor> choices;
    private final CompletableFuture<Optional<CredentialDescriptor>> waiter;
    private final Consumer<UUID> onChosen;
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private boolean settled;

    PickPrompt(WindowHandle owner, List<CredentialDescriptor> choices, CompletableFuture<Optional<CredentialDescriptor>> waiter, Consumer<UUID> onChosen) {
        this.owner = owner; this.choices = List.copyOf(choices); this.waiter = waiter; this.onChosen = onChosen;
        waiter.whenComplete((ignored, failure) -> { if (waiter.isCancelled()) dismiss(); });
    }

    public WindowHandle owner() { return owner; }
    public List<CredentialDescriptor> choices() { return choices; }
    public boolean settled() { return settled; }
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    public void choose(UUID id) {
        if (settled) return;
        Optional<CredentialDescriptor> chosen = choices.stream().filter(choice -> choice.id().equals(id)).findFirst();
        chosen.ifPresent(choice -> onChosen.accept(choice.id()));
        dismiss();
        waiter.complete(chosen);
    }

    public void cancel() { if (settled) return; dismiss(); waiter.complete(Optional.empty()); }

    void dismiss() { if (settled) return; settled = true; dismissListeners.forEach(Runnable::run); }
}
```

`service/VaultService.java`:

```java
package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One per plugin instance; {@link #forConsumer} hands each requiring plugin its own view. */
public final class VaultService {
    static final String NO_VAULT_NOTICE = "Create a vault in Credential Vault first (Open Vault..., F8)";
    static final String NO_WINDOW_NOTICE = "Credential Vault has no window to ask in";

    private final LockManager lock;
    private final Supplier<Optional<WindowHandle>> fallbackOwner;
    private final Consumer<UnlockPrompt> showUnlock;
    private final Consumer<GrantPrompt> showGrant;
    private final Consumer<PickPrompt> showPick;
    private final Consumer<String> notice;
    private UnlockPrompt unlock;
    private final Map<Grant, GrantPrompt> grantPrompts = new HashMap<>();
    private final List<PickPrompt> pickPrompts = new ArrayList<>();
    private final Set<Grant> oneTime = new HashSet<>();
    private final Map<String, WindowHandle> lastOwner = new HashMap<>();

    public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock,
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice) {
        this.lock = lock; this.fallbackOwner = fallbackOwner; this.showUnlock = showUnlock; this.showGrant = showGrant; this.showPick = showPick; this.notice = notice;
    }

    public VaultApi forConsumer(PluginInfo consumer) { return new ConsumerApi(consumer); }

    /** Wire this to the lock manager's listener: a lock forgets one-time grants and closes every grant and pick prompt. */
    public void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) return;
        oneTime.clear();
        for (GrantPrompt prompt : List.copyOf(grantPrompts.values())) prompt.answer(GrantPrompt.Decision.DENY);
        for (PickPrompt prompt : List.copyOf(pickPrompts)) prompt.cancel();
    }

    /** The unlock prompt over {@code owner}, shared with every other requester; {@code true} once unlocked. */
    public CompletableFuture<Boolean> requestUnlock(WindowHandle owner) {
        switch (lock.state()) {
            case UNLOCKED: return CompletableFuture.completedFuture(true);
            case NO_VAULT: notice.accept(NO_VAULT_NOTICE); return CompletableFuture.completedFuture(false);
            default:
        }
        if (unlock == null) {
            unlock = new UnlockPrompt(owner, lock, () -> unlock = null);
            CompletableFuture<Boolean> waiter = unlock.attach();
            showUnlock.accept(unlock);
            return waiter;
        }
        return unlock.attach();
    }

    /** Every account and key without secrets; empty unless unlocked. */
    public List<CredentialDescriptor> descriptors() {
        if (lock.state() != LockState.UNLOCKED) return List.of();
        List<CredentialDescriptor> out = new ArrayList<>();
        for (Account account : lock.vault().accounts()) out.add(describe(account));
        for (SshKey key : lock.vault().keys()) out.add(describe(key));
        return List.copyOf(out);
    }

    static CredentialDescriptor describe(Account account) {
        Kind kind = switch (account.auth()) {
            case Auth.Password password -> Kind.ACCOUNT_PASSWORD;
            case Auth.Key key -> Kind.ACCOUNT_KEY;
            case Auth.KeyAndPassword both -> Kind.ACCOUNT_KEY_AND_PASSWORD;
        };
        return new CredentialDescriptor(account.id(), account.name(), account.username(), kind);
    }

    static CredentialDescriptor describe(SshKey key) { return new CredentialDescriptor(key.id(), key.name(), key.fingerprint(), Kind.SSH_KEY); }

    private Optional<CredentialDescriptor> descriptor(UUID id) { return descriptors().stream().filter(d -> d.id().equals(id)).findFirst(); }

    /** A fresh copy for one caller; empty when locked or unknown. */
    private Optional<Credential> copyOf(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return Optional.empty();
        Optional<Account> account = lock.vault().account(id);
        if (account.isPresent()) {
            Account a = account.get();
            return Optional.of(switch (a.auth()) {
                case Auth.Password p -> new Credential(a.id(), a.name(), Kind.ACCOUNT_PASSWORD, a.username(), p.password().clone(), null, null);
                case Auth.Key k -> new Credential(a.id(), a.name(), Kind.ACCOUNT_KEY, a.username(), null, k.keyPath(), k.passphrase() == null ? null : k.passphrase().clone());
                case Auth.KeyAndPassword b -> new Credential(a.id(), a.name(), Kind.ACCOUNT_KEY_AND_PASSWORD, a.username(), b.password().clone(), b.keyPath(), b.passphrase() == null ? null : b.passphrase().clone());
            });
        }
        return lock.vault().key(id).map(key -> new Credential(key.id(), key.name(), Kind.SSH_KEY, null, null, key.privatePath(), null));
    }

    private void answered(GrantPrompt prompt, GrantPrompt.Decision decision) {
        grantPrompts.remove(prompt.grant());
        switch (decision) {
            case DENY -> prompt.settle(Optional::empty);
            case ALLOW_ONCE -> prompt.settle(() -> copyOf(prompt.grant().credentialId()));
            case ALWAYS -> {
                if (lock.state() == LockState.UNLOCKED) { lock.vault().grants().add(prompt.grant()); lock.save(); }
                prompt.settle(() -> copyOf(prompt.grant().credentialId()));
            }
        }
    }

    private Optional<WindowHandle> ownerFor(PluginInfo consumer) {
        WindowHandle named = lastOwner.get(consumer.id());
        return named != null && named.isOpen() ? Optional.of(named) : fallbackOwner.get();
    }

    private final class ConsumerApi implements VaultApi {
        private final PluginInfo consumer;
        ConsumerApi(PluginInfo consumer) { this.consumer = consumer; }

        @Override public LockState lockState() { return lock.state(); }

        @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) {
            lastOwner.put(consumer.id(), owner);
            return requestUnlock(owner);
        }

        @Override public List<CredentialDescriptor> credentials() { return descriptors(); }

        @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
            var result = new CompletableFuture<Optional<Credential>>();
            Optional<WindowHandle> owner = ownerFor(consumer);
            if (lock.state() != LockState.UNLOCKED && lock.state() != LockState.NO_VAULT && owner.isEmpty()) {
                notice.accept(NO_WINDOW_NOTICE);
                result.complete(Optional.empty());
                return result;
            }
            CompletableFuture<Boolean> unlocked = requestUnlock(owner.orElse(null));
            result.whenComplete((ignored, failure) -> { if (result.isCancelled()) unlocked.cancel(false); });
            unlocked.thenAccept(ok -> {
                if (result.isDone()) return;
                if (!ok) { result.complete(Optional.empty()); return; }
                Optional<CredentialDescriptor> described = descriptor(id);
                if (described.isEmpty()) { result.complete(Optional.empty()); return; }
                Grant grant = new Grant(consumer.id(), id);
                if (lock.vault().grants().contains(grant) || oneTime.remove(grant)) { result.complete(copyOf(id)); return; }
                GrantPrompt prompt = grantPrompts.get(grant);
                if (prompt == null) {
                    prompt = new GrantPrompt(grant, consumer.name(), described.get(), owner.orElse(null), VaultService.this::answered);
                    grantPrompts.put(grant, prompt);
                    prompt.attach(result);
                    showGrant.accept(prompt);
                } else prompt.attach(result);
            });
            return result;
        }

        @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) {
            lastOwner.put(consumer.id(), owner);
            var result = new CompletableFuture<Optional<CredentialDescriptor>>();
            CompletableFuture<Boolean> unlocked = requestUnlock(owner);
            result.whenComplete((ignored, failure) -> { if (result.isCancelled()) unlocked.cancel(false); });
            unlocked.thenAccept(ok -> {
                if (result.isDone()) return;
                if (!ok) { result.complete(Optional.empty()); return; }
                var prompt = new PickPrompt(owner, descriptors(), result, chosen -> oneTime.add(new Grant(consumer.id(), chosen)));
                pickPrompts.add(prompt);
                prompt.onDismiss(() -> pickPrompts.remove(prompt));
                showPick.accept(prompt);
            });
            return result;
        }
    }
}
```

The three prompts accumulate dismiss listeners (the service registers one on a pick prompt before the dialog adds its own), run in registration order.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS. If `cancellingTheLastWaiterDismissesThePrompt` fails because the unlock `whenComplete` handler in `UnlockPrompt.attach` runs before `waiters.remove`, check that `detach` is only reached through the cancellation path (a `complete(...)` from `finish` clears the list first).

- [ ] **Step 6: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): the VaultApi contract and the per-consumer request service with grants and cancellation"
```

---

### Task 7: SSH key generator

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/keygen/package-info.java`, `KeyAlgorithm.java`, `KeyGenerator.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/keygen/KeyGeneratorTest.java`

**Interfaces:**
- Consumes: `SshKey`.
- Produces: `KeyAlgorithm { ED25519, ECDSA_P256, ECDSA_P384, RSA_3072, RSA_4096 }` with `id()`, `label()`, `sshType()`; `KeyGenerator(Path directory)` with `SshKey generate(KeyAlgorithm, String name, String comment) throws IOException` and `void delete(SshKey) throws IOException`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.vault.keygen;

import dev.jasper.vault.model.SshKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeyGeneratorTest {
    static AsymmetricKeyParameter readPrivate(Path file) throws Exception {
        String pem = Files.readString(file);
        String body = pem.lines().filter(line -> !line.startsWith("-----")).reduce("", String::concat);
        return OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(Base64.getDecoder().decode(body));
    }

    @Test void ed25519KeyIsOpenSshFormatWithMatchingPublicLineAndFingerprint(@TempDir Path dir) throws Exception {
        SshKey key = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "laptop", "me@laptop");
        assertThat(key.name()).isEqualTo("laptop");
        assertThat(key.algorithm()).isEqualTo("ed25519");
        assertThat(key.privatePath().getFileName().toString()).startsWith("id_ed25519_").hasSize("id_ed25519_".length() + 8);
        assertThat(key.publicPath()).isEqualTo(key.privatePath().resolveSibling(key.privatePath().getFileName() + ".pub"));
        assertThat(Files.readString(key.privatePath())).startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n").endsWith("-----END OPENSSH PRIVATE KEY-----\n");
        assertThat(readPrivate(key.privatePath())).isInstanceOf(Ed25519PrivateKeyParameters.class);
        String[] parts = Files.readString(key.publicPath()).strip().split(" ");
        assertThat(parts).hasSize(3).startsWith("ssh-ed25519").endsWith("me@laptop");
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        assertThat(OpenSSHPublicKeyUtil.parsePublicKey(blob)).isNotNull();
        assertThat(key.fingerprint()).isEqualTo("SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob)));
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(key.privatePath())).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            assertThat(Files.getPosixFilePermissions(dir.resolve("keys"))).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE, java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        }
        new KeyGenerator(dir.resolve("keys")).delete(key);
        assertThat(key.privatePath()).doesNotExist();
        assertThat(key.publicPath()).doesNotExist();
    }

    @Test void ecdsaAndRsaUseTheirTraditionalPemLabels(@TempDir Path dir) throws Exception {
        var generator = new KeyGenerator(dir);
        SshKey ec = generator.generate(KeyAlgorithm.ECDSA_P256, "ec", "");
        assertThat(Files.readString(ec.privatePath())).startsWith("-----BEGIN EC PRIVATE KEY-----");
        assertThat(readPrivate(ec.privatePath())).isInstanceOf(ECPrivateKeyParameters.class);
        assertThat(Files.readString(ec.publicPath())).startsWith("ecdsa-sha2-nistp256 ").doesNotEndWith(" \n");
        SshKey rsa = generator.generate(KeyAlgorithm.RSA_3072, "rsa", "c");
        assertThat(Files.readString(rsa.privatePath())).startsWith("-----BEGIN RSA PRIVATE KEY-----");
        assertThat(readPrivate(rsa.privatePath())).isInstanceOf(RSAPrivateCrtKeyParameters.class);
        assertThat(Files.readString(rsa.publicPath())).startsWith("ssh-rsa ");
        assertThat(KeyAlgorithm.ECDSA_P384.sshType()).isEqualTo("ecdsa-sha2-nistp384");
        assertThat(KeyAlgorithm.RSA_4096.label()).isEqualTo("RSA 4096");
    }

    /** With OpenSSH on the PATH, its fingerprint matches ours. */
    @Test void sshKeygenAgreesOnTheFingerprint(@TempDir Path dir) throws Exception {
        Path sshKeygen = java.util.stream.Stream.of(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator))
            .map(entry -> Path.of(entry, "ssh-keygen")).filter(Files::isExecutable).findFirst().orElse(null);
        assumeTrue(sshKeygen != null, "ssh-keygen not on PATH");
        SshKey key = new KeyGenerator(dir).generate(KeyAlgorithm.ED25519, "k", "x");
        Process process = new ProcessBuilder(sshKeygen.toString(), "-l", "-f", key.publicPath().toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).contains(key.fingerprint());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`keygen/package-info.java`:

```java
/** SSH key pairs in OpenSSH's formats, written with owner-only permissions. */
package dev.jasper.vault.keygen;
```

`KeyAlgorithm.java`:

```java
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
```

`KeyGenerator.java`:

```java
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
        Files.writeString(privatePath, pem(algorithm, OpenSSHPrivateKeyUtil.encodePrivateKey(pair.getPrivate())), StandardCharsets.US_ASCII);
        permissions(privatePath, "rw-------");
        byte[] publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.getPublic());
        String line = algorithm.sshType() + " " + Base64.getEncoder().encodeToString(publicBlob) + (comment == null || comment.isBlank() ? "" : " " + comment.strip());
        Files.writeString(publicPath, line + "\n", StandardCharsets.US_ASCII);
        return new SshKey(id, name, algorithm.id(), fingerprint(publicBlob), comment == null ? "" : comment.strip(), privatePath.toAbsolutePath(), publicPath.toAbsolutePath(), Instant.now());
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS (the `ssh-keygen` test runs on the dev Mac). If `OpenSSHPublicKeyUtil.encodePublicKey` rejects the EC key with "unable to derive ssh curve name", the domain parameters are not named: keep `ECNamedDomainParameters` as written.

- [ ] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): OpenSSH key generation for Ed25519, ECDSA and RSA"
```

---

### Task 8: Dialog panels, the plugin, and its host tests

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/package-info.java`, `PasswordPanel.java`, `GrantPanel.java`, `PickerPanel.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`, `VaultSettings.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/PanelsTest.java`, `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`

**Interfaces:**
- Consumes: everything above; SDK `Plugin`, `PluginContext`, `ActionSpec`, `DialogSpec`, `PluginDialog`, `Terminals`, `Events`, `Services`.
- Produces: `VaultPlugin()` and the test constructor `VaultPlugin(Function<PluginContext, DeviceSecrets> secrets, Executor ui)`; constants `VaultPlugin.OPEN = "dev.jasper.vault.open"`, `LOCK = "dev.jasper.vault.lock"`; package-private `service()`, `lockManager()`, `timer()`, `tick()`, `showCreate(WindowHandle)`; `VaultSettings.read(PluginConfig, Path dataDirectory)` → record `VaultSettings(Duration autoLock, Path keysDirectory, boolean bindByDefault)`; `PasswordPanel.unlock(UnlockPrompt)`, `PasswordPanel.create(boolean bindDefault, BiConsumer<char[], Boolean> onCreate, Runnable onCancel)` with package-private `password`, `confirm`, `bind`, `message`, `primary`, `cancel` fields; `GrantPanel(GrantPrompt)`; `PickerPanel(PickPrompt)`.

- [ ] **Step 1: Write the failing tests**

`ui/PanelsTest.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PanelsTest {
    @Test void createPanelRequiresMatchingNonEmptyPasswords() {
        List<String> created = new ArrayList<>();
        boolean[] cancelled = {false};
        PasswordPanel panel = PasswordPanel.create(true, (password, bind) -> created.add(new String(password) + ":" + bind), () -> cancelled[0] = true);
        assertThat(panel.bind.isSelected()).isTrue();
        panel.primary.doClick();
        assertThat(panel.message.getText()).contains("at least 8");
        panel.password.setText("hunter2hunter2"); panel.confirm.setText("different");
        panel.primary.doClick();
        assertThat(panel.message.getText()).contains("do not match");
        assertThat(created).isEmpty();
        panel.confirm.setText("hunter2hunter2"); panel.bind.setSelected(false);
        panel.primary.doClick();
        assertThat(created).containsExactly("hunter2hunter2:false");
        assertThat(panel.password.getPassword()).as("fields cleared after use").isEmpty();
        panel.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
    }

    @Test void pickerListsChoicesAndReportsTheSelection() {
        var choices = List.of(new CredentialDescriptor(UUID.randomUUID(), "prod", "deploy", Kind.ACCOUNT_PASSWORD), new CredentialDescriptor(UUID.randomUUID(), "laptop", "SHA256:x", Kind.SSH_KEY));
        List<UUID> chosen = new ArrayList<>();
        boolean[] cancelled = {false};
        var panel = new PickerPanel(choices, chosen::add, () -> cancelled[0] = true);
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        assertThat(panel.primary.isEnabled()).isFalse();
        panel.list.setSelectedIndex(1);
        assertThat(panel.primary.isEnabled()).isTrue();
        panel.primary.doClick();
        assertThat(chosen).containsExactly(choices.get(1).id());
        panel.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
        assertThat(PickerPanel.label(choices.getFirst())).isEqualTo("prod  —  deploy");
    }
}
```

`VaultPluginTest.java`:

```java
package dev.jasper.vault;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.1.0", Set.of());
    static final PluginInfo SSH = new PluginInfo("dev.jasper.ssh", "SSH", "0.1.0", Set.of());

    static VaultPlugin plugin() {
        return new VaultPlugin(context -> new DeviceSecrets(
            new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, context.dataDirectory().resolve("x")),
            new FileStore(context.dataDirectory().resolve("device.secret"))), Runnable::run);
    }

    @Test void publishesTheServiceActionsAndLockStateEvents() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            AtomicReference<VaultApi> api = new AtomicReference<>();
            List<LockState> seen = new ArrayList<>();
            host.start(SSH, Set.of("dev.jasper.vault"), Set.of(), context -> {
                api.set(context.services().require(VaultApi.class));
                context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, seen::add);
            });
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.vault.open|Open Vault...|true", "dev.jasper.vault.lock|Lock Vault|false");
            UUID window = host.addTerminalWindow();
            assertThat(api.get().lockState()).isEqualTo(LockState.NO_VAULT);
            assertThat(host.invoke(VaultPlugin.OPEN, window, null)).isTrue();
            assertThat(host.windows()).as("no vault: the create dialog").containsExactly("dialog|Create Vault|true");
            plugin.lockManager().create("hunter2!".toCharArray(), true);
            host.runBackground();
            host.flush();
            assertThat(api.get().lockState()).isEqualTo(LockState.UNLOCKED);
            assertThat(seen).containsExactly(LockState.UNLOCKED);
            assertThat(host.actions()).contains("dev.jasper.vault.lock|Lock Vault|true");
            assertThat(host.invoke(VaultPlugin.LOCK, window, null)).isTrue();
            host.flush();
            assertThat(seen).containsExactly(LockState.UNLOCKED, LockState.LOCKED);
            assertThat(host.invoke(VaultPlugin.OPEN, window, null)).isTrue();
            assertThat(host.windows()).as("locked: the unlock dialog").contains("dialog|Unlock Vault|true");
        }
    }

    @Test void aConsumerFetchesThroughUnlockAndGrantDialogs() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            AtomicReference<VaultApi> api = new AtomicReference<>();
            host.start(SSH, Set.of("dev.jasper.vault"), Set.of(), context -> api.set(context.services().require(VaultApi.class)));
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            UUID id = UUID.randomUUID();
            plugin.lockManager().vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
            plugin.lockManager().save();
            host.runBackground();
            plugin.lockManager().lock();
            CompletableFuture<Optional<Credential>> fetch = api.get().credential(id);
            assertThat(host.windows()).containsExactly("dialog|Unlock Vault|true");
            plugin.service().requestUnlock(null);   // a second requester joins the same prompt
            assertThat(host.windows()).hasSize(1);
            plugin.currentUnlock().submit("hunter2!".toCharArray());
            host.runBackground();
            assertThat(host.windows()).as("unlock closed, grant open").containsExactly("dialog|Allow SSH to use prod?|true");
            plugin.currentGrant().answer(dev.jasper.vault.service.GrantPrompt.Decision.ALLOW_ONCE);
            try (Credential credential = fetch.join().orElseThrow()) { assertThat(credential.password()).isEqualTo("s3cret".toCharArray()); }
            assertThat(host.windows()).isEmpty();
            CompletableFuture<Optional<Credential>> cancelled = api.get().credential(id);
            assertThat(host.windows()).hasSize(1);
            cancelled.cancel(false);
            assertThat(host.windows()).as("the last waiter left: dialog closed").isEmpty();
        }
    }

    @Test void autoLockFollowsTheSettingsAndStopLocks() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 1L));
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            assertThat(plugin.timer().timeout()).isEqualTo(Duration.ofMinutes(1));
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.clock = Duration.ofMinutes(1).toMillis();
            plugin.tick();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 0L));
            host.flush();
            assertThat(plugin.timer().timeout()).isEqualTo(Duration.ZERO);
            plugin.lockManager().unlock("hunter2!".toCharArray());
            host.runBackground();
            plugin.clock += Duration.ofDays(1).toMillis();
            plugin.tick();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            host.stopAll();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
        }
    }

    @Test void settingsResolveTheKeysDirectoryAndDefaults() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.vault", Map.of("keys_directory", "/elsewhere/keys", "bind_new_vaults_to_device", false));
            var context = host.start(INFO, Set.of(), Set.of(), plugin());
            VaultSettings settings = VaultSettings.read(context.config(), context.dataDirectory());
            assertThat(settings.keysDirectory()).isEqualTo(java.nio.file.Path.of("/elsewhere/keys"));
            assertThat(settings.bindByDefault()).isFalse();
            assertThat(settings.autoLock()).isEqualTo(Duration.ofMinutes(15));
        }
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(VaultSettings.read(context.config(), context.dataDirectory()).keysDirectory()).isEqualTo(context.dataDirectory().resolve("keys"));
        }
    }
}
```

The `actions()` line format is the fake host's: `id|title|binding|enabled` — check `FakePluginHost.actions()` and adjust the expected strings to what it renders before running.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [ ] **Step 3: Implement the panels**

`ui/package-info.java`:

```java
/** Swing content for the plugin's dialogs; the host supplies the dialog windows. Plan 6b adds the manager. */
package dev.jasper.vault.ui;
```

`ui/PasswordPanel.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.service.UnlockPrompt;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** The master-password form: unlock (one field) or create (two fields and the bind checkbox). */
public final class PasswordPanel extends JPanel {
    static final int MIN_LENGTH = 8;
    final JPasswordField password = new JPasswordField(24);
    final JPasswordField confirm = new JPasswordField(24);
    final JCheckBox bind = new JCheckBox("Bind to this device (recommended)");
    final JLabel message = new JLabel(" ");
    final JButton primary;
    final JButton cancel = new JButton("Cancel");

    private PasswordPanel(String primaryTitle, boolean creating, boolean bindDefault, String intro) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        primary = new JButton(primaryTitle);
        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(2, 2, 2, 2); at.anchor = GridBagConstraints.WEST; at.gridy = 0; at.gridwidth = 2;
        form.add(new JLabel(intro), at);
        at.gridwidth = 1; at.gridy++;
        form.add(new JLabel("Master password:"), at); at.gridx = 1; form.add(password, at);
        if (creating) {
            at.gridx = 0; at.gridy++;
            form.add(new JLabel("Confirm:"), at); at.gridx = 1; form.add(confirm, at);
            at.gridx = 0; at.gridy++; at.gridwidth = 2;
            bind.setSelected(bindDefault);
            form.add(bind, at);
        }
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(primary);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);
        password.addActionListener(event -> primary.doClick());
        confirm.addActionListener(event -> primary.doClick());
    }

    /** The unlock form bound to {@code prompt}: Enter submits, errors show, the prompt's dismissal is the dialog's. */
    public static PasswordPanel unlock(UnlockPrompt prompt) {
        var panel = new PasswordPanel("Unlock", false, false, "Enter the master password to unlock the vault.");
        prompt.onError(text -> { panel.message.setText(text); panel.primary.setEnabled(true); panel.password.selectAll(); panel.password.requestFocusInWindow(); });
        panel.primary.addActionListener(event -> {
            char[] typed = panel.password.getPassword();
            if (typed.length == 0) { Arrays.fill(typed, (char) 0); return; }
            panel.primary.setEnabled(false);
            panel.message.setText("Unlocking...");
            panel.password.setText("");
            prompt.submit(typed);
        });
        panel.cancel.addActionListener(event -> prompt.cancel());
        return panel;
    }

    /** The create form: two matching passwords of at least {@value #MIN_LENGTH} characters and the bind choice. */
    public static PasswordPanel create(boolean bindDefault, BiConsumer<char[], Boolean> onCreate, Runnable onCancel) {
        var panel = new PasswordPanel("Create Vault", true, bindDefault, "Choose a master password. It cannot be recovered if forgotten.");
        panel.primary.addActionListener(event -> {
            char[] typed = panel.password.getPassword(), again = panel.confirm.getPassword();
            try {
                if (typed.length < MIN_LENGTH) { panel.message.setText("Use at least " + MIN_LENGTH + " characters"); return; }
                if (!Arrays.equals(typed, again)) { panel.message.setText("The passwords do not match"); return; }
                panel.password.setText(""); panel.confirm.setText("");
                onCreate.accept(typed.clone(), panel.bind.isSelected());
            } finally { Arrays.fill(typed, (char) 0); Arrays.fill(again, (char) 0); }
        });
        panel.cancel.addActionListener(event -> onCancel.run());
        return panel;
    }
}
```

`ui/GrantPanel.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.service.GrantPrompt;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** "Allow SSH to use deploy@prod?" with Allow once, Always allow for SSH, Deny. */
public final class GrantPanel extends JPanel {
    final JButton once = new JButton("Allow once");
    final JButton always;
    final JButton deny = new JButton("Deny");

    public GrantPanel(GrantPrompt prompt) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        always = new JButton("Always allow for " + prompt.consumerName());
        String what = prompt.descriptor().subtitle().isBlank() ? prompt.descriptor().name() : prompt.descriptor().subtitle() + "@" + prompt.descriptor().name();
        add(new JLabel("<html><b>" + escape(prompt.consumerName()) + "</b> wants to use <b>" + escape(what) + "</b> from the vault.</html>"), BorderLayout.CENTER);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(deny); buttons.add(Box.createHorizontalGlue()); buttons.add(always); buttons.add(Box.createHorizontalStrut(8)); buttons.add(once);
        add(buttons, BorderLayout.SOUTH);
        once.addActionListener(event -> prompt.answer(GrantPrompt.Decision.ALLOW_ONCE));
        always.addActionListener(event -> prompt.answer(GrantPrompt.Decision.ALWAYS));
        deny.addActionListener(event -> prompt.answer(GrantPrompt.Decision.DENY));
    }

    static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
```

`ui/PickerPanel.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.service.PickPrompt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** The account picker: a list of descriptors, Choose and Cancel. */
public final class PickerPanel extends JPanel {
    final JList<CredentialDescriptor> list;
    final JButton primary = new JButton("Choose");
    final JButton cancel = new JButton("Cancel");

    public PickerPanel(PickPrompt prompt) { this(prompt.choices(), prompt::choose, prompt::cancel); }

    PickerPanel(List<CredentialDescriptor> choices, Consumer<UUID> onChoose, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var model = new DefaultListModel<CredentialDescriptor>();
        choices.forEach(model::addElement);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((view, value, index, selected, focused) -> {
            var label = new javax.swing.JLabel(label(value));
            label.setOpaque(true);
            label.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
            label.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        });
        var scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 240));
        add(scroll, BorderLayout.CENTER);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(primary);
        add(buttons, BorderLayout.SOUTH);
        primary.setEnabled(false);
        list.addListSelectionListener(event -> primary.setEnabled(list.getSelectedValue() != null));
        primary.addActionListener(event -> { CredentialDescriptor chosen = list.getSelectedValue(); if (chosen != null) onChoose.accept(chosen.id()); });
        cancel.addActionListener(event -> onCancel.run());
    }

    static String label(CredentialDescriptor descriptor) {
        return descriptor.subtitle().isBlank() ? descriptor.name() : descriptor.name() + "  —  " + descriptor.subtitle();
    }
}
```

- [ ] **Step 4: Implement the settings and the plugin**

`VaultSettings.java`:

```java
package dev.jasper.vault;

import dev.jasper.sdk.plugin.PluginConfig;
import java.nio.file.Path;
import java.time.Duration;

/** The three keys of {@code dev.jasper.vault.toml}, with the spec's defaults. */
record VaultSettings(Duration autoLock, Path keysDirectory, boolean bindByDefault) {
    static VaultSettings read(PluginConfig config, Path dataDirectory) {
        long minutes = config.integer("auto_lock_minutes").orElse(15);
        String keys = config.string("keys_directory").orElse("").strip();
        return new VaultSettings(Duration.ofMinutes(Math.max(0, minutes)), keys.isEmpty() ? dataDirectory.resolve("keys") : Path.of(keys),
            config.bool("bind_new_vaults_to_device").orElse(true));
    }
}
```

`VaultPlugin.java`:

```java
package dev.jasper.vault;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.lock.InactivityTimer;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.service.GrantPrompt;
import dev.jasper.vault.service.PickPrompt;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import dev.jasper.vault.ui.GrantPanel;
import dev.jasper.vault.ui.PasswordPanel;
import dev.jasper.vault.ui.PickerPanel;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the vault to Jasper: the lock manager on the plugin's executors, the service published per
 * consumer, the Open and Lock actions, the lock-state topic, the settings file and the inactivity lock.
 * Plan 6b adds the manager window, status item, rail action and palette scope.
 */
public class VaultPlugin implements Plugin {
    public static final String OPEN = "dev.jasper.vault.open";
    public static final String LOCK = "dev.jasper.vault.lock";
    private static final int TICK_MILLIS = 5_000;

    private final Function<PluginContext, DeviceSecrets> secretsFactory;
    private final Executor ui;
    private PluginContext context;
    private LockManager lock;
    private VaultService service;
    private InactivityTimer timer;
    private PluginAction lockAction;
    private UnlockPrompt currentUnlock;
    private GrantPrompt currentGrant;
    private Timer ticker;
    private AWTEventListener activity;
    private VaultSettings settings;
    /** Milliseconds for the inactivity clock; tests set it, production reads the wall clock. */
    long clock = -1;

    /** Created by the runtime: the platform keychain with the file fallback, completing on the EDT. */
    public VaultPlugin() {
        this(context -> new DeviceSecrets(KeychainStore.forPlatform(context.dataDirectory()), new FileStore(context.dataDirectory().resolve("device.secret"))), SwingUtilities::invokeLater);
    }

    /** For tests: the device secret store to use and the executor that stands in for the UI thread. */
    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) { this.secretsFactory = secretsFactory; this.ui = ui; }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = VaultSettings.read(context.config(), context.dataDirectory());
        timer = new InactivityTimer(() -> clock >= 0 ? clock : System.currentTimeMillis());
        timer.setTimeout(settings.autoLock());
        lock = new LockManager(new VaultFile(context.dataDirectory().resolve("vault.jv")), secretsFactory.apply(context), context.background(), ui, this::lockStateChanged);
        service = new VaultService(lock, () -> context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()),
            this::showUnlock, this::showGrant, this::showPick, context.notices()::error);
        context.services().publishPerConsumer(VaultApi.class, service::forConsumer);
        context.actions().register(ActionSpec.of(OPEN, "Open Vault...").withKeywords(List.of("vault", "credentials", "password", "unlock")).withDefaultBinding("F8"),
            invoked -> open(invoked.window()));
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock")), invoked -> lock.lock());
        lockAction.setEnabled(false);
        context.config().onChanged(() -> { settings = VaultSettings.read(context.config(), context.dataDirectory()); timer.setTimeout(settings.autoLock()); });
        activity = event -> timer.touch();
        Toolkit.getDefaultToolkit().addAWTEventListener(activity, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        ticker = new Timer(TICK_MILLIS, event -> tick());
        ticker.start();
    }

    @Override public void stop() {
        if (ticker != null) ticker.stop();
        if (activity != null) Toolkit.getDefaultToolkit().removeAWTEventListener(activity);
        if (lock != null) lock.lock();
    }

    /** Open Vault…: create when there is no vault, unlock when locked; unlocked does nothing until 6b opens the manager. */
    void open(WindowHandle window) {
        switch (lock.state()) {
            case NO_VAULT -> showCreate(window);
            case LOCKED -> service.requestUnlock(window);
            case UNLOCKED -> { }
        }
    }

    /** Locks when the inactivity timeout has passed; the Swing ticker calls this every few seconds. */
    void tick() { if (lock.state() == LockState.UNLOCKED && timer.expired()) lock.lock(); }

    private void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) timer.touch();
        service.lockStateChanged(state);
        if (lockAction != null) lockAction.setEnabled(state == LockState.UNLOCKED);
        context.events().publish(VaultApi.LOCK_STATE_CHANGED, state);
    }

    void showCreate(WindowHandle owner) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Create Vault", owner, true));
        PasswordPanel panel = PasswordPanel.create(settings.bindByDefault(), (password, bind) ->
            lock.create(password, bind).whenComplete((ignored, failure) -> {
                if (failure == null) { dialog.close(); return; }
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                context.notices().error("Could not create the vault: " + cause.getMessage());
            }), dialog::close);
        dialog.setContent(panel);
        dialog.show();
    }

    private void showUnlock(UnlockPrompt prompt) {
        currentUnlock = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Unlock Vault", owner(prompt.owner()), true));
        dialog.setContent(PasswordPanel.unlock(prompt));
        prompt.onDismiss(() -> { currentUnlock = null; dialog.close(); });
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    private void showGrant(GrantPrompt prompt) {
        currentGrant = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Allow " + prompt.consumerName() + " to use " + prompt.descriptor().name() + "?", owner(prompt.owner()), true));
        dialog.setContent(new GrantPanel(prompt));
        prompt.onDismiss(() -> { currentGrant = null; dialog.close(); });
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    private void showPick(PickPrompt prompt) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Choose a credential", owner(prompt.owner()), true));
        dialog.setContent(new PickerPanel(prompt));
        prompt.onDismiss(dialog::close);
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    /** A dialog needs an owner; a prompt raised without one (a consumer with no window yet) uses any terminal window. */
    private WindowHandle owner(WindowHandle named) {
        if (named != null && named.isOpen()) return named;
        return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst())
            .orElseThrow(() -> new IllegalStateException("No window to show the vault dialog in"));
    }

    LockManager lockManager() { return lock; }
    VaultService service() { return service; }
    InactivityTimer timer() { return timer; }
    UnlockPrompt currentUnlock() { return currentUnlock; }
    GrantPrompt currentGrant() { return currentGrant; }
}
```

`VaultService.requestUnlock(null)` in the second test passes a null owner; `owner(null)` then picks the terminal window. `service.lockStateChanged` runs before the topic publish, so a consumer reading `credentials()` inside its `LOCK_STATE_CHANGED` handler sees the new state.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS. Known adjustment points: the exact `host.actions()` line format; `host.windows()` shows `dialog|<title>|true`. `FakePluginHost.close()` stops plugins, so `stop()` runs headless with the Swing `Timer` and the AWT listener — both work under `java.awt.headless`.

- [ ] **Step 6: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): the plugin wiring with create, unlock, grant and picker dialogs, actions and auto-lock"
```

---

### Task 9: Bundle the plugin, document it, record status

**Files:**
- Modify: `jasper-app/build.gradle.kts` (the `stagePlugins` task), `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java:31`, `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java:42-45`
- Modify: `README.md:125,155`, `docs/app-maintenance.md` (after the "History and Snippets live in plugins" section), `docs/plugin-authoring.md` (the worked-example paragraph near line 317), `docs/STATUS.md`, `AGENTS.md` (architecture rules bullet on bundled plugins)

- [ ] **Step 1: Stage the vault with the other bundled plugins**

In `jasper-app/build.gradle.kts`, extend `stagePlugins`:

```kotlin
val stagePlugins = tasks.register<Sync>("stagePlugins") {
    from(project(":jasper-plugin-sample").tasks.named("jar")) { into("dev.jasper.sample") }
        from(project(":jasper-plugin-snippets").tasks.named("jar")) { into("dev.jasper.snippets") }
        from(project(":jasper-plugin-snippets").configurations.named("runtimeClasspath")) { into("dev.jasper.snippets") }
    from(project(":jasper-plugin-history").tasks.named("jar")) { into("dev.jasper.history") }
    from(project(":jasper-plugin-vault").tasks.named("jar")) { into("dev.jasper.vault") }
    from(project(":jasper-plugin-vault").configurations.named("runtimeClasspath")) { into("dev.jasper.vault") }
    into(layout.buildDirectory.dir("plugins"))
}
```

- [ ] **Step 2: Extend the app's bundled-plugin tests**

`PluginZipsTest.java` line 31:

```java
        assertThat(ids).containsExactlyInAnyOrder("dev.jasper.sample", "dev.jasper.snippets", "dev.jasper.history", "dev.jasper.vault");
```

If the test's loop has a branch for `dev.jasper.snippets` checking that a bundled jar is present beside the plugin jar (line 27), add the same expectation for `dev.jasper.vault` (a `bcprov-jdk18on-*.jar` beside `dev.jasper.vault.jar`).

`BundledSamplePluginTest.java` lines 42–45:

```java
        assertThat(runtime.get().statusLines()).as("the sample and the three bundled feature plugins").hasSize(4)
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.sample", "0.1.0", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.history", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.snippets", "BUNDLED", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.vault", "BUNDLED", "ACTIVE"));
```

- [ ] **Step 3: Run the app tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' -q`
Expected: PASS. If `BundledSamplePluginTest` reports the vault plugin failing to start, read its status line: the likely causes are the `Toolkit` listener (must work headless — it does) or the descriptor (`capabilities = []` must parse; if `DescriptorParser` requires the key to be absent rather than empty, drop the line).

- [ ] **Step 4: Documentation**

`README.md` line 125 — replace "Shell History, Snippets and a sample plugin are bundled" with "Shell History, Snippets, Credential Vault and a sample plugin are bundled"; line 155 — "the bundled History, Snippets, Vault and sample plugins".

`docs/app-maintenance.md`, after the History and Snippets section, add:

```markdown
## Credential Vault lives in a plugin

Accounts, SSH keys and secure notes are the bundled `dev.jasper.vault` plugin (`plugins/vault`), a
pure core under a thin SDK wiring: `crypto` (Argon2id, the authenticated file format, AES-GCM),
`model` (the vault and its binary codec; secrets are arrays, zeroed at lock), `store` (atomic file
writes; the device secret in the macOS/Linux/Windows keychain tool or a 0600 file), `lock` (the
single owner of the open vault and derived key; the inactivity clock), `keygen` (OpenSSH keys via
BouncyCastle), `service` (the request queue behind `VaultApi`: one prompt per unlock, per
(plugin, credential) grant and per pick; the last cancelled waiter dismisses it) and `ui` (the dialog
panels). Other plugins compile `compileOnly` against `dev.jasper.vault.api` and declare
`requires = [{ id = "dev.jasper.vault", version = ">=0.1" }]`. The file is
`plugins/dev.jasper.vault/data/vault.jv`; the settings file is `dev.jasper.vault.toml` beside it.
Run its tests with `./gradlew :jasper-plugin-vault:test`; `-Djasper.vault.keychainTest=true` also
touches the real keychain under a throwaway item.
```

`docs/plugin-authoring.md`, after the History/Snippets worked-example paragraph:

```markdown
Credential Vault (`plugins/vault`) is the worked example for `publishPerConsumer`: every plugin that
requires it gets its own `VaultApi` whose `credential(id)` asks the user to allow *that* plugin once or
always, and whose futures can be cancelled to withdraw the request.
```

`AGENTS.md` architecture bullet: after "History and Snippets are bundled plugins under `plugins/`; the app has no shell-history or snippet code." add "Credential Vault is a bundled plugin too (`plugins/vault`, bundling BouncyCastle); other plugins reach it through `dev.jasper.vault.api` only."

`docs/STATUS.md`: in the opening paragraph replace "is approved and awaits its plans 6a (core and API) and 6b (UI)" with "has plan 6a (core and API) implemented on `claude/vault-6a`; plan 6b (UI) is next", and add a section after the plugin-home one:

```markdown
### Credential Vault plan 6a — 2026-09-22

Implemented on `claude/vault-6a` (not merged, not pushed): the bundled `dev.jasper.vault` plugin's
core and API from the [vault design](superpowers/specs/2026-09-22-jasper-vault-design.md) per the
[6a plan](superpowers/plans/2026-09-22-jasper-vault-plan-6a-core-and-api.md). A device-bound
Argon2id/AES-256-GCM file with a binary plaintext that never becomes a `String`; the device secret in
the platform keychain tool with a 0600-file fallback; a lock manager with inactivity auto-lock; an
OpenSSH key generator; `VaultApi` published per consumer with once/always/deny grants stored in the
vault, one prompt per request kind, and cancellation that withdraws a request. UI so far: the create,
unlock, grant and picker dialogs, `Open Vault...` (F8) and `Lock Vault`. Plan 6b adds the manager
window, status item, rail action, key generator dialog and the Vault palette scope.
```

- [ ] **Step 5: Full check and commit**

Run: `./gradlew check -q`
Expected: PASS, including `verifyPluginArchitecture` (the vault imports only the JDK, the SDK, its own packages and bundled BouncyCastle) and the documentation link test.

```bash
git add jasper-app README.md AGENTS.md docs
git commit -m "feat(vault): bundle the Credential Vault plugin and document it"
```

---

## Self-review

- **Spec coverage.** Section 3 shape: Tasks 1, 8, 9. Section 4 API and rules (threading, per consumer, grants, unlocking, cancellation, lifetime, events): Task 6, with the executors and topic publication in Task 8. Section 5 file, KDF, plaintext, model, keys on disk, change password: Tasks 2, 3, 5, 7. Section 6 device secret: Task 4. Section 7 lifecycle: Tasks 5 and 8. Section 8 settings: Task 8 (`VaultSettings`, `settings.toml`). Section 9 items that belong to 6a per section 11: the actions and the four dialogs (Task 8); the status item, rail, manager, key generator dialog and palette scope are 6b. Section 10 tests: each task carries its own; the keychain test is opt-in.
- **Placeholders.** None: every class in the file lists is written out. `open` while unlocked is an intentional no-op documented for 6b.
- **Type consistency.** `LockState` lives in `api` and is created in Task 5, before `VaultService` (Task 6) and the topic use it. `LockManager.save()` is the `CompletableFuture<Void>` used by `VaultService.answered` and `VaultPlugin`. `KeychainStore.Tool` in tests is a `Function<Command, Output>`, the same type `KeychainStore` takes. `DeviceSecrets(KeychainStore, FileStore)` is what `LockManagerTest`, `VaultServiceTest` and `VaultPluginTest` build. `PickerPanel.label` and the `list`, `primary`, `cancel` fields are package-private for `PanelsTest`. `GrantPrompt.settled()` and `PickPrompt.settled()` exist for the service tests.
- **Deviation from the spec recorded in the spec itself:** the salt lasts one unlock session (fresh nonce per write; fresh salt at create and change password), because a fresh salt needs the password again.
