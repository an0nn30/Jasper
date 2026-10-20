package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.TerminalTypography;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.*;

class HostedTypographyTest {
    @Test void publishesBeforeDeliverySuppressesEqualityAndSupportsClosingDuringDelivery() {
        var owner = new HostedTypography();
        var next = new TerminalTypography("DialogInput", 13.5, List.of("Monospaced"), true, 1.25);
        var seen = new ArrayList<TerminalTypography>();
        var skipped = new AtomicReference<Subscription>();
        var self = new AtomicReference<Subscription>();
        onEdt(() -> {
            self.set(owner.subscribe(value -> {
                assertThat(owner.current()).isEqualTo(value);
                seen.add(value); self.get().close(); skipped.get().close();
            }));
            skipped.set(owner.subscribe(value -> { throw new AssertionError("closed registration delivered"); }));
            owner.publish(next);
            owner.publish(new TerminalTypography("DialogInput", 13.5, List.of("Monospaced"), true, 1.25));
            owner.publish(new TerminalTypography("DialogInput", 14, List.of(), false, 1));
        });
        assertThat(seen).containsExactly(next);
        assertThat(CompletableFuture.supplyAsync(owner::current).join().size()).isEqualTo(14);
    }

    @Test void publicationAndRegistrationRequireEdtButClosureIsImmediateOffThread() {
        var owner = new HostedTypography();
        assertThatIllegalStateException().isThrownBy(() -> owner.publish(owner.current()));
        assertThatIllegalStateException().isThrownBy(() -> owner.subscribe(value -> { }));
        onEdt(() -> {
            var registration = owner.subscribe(value -> { throw new AssertionError("closed off-thread"); });
            CompletableFuture.runAsync(registration::close).join();
            owner.publish(new TerminalTypography("Mono", 18, List.of(), false, 1));
        });
    }

    @Test void convertsEverySavedFontFieldAndDefaultsWithoutConfiguration() {
        var defaults = new HostedTypography().current();
        assertThat(defaults.family()).isEqualTo("Cousine Nerd Font Mono");
        assertThat(defaults.fallback()).containsExactly("Symbols Nerd Font Mono", "Apple Color Emoji");
        assertThat(HostedTypography.from(new dev.jasper.app.config.FontConfig("Mono", 13.5f,
            List.of("DialogInput"), false, 1.25f)))
            .isEqualTo(new TerminalTypography("Mono", 13.5, List.of("DialogInput"), false, 1.25));
    }
}
