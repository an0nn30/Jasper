package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Stores;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec section 12's browsing groups: one data store for the persistent items, and one of its own for each group of
 * /dev/null items, which ends with the last of them. Every store's {@code Stores} is in memory here, and its in-memory
 * context a recording stand-in, as no context can be made without Chromium.
 */
class DataStoresTest {
    private static final String HOST = "self-signed.badssl.com";
    private static final String SHA256 = "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61";
    @TempDir Path data;
    private final MemoryStores memory = new MemoryStores();
    // How many in-memory contexts the stores' pages had released, as Chromium would be asked to.
    private final AtomicInteger released = new AtomicInteger();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @Test void twoDevNullStoresAreTwoStoresEachWithItsOwnContextAndStores() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore one = stores.newDevNull(), two = stores.newDevNull();
            assertThat(one).isNotSameAs(two);
            assertThat(one.devNull()).isTrue();
            assertThat(two.devNull()).isTrue();
            assertThat(one.context()).isNotSameAs(two.context());
            assertThat(one.context().inMemory()).isTrue();
            assertThat(one.stores()).isNotSameAs(two.stores()).isNotSameAs(plugins);
            assertThat(one.allowance()).isNotSameAs(two.allowance());
            assertThat(stores.live()).as("the persistent store is not open yet").containsExactly(one, two);
            DataStore persistent = stores.persistent();
            assertThat(persistent.devNull()).isFalse();
            assertThat(persistent.context().inMemory()).isFalse();
            assertThat(persistent.stores()).isSameAs(plugins);
            assertThat(stores.persistent()).isSameAs(persistent);
            assertThat(one.permissions()).isNotSameAs(two.permissions()).isNotSameAs(persistent.permissions());
            stores.close();
            return null;
        });
    }

    /**
     * Plan 6: a store is ready once its site permissions have loaded, which only asking it starts; one whose permissions
     * cannot load, as a store closed before its first item showed, is ready all the same (F15). Each store keeps its own
     * permissions: a /dev/null group's are not the persistent store's.
     */
    @Test void aStoreIsReadyOnceItsPermissionsHaveLoadedOrCannotLoad() throws Exception {
        Stores plugins = memory.make();
        done(new dev.jasper.browser.permissions.PermissionStore(plugins.database(), Runnable::run, failure -> { })
            .set("https://a.test", Permission.CAMERA, Decision.ALLOWED));
        var stores = new AtomicReference<DataStores>();
        DataStore persistent = onUi(() -> {
            stores.set(stores(plugins));
            return stores.get().persistent();
        });
        assertThat(persistent.permissions().decision("https://a.test", Permission.CAMERA)).as("not loaded before it is asked").isEmpty();
        Runnable release = memory.hold(plugins);
        CompletableFuture<Void> ready = onUi(persistent::ready);
        assertThat(ready).as("its permissions are still loading").isNotDone();
        release.run();
        done(ready);
        assertThat(persistent.permissions().decision("https://a.test", Permission.CAMERA)).contains(Decision.ALLOWED);

        DataStore devNull = onUi(() -> stores.get().newDevNull());
        done(onUi(devNull::ready));
        done(devNull.permissions().set("https://private.test", Permission.MICROPHONE, Decision.ALLOWED));
        assertThat(persistent.permissions().all()).extracting(dev.jasper.browser.permissions.Grant::origin).containsExactly("https://a.test");

        DataStore closed = onUi(() -> {
            DataStore early = stores.get().newDevNull();
            early.retain();
            early.release();
            return early;
        });
        assertThat(closed.closed()).isTrue();
        try (var log = new dev.jasper.browser.store.CapturedLog(dev.jasper.browser.permissions.PermissionStore.class.getName())) {
            assertThat(onUi(closed::ready)).as("a store whose permissions cannot load").succeedsWithin(10, TimeUnit.SECONDS);
            assertThat(log.records()).as("no warning for a closed store").noneMatch(record -> record.getLevel() == java.util.logging.Level.WARNING);
        }
        onUi(() -> {
            stores.get().close();
            return null;
        });
    }

    /**
     * Review item 3: a /dev/null group's permissions end with its store. A query of its permissions page that found the
     * store just before it closed, answered after, lists none, and the closed store answers no decision.
     */
    @Test void aQueryThatRacedADevNullStoresCloseFindsNoPermission() throws Exception {
        Stores plugins = memory.make();
        var stores = new AtomicReference<DataStores>();
        DataStore devNull = onUi(() -> {
            stores.set(stores(plugins));
            DataStore made = stores.get().newDevNull();
            made.retain();
            return made;
        });
        done(onUi(devNull::ready));
        done(devNull.permissions().set("https://private.test", Permission.CAMERA, Decision.ALLOWED));
        dev.jasper.browser.permissions.PermissionStore found = devNull.permissions();
        onUi(() -> {
            devNull.release();
            return null;
        });
        assertThat(devNull.closed()).isTrue();
        String listed = done(dev.jasper.browser.pages.PermissionsPage.answer(found, sites -> { }, "{\"op\":\"list\"}"));
        assertThat(listed).isEqualTo("{\"grants\":[]}");
        assertThat(found.decision("https://private.test", Permission.CAMERA)).isEmpty();
        onUi(() -> {
            stores.get().close();
            return null;
        });
    }

    /** Plan 6: a permission a store could not save is told to whoever listens, once for each write, on the UI thread. */
    @Test void aPermissionNoStoreCouldSaveIsToldOnTheUiThread() throws Exception {
        Stores plugins = memory.make();
        done(plugins.database().call(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TRIGGER refused BEFORE INSERT ON permissions BEGIN SELECT RAISE(ABORT, 'refused'); END");
            }
            return null;
        }));
        var told = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var stores = new AtomicReference<DataStores>();
        DataStore persistent = onUi(() -> {
            stores.set(stores(plugins));
            stores.get().onPermissionsUnsaved(cause -> told.add(javax.swing.SwingUtilities.isEventDispatchThread() + " " + (cause instanceof java.sql.SQLException)));
            return stores.get().persistent();
        });
        try (var log = new dev.jasper.browser.store.CapturedLog(dev.jasper.browser.permissions.PermissionStore.class.getName())) {
            assertThat(persistent.permissions().set("https://a.test", Permission.CAMERA, Decision.ALLOWED)).failsWithin(10, TimeUnit.SECONDS);
            onUi(() -> null);
            assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getMessage()).isEqualTo("Site permissions could not be saved"));
        }
        assertThat(told).as("on the UI thread, with SQLite's reason").containsExactly("true true");
        onUi(() -> {
            stores.get().close();
            return null;
        });
    }

    @Test void aDevNullStoreReleasedToZeroClosesItsContextOnceAndItsStores() throws Exception {
        Stores plugins = memory.make();
        var closings = new AtomicInteger();
        DataStore store = onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore devNull = stores.newDevNull();
            devNull.onClosed(closings::incrementAndGet);
            devNull.retain();
            devNull.retain();
            Chromium chromium = chromium();
            PageTestAccess.askContext(devNull.context(), chromium);
            PageTestAccess.askContext(devNull.context(), chromium);
            devNull.release();
            assertThat(devNull.closed()).as("one item still holds it").isFalse();
            assertThat(released).hasValue(0);
            devNull.release();
            assertThat(devNull.closed()).isTrue();
            assertThat(released).as("its context, released once").hasValue(1);
            assertThat(closings).hasValue(1);
            assertThat(stores.live()).doesNotContain(devNull);
            var late = new AtomicInteger();
            devNull.onClosed(late::incrementAndGet);
            assertThat(late).as("told at once once closed").hasValue(1);
            devNull.release();
            assertThat(released).as("a release after the close does nothing").hasValue(1);
            assertThat(closings).hasValue(1);
            assertThatThrownBy(devNull::retain).as("a closed store takes no item").isInstanceOf(IllegalStateException.class);
            return devNull;
        });
        store.stores().closed().get(10, TimeUnit.SECONDS);
        assertThat(store.stores().history().visits(1)).as("its database takes no more work").isCompletedExceptionally();
    }

    @Test void thePersistentStoreSurvivesReleaseToZero() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore persistent = stores.persistent();
            assertThatThrownBy(persistent::release).as("an open store that holds no item").isInstanceOf(IllegalStateException.class);
            persistent.retain();
            persistent.release();
            assertThat(persistent.closed()).isFalse();
            assertThat(plugins.closed()).isNotDone();
            persistent.retain();
            assertThat(persistent.closed()).isFalse();
            stores.close();
            return null;
        });
    }

    @Test void closeClosesEveryStore() throws Exception {
        Stores plugins = memory.make();
        var stores = new AtomicReference<DataStores>();
        DataStore[] made = onUi(() -> {
            stores.set(stores(plugins));
            DataStore persistent = stores.get().persistent(), held = stores.get().newDevNull(), unheld = stores.get().newDevNull();
            persistent.retain();
            held.retain();
            Chromium chromium = chromium();
            PageTestAccess.askContext(held.context(), chromium);
            PageTestAccess.askContext(unheld.context(), chromium);
            stores.get().close();
            assertThat(persistent.closed()).isTrue();
            assertThat(held.closed()).isTrue();
            assertThat(unheld.closed()).isTrue();
            assertThat(released).as("each context asked for, once").hasValue(2);
            assertThat(stores.get().live()).isEmpty();
            stores.get().close();
            assertThat(released).as("a second close does nothing").hasValue(2);
            assertThatThrownBy(() -> stores.get().newDevNull()).isInstanceOf(IllegalStateException.class);
            return new DataStore[] {persistent, held, unheld};
        });
        for (DataStore store : made) store.stores().closed().get(10, TimeUnit.SECONDS);
    }

    @Test void closeClosesThePluginsStoresThoughNoItemAskedForThem() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            stores(plugins).close();
            return null;
        });
        plugins.closed().get(10, TimeUnit.SECONDS);
        assertThat(MemoryStores.started(plugins)).as("never opened").isFalse();
    }

    /** Nothing touches a native object after Chromium has stopped: a store that closes then releases no context. */
    @Test void aStoreThatClosesAfterChromiumStoppedCallsNoRelease() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore devNull = stores.newDevNull();
            devNull.retain();
            Chromium chromium = chromium();
            PageTestAccess.askContext(devNull.context(), chromium);
            chromium.close();
            devNull.release();
            assertThat(devNull.closed()).isTrue();
            assertThat(released).hasValue(0);
            stores.close();
            assertThat(released).hasValue(0);
            return null;
        });
    }

    /** Plan 5's routing (F13): the store of a browser is known from Chromium's thread on, and forgotten as it closes. */
    @Test void aBrowsersStoreIsFoundFromAnyThreadUntilTheBrowserOrItsStoreCloses() throws Exception {
        Stores plugins = memory.make();
        var stores = new AtomicReference<DataStores>();
        var devNull = new AtomicReference<DataStore>();
        var persistent = new AtomicReference<DataStore>();
        onUi(() -> {
            stores.set(stores(plugins));
            devNull.set(stores.get().newDevNull());
            devNull.get().retain();
            persistent.set(stores.get().persistent());
            return null;
        });
        CompletableFuture.runAsync(() -> {
            stores.get().browserMade(7, devNull.get());
            stores.get().browserMade(8, persistent.get());
        }).get(10, TimeUnit.SECONDS);
        assertThat(stores.get().forBrowser(7)).containsSame(devNull.get());
        assertThat(stores.get().forBrowser(8)).containsSame(persistent.get());
        assertThat(stores.get().forBrowser(9)).as("a browser no page named").isEmpty();
        stores.get().browserClosed(8, devNull.get());
        assertThat(stores.get().forBrowser(8)).as("another store's close of the number").containsSame(persistent.get());
        stores.get().browserClosed(8, persistent.get());
        assertThat(stores.get().forBrowser(8)).isEmpty();
        onUi(() -> {
            devNull.get().release();
            return null;
        });
        assertThat(stores.get().forBrowser(7)).as("its store closed").isEmpty();
        onUi(() -> {
            stores.get().browserMade(7, devNull.get());
            return null;
        });
        assertThat(stores.get().forBrowser(7)).as("a closed store is no browser's").isEmpty();
        onUi(() -> {
            stores.get().close();
            return null;
        });
    }

    /**
     * The persistent store is live once its database has opened, not because a restore or an item asked for the store:
     * listing the live stores opens nothing.
     */
    @Test void thePersistentStoreIsLiveOnlyOnceItsDatabaseHasOpened() throws Exception {
        Stores plugins = memory.make();
        var stores = new AtomicReference<DataStores>();
        var persistent = new AtomicReference<DataStore>();
        onUi(() -> {
            stores.set(stores(plugins));
            persistent.set(stores.get().persistent());
            persistent.get().retain();
            assertThat(stores.get().live()).as("asked for, as the restorer asks, not opened").isEmpty();
            return null;
        });
        assertThat(MemoryStores.started(plugins)).as("listing opened nothing").isFalse();
        plugins.history().visits(1).get(10, TimeUnit.SECONDS);
        onUi(() -> {
            assertThat(stores.get().live()).containsExactly(persistent.get());
            stores.get().close();
            assertThat(stores.get().live()).isEmpty();
            return null;
        });
    }

    /** No browser is found in any store once the stores have closed, at the plugin's stop, or named to them after. */
    @Test void theStoresForgetEveryBrowserAsTheyCloseAndHearNoneAfter() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore persistent = stores.persistent(), devNull = stores.newDevNull();
            devNull.retain();
            stores.browserMade(3, persistent);
            stores.browserMade(4, devNull);
            assertThat(stores.browsersKnown()).isEqualTo(2);
            stores.close();
            assertThat(stores.browsersKnown()).as("forgotten at the close, whether Chromium closes them after or not").isZero();
            stores.browserMade(5, persistent);
            assertThat(stores.browsersKnown()).as("one Chromium names after the close").isZero();
            assertThat(stores.forBrowser(3)).isEmpty();
            assertThat(stores.forBrowser(5)).isEmpty();
            return null;
        });
    }

    /** Spec section 14: a /dev/null item's allowance is its store's alone, and ends with its store. */
    @Test void anAllowanceInADevNullStoreIsAbsentFromThePersistentOneAndGoesWithItsStore() throws Exception {
        Stores plugins = memory.make();
        onUi(() -> {
            DataStores stores = stores(plugins);
            DataStore devNull = stores.newDevNull(), persistent = stores.persistent();
            devNull.retain();
            devNull.allowance().allow(HOST, SHA256);
            assertThat(devNull.allowance().allows(HOST, SHA256)).isTrue();
            assertThat(persistent.allowance().allows(HOST, SHA256)).isFalse();
            assertThat(persistent.allowance().allowsAny(HOST)).isFalse();
            devNull.release();
            assertThat(devNull.allowance().allows(HOST, SHA256)).as("gone with its store").isFalse();
            assertThat(devNull.allowance().allowsAny(HOST)).isFalse();
            stores.close();
            return null;
        });
    }

    /** The stores over {@code plugins}, whose /dev/null stores are in memory with recording contexts. UI thread. */
    private DataStores stores(Stores plugins) {
        return new DataStores(plugins, memory::make, () -> PageTestAccess.recordingContext(released), () -> false);
    }

    /** A Chromium that never starts: nothing here asks for its client. */
    private Chromium chromium() {
        return new Chromium(data, () -> { }, task -> { throw new AssertionError("Chromium was started"); }, new AboutScheme(),
            new PreviewScheme(Runnable::run), cause -> { });
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }
}
