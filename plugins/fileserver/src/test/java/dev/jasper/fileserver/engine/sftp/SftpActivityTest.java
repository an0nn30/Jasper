package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpActivityTest {
    @TempDir Path temp;

    @Test void onlyHarmlessExtensionsRun() throws Exception {
        var activity = new SftpActivity(new ShareRoot(temp, true), new ServerEvents(new TestClock(), event -> { }));
        assertThat(SftpActivity.EXTENSIONS).containsExactlyInAnyOrder("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");
        for (String allowed : SftpActivity.EXTENSIONS) assertThatCode(() -> activity.receivedExtension(null, allowed, 1)).doesNotThrowAnyException();
        for (String refused : new String[]{"copy-file", "copy-data", "md5-hash", "check-file-name", "space-available", "hardlink@openssh.com", "lsetstat@openssh.com"})
            assertThatThrownBy(() -> activity.receivedExtension(null, refused, 1)).as(refused).isInstanceOf(UnsupportedOperationException.class);
    }
}
