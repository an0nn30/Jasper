package dev.jasper.app.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class VaultServiceTest {
    @TempDir Path temp;
    final TestClock clock = new TestClock();
    final MemoryStore store = new MemoryStore();
    VaultService service() throws IOException {
        return new VaultService(temp.resolve("vault.bin"), store, clock, () -> clock.nanos);
    }
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-13T12:00:00Z"); long nanos;
        void advance(Duration duration) { now = now.plus(duration); nanos += duration.toNanos(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static final class MemoryStore implements DeviceAccessStore {
        byte[] bytes; int reads; boolean failDelete, failWrite; boolean available = true;
        CountDownLatch readStarted, releaseRead;
        @Override public boolean available() { return available; }
        @Override public byte[] read(String id) throws IOException {
            reads++;
            if (readStarted != null) {
                readStarted.countDown();
                try { if (!releaseRead.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out test store"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            return bytes == null ? null : bytes.clone();
        }
        @Override public void write(String id, byte[] payload) throws IOException {
            if (failWrite) throw new IOException("Synthetic store failure"); bytes = payload.clone();
        }
        @Override public void delete(String id) throws IOException {
            if (failDelete) throw new IOException("Synthetic store failure");
            if (bytes != null) Arrays.fill(bytes, (byte) 0); bytes = null;
        }
    }
    static byte[] syntheticKey(String algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm,
                new org.bouncycastle.jce.provider.BouncyCastleProvider());
        if (algorithm.equals("RSA")) generator.initialize(2048);
        if (algorithm.equals("EC")) generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var out = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(generator.generateKeyPair(), "synthetic test", null, out);
        return out.toByteArray();
    }

    @Test void startsLockedAndRememberedDeadlineNeverSlides() throws Exception {
        try (var v = service()) { v.create("master".toCharArray(), Duration.ofDays(7)); }
        Instant deadline = clock.instant().plus(Duration.ofDays(7));
        try (var v = service()) {
            assertThat(v.snapshot().locked()).isTrue(); assertThat(store.reads).isZero();
            assertThat(v.snapshot().rememberedUntil()).isEqualTo(deadline);
            clock.advance(Duration.ofDays(1)); assertThat(v.unlockRemembered()).isTrue();
            v.userActivity(); v.lock(VaultService.LockReason.AUTO);
            assertThat(v.snapshot().rememberedUntil()).isEqualTo(deadline);
            clock.advance(Duration.ofDays(6)); assertThat(v.unlockRemembered()).isFalse();
            assertThat(v.snapshot().locked()).isTrue();
            v.unlock("master".toCharArray(), null); assertThat(v.snapshot().locked()).isFalse();
        }
    }

    @Test void importedBytesSurviveSourceRemovalDuplicateAndRenameKeepReferences() throws Exception {
        for (String algorithm : List.of("Ed25519", "RSA", "EC")) {
            Path source = temp.resolve("source"); byte[] bytes = syntheticKey(algorithm); Files.write(source, bytes);
            Path vault = temp.resolve(algorithm + ".vault");
            try (var v = new VaultService(vault, store, clock, () -> clock.nanos)) {
                v.create("master".toCharArray(), null);
                var preview = v.inspectKey(bytes, new char[0]); assertThat(preview.fingerprint()).startsWith("SHA256:");
                var imported = v.importKey("synthetic", bytes, new char[0]);
                assertThat(imported.duplicate()).isFalse();
                assertThat(v.importKey("other name", bytes, new char[0]))
                        .isEqualTo(new VaultService.ImportResult(imported.id(), true));
                UUID login = v.saveLogin(null, "login", "alice", null, imported.id());
                Files.delete(source); Arrays.fill(bytes, (byte) 0);
                v.renameKey(imported.id(), "renamed");
                assertThat(v.snapshot().logins().getFirst().keyId()).isEqualTo(imported.id());
                assertThat(v.snapshot().keys().getFirst().loginUses()).isEqualTo(1);
                assertThatThrownBy(() -> v.deleteKey(imported.id())).isInstanceOf(IOException.class);
                v.lock(VaultService.LockReason.AUTO); v.unlock("master".toCharArray(), null);
                try (var material = v.resolveLogin(login)) {
                    assertThat(v.inspectKey(material.privateKey(), material.passphrase())).isEqualTo(preview);
                }
                v.deleteLogin(login); v.deleteKey(imported.id()); assertThat(v.snapshot().keys()).isEmpty();
            }
        }
    }

    @Test void editsAreTransactionalAndNullRetainsPassword() throws Exception {
        try (var v = service()) {
            v.create("master".toCharArray(), null);
            UUID id = v.saveLogin(null, "login", "alice", "password".toCharArray(), null);
            v.saveLogin(id, "renamed", "bob", null, null);
            try (var material = v.resolveLogin(id)) { assertThat(material.password()).containsExactly("password".toCharArray()); }
            byte[] previous = Files.readAllBytes(temp.resolve("vault.bin"));
            assertThatThrownBy(() -> v.saveLogin(id, "bad", "bob", new char[0], null)).isInstanceOf(IOException.class);
            assertThat(Files.readAllBytes(temp.resolve("vault.bin"))).isEqualTo(previous);
            assertThat(v.snapshot().logins().getFirst().name()).isEqualTo("renamed");
            Files.write(temp.resolve("vault.bin"), new byte[]{9});
            assertThatThrownBy(() -> v.saveLogin(id, "lost edit", "bob", null, null)).isInstanceOf(IOException.class);
            assertThat(v.snapshot().logins().getFirst().name()).isEqualTo("renamed");
            assertThat(Files.readAllBytes(temp.resolve("vault.bin"))).containsExactly(9);
        }
    }

    @Test void inactivityIsMonotonicAndExplicitLockRevokesEvenWhenNativeDeleteFails() throws Exception {
        try (var v = service()) {
            v.create("master".toCharArray(), Duration.ofDays(7));
            v.saveLogin(null, "test", "user", new char[]{'p'}, null);
            clock.advance(Duration.ofMinutes(14)); v.userActivity();
            clock.now = clock.now.minus(Duration.ofDays(30));
            clock.nanos += Duration.ofMinutes(14).toNanos(); assertThat(v.checkInactivity()).isFalse(); assertThat(v.snapshot().locked()).isFalse();
            clock.nanos += Duration.ofMinutes(1).toNanos(); assertThat(v.checkInactivity()).isTrue(); assertThat(v.snapshot().locked()).isTrue();
            assertThat(v.checkInactivity()).isFalse();
            assertThat(store.bytes).isNotNull();
            v.unlock("master".toCharArray(), null); store.failDelete = true;
            assertThatThrownBy(() -> v.lock(VaultService.LockReason.EXPLICIT)).isInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isTrue(); assertThat(v.snapshot().logins()).isEmpty();
            assertThat(v.snapshot().deviceWarning()).contains("revoked locally");
        }
        try (var restarted = service()) { assertThat(restarted.unlockRemembered()).isFalse(); }
    }

    @Test void malformedMissingOrMismatchedHintsNeverEnableRememberedUnlock() throws Exception {
        try (var v = service()) { v.create("master".toCharArray(), Duration.ofDays(7)); }
        Files.write(temp.resolve("vault.bin.device"), new byte[]{1, 2});
        try (var v = service()) { assertThat(v.unlockRemembered()).isFalse(); assertThat(store.reads).isZero(); }
        Files.delete(temp.resolve("vault.bin.device"));
        try (var v = service()) { assertThat(v.unlockRemembered()).isFalse(); assertThat(store.reads).isZero(); }
    }

    @Test void lockInvalidatesBlockedRememberedUnlockWithoutWaitingToSeal() throws Exception {
        try (var v = service()) {
            v.create("master".toCharArray(), Duration.ofDays(7)); v.lock(VaultService.LockReason.AUTO);
            store.readStarted = new CountDownLatch(1); store.releaseRead = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Boolean> pending = executor.submit(v::unlockRemembered);
                assertThat(store.readStarted.await(5, TimeUnit.SECONDS)).isTrue();
                v.lock(VaultService.LockReason.AUTO); assertThat(v.snapshot().locked()).isTrue();
                store.releaseRead.countDown();
                assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
                assertThat(v.snapshot().locked()).isTrue();
            } finally { store.releaseRead.countDown(); }
        }
    }

    @Test void badPasswordAndEnrollmentFailureDoNotDestroyCommittedCredentials() throws Exception {
        try (var v = service()) {
            v.create("master".toCharArray(), null);
            UUID id = v.saveLogin(null, "demo", "user", new char[]{'p'}, null);
            v.lock(VaultService.LockReason.AUTO);
            assertThatThrownBy(() -> v.unlock("incorrect".toCharArray(), null)).isInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isTrue();
            store.failWrite = true;
            assertThatThrownBy(() -> v.unlock("master".toCharArray(), Duration.ofDays(7))).isInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isFalse();
            try (var material = v.resolveLogin(id)) { assertThat(material.password()).containsExactly('p'); }
        }
    }
@Test void encryptedPemRequiresPassphraseAndRejectsPublicOnlyOrMalformedInput() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    var text = new StringWriter();
    try (var writer = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(text)) {
        writer.writeObject(pair, new org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder("AES-256-CBC")
                .setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
                .build("fixture passphrase".toCharArray()));
    }
    byte[] pem = text.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    try (var v = service()) {
        v.create("master".toCharArray(), null);
        assertThatThrownBy(() -> v.inspectKey(pem, "wrong".toCharArray())).isInstanceOf(IOException.class);
        var imported = v.importKey("encrypted synthetic", pem, "fixture passphrase".toCharArray());
        try (var material = v.resolveKey(imported.id(), "alice")) {
            assertThat(v.inspectKey(material.privateKey(), material.passphrase()).fingerprint()).startsWith("SHA256:");
        }
        assertThatThrownBy(() -> v.inspectKey(new byte[VaultData.MAX_KEY + 1], new char[0])).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> v.inspectKey("not a key".getBytes(java.nio.charset.StandardCharsets.US_ASCII), new char[0]))
                .isInstanceOf(IOException.class);
        String publicOnly = org.apache.sshd.common.config.keys.PublicKeyEntry.toString(pair.getPublic());
        assertThatThrownBy(() -> v.inspectKey(publicOnly.getBytes(java.nio.charset.StandardCharsets.US_ASCII), new char[0]))
                .isInstanceOf(IOException.class);
    } finally { Arrays.fill(pem, (byte) 0); }
}

@Test void explicitRevocationCannotBeUndoneByReloadingAnOldHintInThisProcess() throws Exception {
    try (var v = service()) {
        v.create("master".toCharArray(), Duration.ofDays(7));
        Path hint = temp.resolve("vault.bin.device");
        byte[] originalHint = Files.readAllBytes(hint);
        store.failDelete = true;
        assertThatThrownBy(() -> v.lock(VaultService.LockReason.EXPLICIT)).isInstanceOf(IOException.class);
        // Simulate an old sidecar remaining/restored while native deletion has failed.
        Files.write(hint, originalHint);
        int before = store.reads;
        assertThat(v.unlockRemembered()).isFalse();
        assertThat(store.reads).isEqualTo(before);
        assertThat(v.snapshot().locked()).isTrue();
        assertThat(v.snapshot().rememberedUntil()).isNull();
        v.unlock("master".toCharArray(), Duration.ofDays(7));
        v.lock(VaultService.LockReason.AUTO);
        assertThat(v.unlockRemembered()).isTrue();
    }
}

@Test void rememberedUuidAndDeadlineMustMatchAndDisabledAutoLockStaysUnlocked() throws Exception {
    try (var v = service()) {
        v.create("master".toCharArray(), Duration.ofDays(7)); v.lock(VaultService.LockReason.AUTO);
        store.bytes[8] ^= 1; assertThat(v.unlockRemembered()).isFalse();
        v.unlock("master".toCharArray(), Duration.ofDays(7)); v.lock(VaultService.LockReason.AUTO);
        store.bytes[24] ^= 1; assertThat(v.unlockRemembered()).isFalse();
        v.unlock("master".toCharArray(), null); v.saveSettings(new VaultSettings(0, 30));
        clock.advance(Duration.ofDays(20)); v.checkInactivity(); assertThat(v.snapshot().locked()).isFalse();
        v.forgetDeviceAccess(); assertThat(store.bytes).isNull(); assertThat(v.snapshot().locked()).isFalse();
        v.lock(VaultService.LockReason.EXPLICIT); assertThat(v.unlockRemembered()).isFalse();
    }
}

    @Test void unavailableNativeStoreIsVisibleWithoutReadingSecretsAndPasswordUnlockStillWorks() throws Exception {
        store.available = false;
        try (var v = service()) {
            assertThat(v.snapshot().deviceWarning()).contains("unavailable");
            assertThat(store.reads).isZero();
            v.create("master".toCharArray(), null);
            v.lock(VaultService.LockReason.AUTO);
            v.unlock("master".toCharArray(), null);
            assertThat(v.snapshot().locked()).isFalse();
            assertThat(v.snapshot().deviceWarning()).contains("unavailable");
            assertThat(store.reads).isZero();
        }
    }

    @Test void forgettingDeviceAccessPreventsAlreadyPendingRememberedUnlockFromPublishing() throws Exception {
        try (var v = service()) {
            v.create("master".toCharArray(), Duration.ofDays(7)); v.lock(VaultService.LockReason.AUTO);
            store.readStarted = new CountDownLatch(1); store.releaseRead = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Boolean> pending = executor.submit(v::unlockRemembered);
                assertThat(store.readStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> forget = executor.submit(() -> { v.forgetDeviceAccess(); return null; });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                while (v.snapshot().rememberedUntil() != null && System.nanoTime() < deadline) Thread.sleep(1);
                assertThat(v.snapshot().rememberedUntil()).isNull();
                store.releaseRead.countDown();
                assertThat(pending.get(5, TimeUnit.SECONDS)).isFalse();
                forget.get(5, TimeUnit.SECONDS);
                assertThat(v.snapshot().locked()).isTrue();
                assertThat(store.bytes).isNull();
            } finally { store.releaseRead.countDown(); }
        }
    }


    @Test void encryptedOpenSshAndPkcs8ImportsRemainUsableAfterRestart() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        var provider = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        byte[] openSsh;
        var context = new org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext();
        context.setCipherName("AES"); context.setCipherType("256"); context.setCipherMode("CTR");
        context.setPassword("fixture passphrase"); context.setKdfRounds(4);
        try (var out = new ByteArrayOutputStream()) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(pair, "synthetic test", context, out);
            openSsh = out.toByteArray();
        }
        var formats = new LinkedHashMap<String, byte[]>(); formats.put("openssh", openSsh);
        for (boolean encrypted : List.of(false, true)) {
            var text = new StringWriter();
            var encryptor = encrypted ? new org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder(
                    org.bouncycastle.openssl.PKCS8Generator.AES_256_CBC).setProvider(provider)
                    .setPassword("fixture passphrase".toCharArray()).build() : null;
            try (var writer = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(text)) {
                writer.writeObject(new org.bouncycastle.openssl.jcajce.JcaPKCS8Generator(pair.getPrivate(), encryptor));
            }
            formats.put(encrypted ? "pkcs8-encrypted" : "pkcs8-clear", text.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        try {
            for (var entry : formats.entrySet()) {
                Path file = temp.resolve(entry.getKey() + ".vault"); UUID keyId; String fingerprint;
                char[] phrase = entry.getKey().equals("pkcs8-clear") ? new char[0] : "fixture passphrase".toCharArray();
                try (var v = new VaultService(file, store, clock, () -> clock.nanos)) {
                    v.create("master".toCharArray(), null);
                    if (phrase.length != 0) assertThatThrownBy(() -> v.inspectKey(entry.getValue(), new char[0]))
                            .isInstanceOf(IOException.class);
                    fingerprint = v.inspectKey(entry.getValue(), phrase).fingerprint();
                    keyId = v.importKey(entry.getKey(), entry.getValue(), phrase).id();
                }
                Arrays.fill(phrase, (char) 0); Arrays.fill(entry.getValue(), (byte) 0);
                try (var v = new VaultService(file, store, clock, () -> clock.nanos)) {
                    v.unlock("master".toCharArray(), null);
                    try (var material = v.resolveKey(keyId, "alice")) {
                        assertThat(v.inspectKey(material.privateKey(), material.passphrase()).fingerprint()).isEqualTo(fingerprint);
                    }
                }
            }
        } finally { formats.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0)); }
    }

    @Test void rejectsWeakAndMultipleKeysWithoutPersistingPartialImports() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(1024);
        var weak = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(generator.generateKeyPair(), "weak synthetic", null, weak);
        generator.initialize(2048);
        var bundle = new StringWriter();
        try (var writer = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(bundle)) {
            writer.writeObject(generator.generateKeyPair()); writer.writeObject(generator.generateKeyPair());
        }
        try (var v = service()) {
            v.create("master".toCharArray(), null);
            byte[] before = Files.readAllBytes(temp.resolve("vault.bin"));
            assertThatThrownBy(() -> v.importKey("weak", weak.toByteArray(), new char[0])).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> v.importKey("bundle", bundle.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII), new char[0]))
                    .isInstanceOf(IOException.class);
            assertThat(v.snapshot().keys()).isEmpty(); assertThat(Files.readAllBytes(temp.resolve("vault.bin"))).containsExactly(before);
        }
    }


    static final class BlockedPublication extends VaultService.Publication {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean blockVault, blockHint;
        private void block() throws IOException {
            started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Synthetic publication timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        }
        @Override void publish(Path path, byte[] bytes, byte[] expected, boolean create) throws IOException {
            if (blockVault) { blockVault = false; block(); }
            super.publish(path, bytes, expected, create);
        }
        @Override void replaceHint(Path path, byte[] bytes) throws IOException {
            if (blockHint) { blockHint = false; block(); }
            super.replaceHint(path, bytes);
        }
    }

    private static void assertResponsiveLock(VaultService v, ExecutorService executor) throws Exception {
        Future<?> lock = executor.submit(() -> { v.lock(VaultService.LockReason.AUTO); return null; });
        assertThatCode(() -> lock.get(1, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThat(executor.submit(v::snapshot).get(1, TimeUnit.SECONDS).locked()).isTrue();
        executor.submit(v::userActivity).get(1, TimeUnit.SECONDS);
    }

    @Test void publicationCrossingCommitBoundaryCanFinishButCannotRestoreUnlockedState() throws Exception {
        var publication = new BlockedPublication();
        try (var v = new VaultService(temp.resolve("vault.bin"), store, clock, () -> clock.nanos, publication);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            v.create("master".toCharArray(), null); UUID login = v.saveLogin(null, "before", "alice", new char[]{'p'}, null);
            publication.blockVault = true;
            Future<UUID> saving = executor.submit(() -> v.saveLogin(login, "committed before lock", "alice", null, null));
            try {
                assertThat(publication.started.await(5, TimeUnit.SECONDS)).isTrue();
                assertResponsiveLock(v, executor);
            } finally { publication.release.countDown(); }
            assertThatThrownBy(() -> saving.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isTrue(); assertThat(v.snapshot().logins()).isEmpty();
            v.unlock("master".toCharArray(), null);
            assertThat(v.snapshot().logins().getFirst().name()).isEqualTo("committed before lock");
        }
    }

    @Test void lockingDuringCreationPublicationLeavesCreatedFileSealedAndUnlockable() throws Exception {
        var publication = new BlockedPublication(); publication.blockVault = true;
        try (var v = new VaultService(temp.resolve("vault.bin"), store, clock, () -> clock.nanos, publication);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> creating = executor.submit(() -> { v.create("master".toCharArray(), null); return null; });
            try {
                assertThat(publication.started.await(5, TimeUnit.SECONDS)).isTrue();
                assertResponsiveLock(v, executor);
            } finally { publication.release.countDown(); }
            assertThatThrownBy(() -> creating.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
            assertThat(v.snapshot().exists()).isTrue(); assertThat(v.snapshot().locked()).isTrue();
            v.unlock("master".toCharArray(), null); assertThat(v.snapshot().locked()).isFalse();
        }
    }

    @Test void rememberedHintPublicationCannotBlockLockOrRestoreLiveCredentials() throws Exception {
        var publication = new BlockedPublication();
        try (var v = new VaultService(temp.resolve("vault.bin"), store, clock, () -> clock.nanos, publication);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            v.create("master".toCharArray(), null); v.lock(VaultService.LockReason.AUTO); publication.blockHint = true;
            Future<?> unlocking = executor.submit(() -> { v.unlock("master".toCharArray(), Duration.ofDays(7)); return null; });
            try {
                assertThat(publication.started.await(5, TimeUnit.SECONDS)).isTrue();
                assertResponsiveLock(v, executor);
            } finally { publication.release.countDown(); }
            assertThatThrownBy(() -> unlocking.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isTrue(); assertThat(v.snapshot().logins()).isEmpty();
            assertThat(v.unlockRemembered()).isFalse();
        }
    }


    @Test void waitingForgetRetainsItsDenyLatchAfterEarlierEnrollmentCompletes() throws Exception {
        var publication = new BlockedPublication();
        try (var v = new VaultService(temp.resolve("vault.bin"), store, clock, () -> clock.nanos, publication);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            v.create("master".toCharArray(), Duration.ofDays(7));
            Path hint = temp.resolve("vault.bin.device"); byte[] originalHint = Files.readAllBytes(hint);
            v.lock(VaultService.LockReason.AUTO); publication.blockHint = true; store.failDelete = true;
            Future<?> enrolling = executor.submit(() -> { v.unlock("master".toCharArray(), Duration.ofDays(7)); return null; });
            Future<?> forgetting;
            try {
                assertThat(publication.started.await(5, TimeUnit.SECONDS)).isTrue();
                forgetting = executor.submit(() -> { v.forgetDeviceAccess(); return null; });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                while (v.snapshot().rememberedUntil() != null && System.nanoTime() < deadline) Thread.sleep(1);
                assertThat(v.snapshot().rememberedUntil()).isNull();
            } finally { publication.release.countDown(); }
            enrolling.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> forgetting.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IOException.class);
            assertThat(v.snapshot().locked()).isFalse();
            Files.write(hint, originalHint); v.lock(VaultService.LockReason.AUTO);
            assertThat(v.unlockRemembered()).isFalse(); assertThat(v.snapshot().rememberedUntil()).isNull();
        }
    }

    @Test void validShapedOrphanHintCannotUnlockAMissingVault() throws Exception {
        UUID id=UUID.randomUUID();Path hint=temp.resolve("vault.bin.device");
        byte[] bytes=java.nio.ByteBuffer.allocate(33).putLong(0x4D5259484E543031L)
            .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
            .putLong(clock.instant().plus(Duration.ofDays(7)).toEpochMilli()).put((byte)0).array();
        Files.write(hint,bytes);
        try(var service=service()){
            assertThat(service.unlockRemembered()).isFalse();assertThat(service.snapshot().exists()).isFalse();
            assertThat(service.snapshot().locked()).isTrue();assertThat(store.reads).isZero();
            assertThat(Files.readAllBytes(hint)).containsExactly(bytes);
        }
    }
}
