package dev.jasper.app.platform;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MacDockTest {
    @Test
    @EnabledIfSystemProperty(named = "jasper.testNativeLibrary", matches = ".+")
    @SuppressWarnings("restricted")
    void theRealBridgeLinksAndLeavesAnUnbundledJvmAlone() throws Exception {
        System.load(System.getProperty("jasper.testNativeLibrary"));
        Method setRegular = MacDock.class.getDeclaredMethod("setRegular", boolean.class);
        Method watch = MacDock.class.getDeclaredMethod("watchPowerOff");
        Method poweringOff = MacDock.class.getDeclaredMethod("poweringOffNow");
        for (Method method : new Method[] {setRegular, watch, poweringOff}) method.setAccessible(true);
        // Real JNI linkage; a JVM outside Jasper's bundle returns before touching NSApp or NSWorkspace.
        setRegular.invoke(null, false);
        setRegular.invoke(null, true);
        watch.invoke(null);
        assertThat(poweringOff.invoke(null)).isEqualTo(false);
    }

    @Test void onlyAPackagedMacAppWithItsBridgeCanSwitch(@TempDir Path directory) throws Exception {
        String app = directory.resolve("Jasper.app/Contents/MacOS/Jasper").toString();
        Path library = MacNative.libraryPath(app);
        assertThat(MacDock.available("Mac OS X", app)).as("a packaged image missing the bridge").isFalse();
        Files.createDirectories(library.getParent());
        Files.writeString(library, "placeholder");
        assertThat(MacDock.available("Mac OS X", app)).isTrue();
        assertThat(MacDock.available("Mac OS X", null)).as("a development run").isFalse();
        assertThat(MacDock.available("Mac OS X", "/usr/bin/java")).isFalse();
        assertThat(MacDock.available("Windows 11", app)).isFalse();
        assertThat(MacDock.available(null, app)).isFalse();
        assertThat(MacDock.available()).as("a test JVM is never a packaged app").isFalse();
    }

    @Test void outsideAPackagedAppNothingLoadsAndNothingThrows() {
        MacDock.prepare();
        MacDock.show(false);
        MacDock.show(true);
        assertThat(MacDock.poweringOff()).isFalse();
    }
}
