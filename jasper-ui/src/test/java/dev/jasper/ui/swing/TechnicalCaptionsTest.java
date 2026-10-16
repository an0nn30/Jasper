package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code PickerFace.technicalCaption}: the caption is a data value, drawn in the data face. */
@SuppressWarnings("try")
class TechnicalCaptionsTest {
    @Test void aTechnicalCaptionTakesTheDataFaceAtEitherDensity() {
        assertThat(PickerMenus.captionRole(false, false)).isEqualTo(Typography.Role.CAPTION);
        assertThat(PickerMenus.captionRole(true, false)).isEqualTo(Typography.Role.PICKER_CAPTION);
        assertThat(PickerMenus.captionRole(false, true)).isEqualTo(Typography.Role.MONO_CAPTION);
        assertThat(PickerMenus.captionRole(true, true)).isEqualTo(Typography.Role.MONO_CAPTION);
    }

    @Test void aTechnicalCaptionPaintsInAnotherFaceThanAPlainOne() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var kind : List.of(ChoiceKind.PICKER, ChoiceKind.DROPDOWN))
                    assertThat(Arrays.equals(pixels(render(context, kind, false)), pixels(render(context, kind, true)))).as(kind.name()).isFalse();
            } finally { release(context); }
        });
    }

    private static BufferedImage render(KitContext context, ChoiceKind kind, boolean technical) {
        var face = new PickerFace("Mac", "dustin@192.168.1.119:22", "server", Hue.BLUE, PickerDensity.DEFAULT, technical);
        var options = kind == ChoiceKind.DROPDOWN ? List.of(new Option("mac", "Mac", Optional.empty())) : List.<Option>of();
        var spec = new ViewSpec(new Choice("via", ControlState.READY, kind, options, kind == ChoiceKind.DROPDOWN ? "mac" : "", "Via host", "pick", "",
            Optional.empty(), Optional.of(face)));
        try (var view = new KitView(context, spec, e -> {})) { return KitFixtures.render(view, 320, 60, 1); }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
