package dev.jasper.fileserver.engine.http;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPathsTest {
    @Test void decodesEncodesAndEscapes() {
        assertThat(HttpPaths.decode("/a%20b/%C3%A9+c")).isEqualTo("/a b/é+c");
        assertThatThrownBy(() -> HttpPaths.decode("/bad%zz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpPaths.decode("/cut%4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpPaths.decode("/%C3")).as("not UTF-8").isInstanceOf(IllegalArgumentException.class);
        assertThat(HttpPaths.segment("x & <y>.txt")).isEqualTo("x%20%26%20%3Cy%3E.txt");
        assertThat(HttpPaths.segment("é")).isEqualTo("%C3%A9");
        assertThat(HttpPaths.href(List.of("docs", "a b"), true)).isEqualTo("/docs/a%20b/");
        assertThat(HttpPaths.href(List.of(), true)).isEqualTo("/");
        assertThat(HttpPaths.html("<a href=\"x\">'&'</a>")).isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
        assertThat(HttpPaths.contentDisposition("résumé \"v2\".pdf"))
            .isEqualTo("attachment; filename=\"r_sum_ _v2_.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9%20%22v2%22.pdf");
    }
}
