package dev.jasper.browser.item;

import dev.jasper.browser.item.AudioGate.AudioAnswer;
import dev.jasper.sdk.ui.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec section 12's audio bar across the top of an item, as SDK nodes built as the Editor's on-disk banner is: its
 * question, then Allow Once, Allow Always, Deny Once and Deny Always, each reporting its own id. No host.
 */
class AudioBarTest {
    @Test void theBarAsksForItsSiteWithItsFourAnswersInOrder() {
        Nodes.Stack bar = AudioBar.node("https://a.test", true);
        assertThat(bar.id()).isEqualTo("browser.audio");
        assertThat(bar.role()).isEqualTo(Nodes.LayoutRole.INLINE);
        assertThat(bar.state().visible()).isTrue();
        assertThat(bar.children()).hasSize(5);
        assertThat(bar.children().getFirst()).isInstanceOfSatisfying(Nodes.Text.class, text -> {
            assertThat(text.id()).isEqualTo("browser.audio.text");
            assertThat(text.text()).isEqualTo("Audio was muted. Allow playback by https://a.test?");
            assertThat(text.role()).isEqualTo(Nodes.TextRole.BODY);
        });
        List<Nodes.Button> buttons = bar.children().subList(1, 5).stream().map(Nodes.Button.class::cast).toList();
        assertThat(buttons).extracting(Nodes.Button::id, Nodes.Button::label, Nodes.Button::action, Nodes.Button::kind).containsExactly(
            tuple("browser.audio.allow-once", "Allow Once", "browser.audio.allow-once", Nodes.ButtonKind.SECONDARY),
            tuple("browser.audio.allow-always", "Allow Always", "browser.audio.allow-always", Nodes.ButtonKind.SECONDARY),
            tuple("browser.audio.deny-once", "Deny Once", "browser.audio.deny-once", Nodes.ButtonKind.SECONDARY),
            tuple("browser.audio.deny-always", "Deny Always", "browser.audio.deny-always", Nodes.ButtonKind.SECONDARY));
        assertThat(buttons).allSatisfy(button -> {
            assertThat(button.state().visible()).isTrue();
            assertThat(button.state().enabled()).isTrue();
        });
    }

    @Test void aHiddenBarKeepsItsPlaceAndShowsNothing() {
        Nodes.Stack bar = AudioBar.node("https://a.test", false);
        assertThat(bar.id()).isEqualTo("browser.audio");
        assertThat(bar.state().visible()).isFalse();
        assertThat(bar.children()).extracting(Nodes.Node::id).containsExactly("browser.audio.text", "browser.audio.allow-once",
            "browser.audio.allow-always", "browser.audio.deny-once", "browser.audio.deny-always");
    }

    @Test void eachButtonsActionIsItsAnswer() {
        assertThat(AudioBar.answer("browser.audio.allow-once")).contains(AudioAnswer.ALLOW_ONCE);
        assertThat(AudioBar.answer("browser.audio.allow-always")).contains(AudioAnswer.ALLOW_ALWAYS);
        assertThat(AudioBar.answer("browser.audio.deny-once")).contains(AudioAnswer.DENY_ONCE);
        assertThat(AudioBar.answer("browser.audio.deny-always")).contains(AudioAnswer.DENY_ALWAYS);
        for (String other : List.of("browser.audio", "browser.audio.text", "browser.audio.allow", "browser.unmute", "browser.settings", ""))
            assertThat(AudioBar.answer(other)).as(other).isEmpty();
    }
}
