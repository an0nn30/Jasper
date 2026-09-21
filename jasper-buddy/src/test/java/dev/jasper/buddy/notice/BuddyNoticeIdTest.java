package dev.jasper.buddy.notice;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BuddyNoticeIdTest {
@Test void sourceQualifiesNoticeIdentity() {
    Object key = new Object();
    assertThat(new BuddyNoticeId("terminal", key)).isEqualTo(new BuddyNoticeId("terminal", key));
    assertThat(new BuddyNoticeId("transfer", key)).isNotEqualTo(new BuddyNoticeId("terminal", key));
    assertThatThrownBy(() -> new BuddyNoticeId(" ", key)).isInstanceOf(IllegalArgumentException.class);
}
}
