package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

class MacNotificationsTest {
    @AfterEach void forgetTheSink() { MacNotifications.onClick(id -> { }); }

    @Test
    @EnabledIfSystemProperty(named = "jasper.testNativeLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void realNativeBridgeBindsItsClickCallbackAndDeclinesAnUnbundledJvm() throws Exception {
        System.load(System.getProperty("jasper.testNativeLibrary"));
        // The shared library binds the click callback on request, not when it loads: this fails if clicked(String)
        // cannot be resolved, say because it was renamed.
        assertThat(invoke("bind")).as("the bridge resolved MacNotifications.clicked(String)").isEqualTo(true);
        assertThat(invoke("bind")).as("binding again keeps what is bound").isEqualTo(true);
        // Real JNI linkage without a permission prompt or a notification.
        String id = "0f8fad5b-d9cb-469f-a165-70867728950e";
        invoke("post", id, "build", "Finished");
        invoke("remove", id);
        invoke("removeAll");
    }

    @Test void aClickReachesTheSinkAndASinkThatThrowsStaysInJava() {
        var heard = new ArrayList<String>();
        MacNotifications.onClick(heard::add);
        MacNotifications.clicked("first");
        MacNotifications.onClick(id -> { throw new IllegalStateException("broken handler"); });
        MacNotifications.clicked("second");
        assertThat(heard).containsExactly("first");
    }

    @Test void outsideAPackagedAppNothingLoadsAndNothingThrows() {
        MacNotifications.send("0f8fad5b-d9cb-469f-a165-70867728950e", "build", "Finished");
        MacNotifications.withdraw("0f8fad5b-d9cb-469f-a165-70867728950e");
        MacNotifications.withdrawAll();
    }

    @Test void nativeLibraryBelongsToTheRunningBundleIncludingRenamedApps() {
        assertThat(MacNative.libraryPath("/Applications/Jasper Preview.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper Preview.app/Contents/app/libjasper-macos.dylib")
                .toAbsolutePath());
    }

    @Test void unpackagedLaunchesDoNotUseAnotherApplicationsIdentity() {
        assertThat(MacNative.libraryPath(null)).isNull();
        assertThat(MacNative.libraryPath(" ")).isNull();
        assertThat(MacNative.libraryPath("/usr/bin/java")).isNull();
        assertThat(MacNative.libraryPath("/tmp/Contents/MacOS/Jasper")).isNull();
        assertThat(MacNative.libraryPath("/Contents/MacOS/Jasper")).isNull();
    }

    private static Object invoke(String name, String... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        Arrays.fill(types, String.class);
        var method = MacNotifications.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, (Object[]) arguments);
    }
}
