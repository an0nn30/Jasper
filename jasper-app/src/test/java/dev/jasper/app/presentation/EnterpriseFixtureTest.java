package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import org.junit.jupiter.api.Test;

/** Spec 16.1.5: the shipped kit, parsed by the production parser, is jasper-ui's Enterprise fixture. */
class EnterpriseFixtureTest {
    @Test void theShippedKitIsTheFixtureKit() {
        Kit shipped = EnterpriseResources.facts().parsed().orElseThrow().kit();
        Kit fixture = KitFixtures.enterprise();
        assertThat(shipped.metrics()).isEqualTo(fixture.metrics());
        assertThat(shipped.type()).isEqualTo(fixture.type());
        assertThat(shipped.controls()).isEqualTo(fixture.controls());
        assertThat(shipped.colors()).isEqualTo(fixture.colors());
        assertThat(shipped.uiKeys()).isEqualTo(fixture.uiKeys());
        assertThat(shipped.metrics()).as("spec 5.1's tokens over the classic catalog")
            .isEqualTo(Kit.CLASSIC.metrics().with(KitFixtures.enterpriseTokens()));
        assertThat(shipped.type()).isEqualTo(Kit.CLASSIC.type().withBody(12));
        assertThat(shipped.faces().uiFace(400)).isEqualTo("IBM Plex Sans");
        assertThat(shipped.faces().uiFace(500)).isEqualTo("IBM Plex Sans Medm");
        assertThat(shipped.faces().uiFace(600)).isEqualTo("IBM Plex Sans SmBld");
        assertThat(shipped.faces().dataRegular()).isEqualTo("IBM Plex Mono");
        assertThat(shipped.faces().dataMedium()).isEqualTo("IBM Plex Mono Medm");
        assertThat(EnterpriseResources.facts().parsed().orElseThrow().chrome()).as("plan 6a's EnterpriseLook.CHROME is the shipped table")
            .isEqualTo(dev.jasper.app.testsupport.EnterpriseLook.CHROME);
    }
}
