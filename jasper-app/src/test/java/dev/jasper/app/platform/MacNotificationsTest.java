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
    @EnabledIfSystemProperty(named = "jasper.testNotificationLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void realNativeBridgeLinksAndDeclinesAnUnbundledJvm() throws Exception {
        System.load(System.getProperty("jasper.testNotificationLibrary"));
        // Real JNI linkage, JNI_OnLoad included, without a permission prompt or a notification.
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

    @Test void nativeLibraryBelongsToTheRunningBundleIncludingRenamedApps() {
        assertThat(MacNotifications.libraryPath("/Applications/Jasper Preview.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper Preview.app/Contents/app/libjasper-notifications.dylib")
                .toAbsolutePath());
    }

    @Test void unpackagedLaunchesDoNotUseAnotherApplicationsIdentity() {
        assertThat(MacNotifications.libraryPath(null)).isNull();
        assertThat(MacNotifications.libraryPath(" ")).isNull();
        assertThat(MacNotifications.libraryPath("/usr/bin/java")).isNull();
        assertThat(MacNotifications.libraryPath("/tmp/Contents/MacOS/Jasper")).isNull();
        assertThat(MacNotifications.libraryPath("/Contents/MacOS/Jasper")).isNull();
    }

    private static void invoke(String name, String... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        Arrays.fill(types, String.class);
        var method = MacNotifications.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(null, (Object[]) arguments);
    }
}
