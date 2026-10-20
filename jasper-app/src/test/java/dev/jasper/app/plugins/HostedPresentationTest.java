package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseResources;
import dev.jasper.sdk.ui.Presentation;
import dev.jasper.ui.kit.Kit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plan 15a (spec 9.1): the presentation plugins read is the look's plugin projection, its kit and its defaults. */
class HostedPresentationTest {
    static final Projections.Host LINUX = new Projections.Host(Projections.Platform.LINUX, false);

    @Test void theDefaultLookIsReportedWithThePlatformFacesAndTheCoreToolbarHeight() {
        var look = Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX).plugins();
        assertThat(HostedPresentation.of(look, Kit.DEFAULT, DefaultSource.CORE)).isEqualTo(new Presentation(false, Presentation.Icons.NEW_UI,
            Presentation.ToolWindows.RAILS, Presentation.TitleBar.UNIFIED, Presentation.Menus.TITLE_BAR, Presentation.ToolbarSearch.FIELD,
            Optional.empty(), Optional.empty(), 13, 30));
    }

    @Test void enterpriseIsReportedFromItsKitAndItsDefaults() {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        var look = Projections.of(parsed.chrome(), true, true, "dev.jasper.enterprise", LINUX).plugins();
        assertThat(HostedPresentation.of(look, parsed.kit(), parsed.defaults())).isEqualTo(new Presentation(true, Presentation.Icons.CLASSIC,
            Presentation.ToolWindows.STRIPES, Presentation.TitleBar.NATIVE, Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD,
            Optional.of("IBM Plex Sans"), Optional.of("IBM Plex Mono"), 12, 28));
    }

    @Test void theFallbackReportsTheDefaultLooksChromeWithTheDefaultsReadAtStart() {
        var parsed = EnterpriseResources.facts().parsed().orElseThrow();
        var provider = new dev.jasper.app.presentation.Presentation.Provider("dev.jasper.enterprise", "Enterprise", "0.3.0", "d");
        var fallback = dev.jasper.app.presentation.Presentation.of(provider, parsed, LINUX).fallback(LINUX);
        var reported = HostedPresentation.of(fallback.projections().plugins(), fallback.kit(), fallback.defaults());
        assertThat(reported.classic()).isFalse();
        assertThat(reported.icons()).isEqualTo(Presentation.Icons.NEW_UI);
        assertThat(reported.uiFace()).isEmpty();
        assertThat(reported.bodySize()).isEqualTo(13);
        assertThat(reported.toolbarHeight()).as("the kit's default, which the configuration keeps").isEqualTo(28);
        assertThat(fallback.defaults().plugin("dev.jasper.editor")).as("the kit's plugin defaults still apply")
            .containsEntry("font_family", "IBM Plex Mono");
    }
}
