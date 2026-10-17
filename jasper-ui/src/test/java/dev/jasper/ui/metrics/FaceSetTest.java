package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Spec 5.6: a kit's faces by registered full name, and the platform face for any that is not registered. */
class FaceSetTest {
    @Test void facesAreNamedPerWeightAndCompareByValue() {
        var faces = FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm");
        assertThat(faces.namesUiFaces()).isTrue();
        assertThat(faces.uiFace(400)).isEqualTo("Look Sans");
        assertThat(faces.uiFace(500)).isEqualTo("Look Sans Medm");
        assertThat(faces.uiFace(600)).isEqualTo("Look Sans SmBld");
        assertThat(faces.dataRegular()).isEqualTo("Look Mono");
        assertThat(faces).isEqualTo(FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm"))
            .hasSameHashCodeAs(FaceSet.of("Look Sans", "Look Sans Medm", "Look Sans SmBld", "Look Mono", "Look Mono Medm"));
        assertThat(FaceSet.PLATFORM.namesUiFaces()).isFalse();
        assertThat(FaceSet.of("", "", "", "", "")).isEqualTo(FaceSet.PLATFORM);
    }

    @Test void aFaceThatIsNotRegisteredDrawsInThePlatformFace() {
        var platform = Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, Typography.UserFont.NONE);
        var missing = Typography.of(TypeScale.DEFAULT, FaceSet.of("No Such Face 5b", "", "", "No Such Mono 5b", ""), Typography.UserFont.NONE);
        for (var role : Typography.Role.values()) {
            assertThat(missing.font(role).getFontName(Locale.ROOT)).as(role.name()).isEqualTo(platform.font(role).getFontName(Locale.ROOT));
            assertThat(missing.font(role).getSize2D()).as(role.name()).isEqualTo(platform.font(role).getSize2D());
        }
        assertThat(missing.font(Typography.Role.MONO).getFamily(Locale.ROOT)).isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 1).getFamily(Locale.ROOT));
    }
}
