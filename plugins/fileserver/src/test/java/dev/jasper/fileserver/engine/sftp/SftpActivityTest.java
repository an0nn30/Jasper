package dev.jasper.fileserver.engine.sftp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpActivityTest {
    @Test void onlyHarmlessExtensionsRun() {
        var activity = new SftpActivity();
        assertThat(SftpActivity.EXTENSIONS).containsExactlyInAnyOrder("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");
        for (String allowed : SftpActivity.EXTENSIONS) assertThatCode(() -> activity.receivedExtension(null, allowed, 1)).doesNotThrowAnyException();
        for (String refused : new String[]{"copy-file", "copy-data", "md5-hash", "check-file-name", "space-available", "hardlink@openssh.com", "lsetstat@openssh.com"})
            assertThatThrownBy(() -> activity.receivedExtension(null, refused, 1)).as(refused).isInstanceOf(UnsupportedOperationException.class);
    }
}
