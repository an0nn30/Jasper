package dev.jasper.browser.engine;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pages behind {@code jasper-about:}, looked up as Chromium asks for them, without Chromium. */
class AboutSchemeTest {
    @Test void aRegisteredPageAnswersItsHtmlAndAnUnknownOneAnswers404WithAnEmptyBody() {
        var about = new AboutScheme();
        var asked = new AtomicInteger();
        about.register("welcome", () -> "<h1>Welcome " + asked.incrementAndGet() + "</h1>");
        assertThat(about.answer("jasper-about://welcome/")).isEqualTo(new AboutScheme.Answer(200, "<h1>Welcome 1</h1>"));
        assertThat(about.answer("jasper-about:welcome")).as("each request asks the page again")
            .isEqualTo(new AboutScheme.Answer(200, "<h1>Welcome 2</h1>"));
        assertThat(about.answer("JASPER-ABOUT://Welcome/?from=tab#top")).extracting(AboutScheme.Answer::status).isEqualTo(200);
        assertThat(about.answer("jasper-about://missing/")).isEqualTo(new AboutScheme.Answer(404, ""));
        assertThat(about.answer("https://welcome/")).isEqualTo(new AboutScheme.Answer(404, ""));
        about.register("empty", () -> null);
        assertThat(about.answer("jasper-about://empty/")).as("a page with nothing to show").isEqualTo(new AboutScheme.Answer(404, ""));
    }

    @Test void aPageNameIsLowercaseLettersDigitsAndHyphensAndIsTakenOnce() {
        var about = new AboutScheme();
        about.register("new-tab2", () -> "");
        for (String name : new String[] { "", "Welcome", "2nd", "a/b", "a.b", "-a" })
            assertThatThrownBy(() -> about.register(name, () -> "")).as(name).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.register("new-tab2", () -> "")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:new-tab2");
    }
}
