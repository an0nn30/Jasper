package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypographyTest {
    /** Today's platform typography: the default scale and faces with no user setting. */
    static Typography platform() { return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, Typography.UserFont.NONE); }
    /** A size-only user setting, which keeps the platform family policy. */
    static Typography sized(float size) {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, new Typography.UserFont(Optional.empty(), Optional.of(size)));
    }
    /** A named family and a size. */
    static Typography named(String family, float size) {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, new Typography.UserFont(Optional.of(family), Optional.of(size)));
    }

    @Test void sectionAndTechnicalCaptionInkUseTheirOwnSourceCalibratedAdvances() {
        var typography=platform();
        org.junit.jupiter.api.Assumptions.assumeTrue(typography.family().equals("Helvetica Neue"));
        String[] sections={"Server","Access","Security & Startup"};
        int[][] extents={{71,75},{77,83},{205,209}};
        for(int i=0;i<sections.length;i++)assertThat(inkWidth(typography.font(Typography.Role.SECTION_LABEL),sections[i]))
            .as("source section %s at2x",sections[i]).isBetween(extents[i][0],extents[i][1]);
        assertThat(inkWidth(typography.font(Typography.Role.MONO_CAPTION),"http://127.0.0.1:8080")).isBetween(273,277);
        // Separate technical roles and established title policy do not follow caption calibration.
        assertThat(sized(26).font(Typography.Role.MONO).getSize2D()).isEqualTo(24);
        assertThat(sized(26).font(Typography.Role.PANEL_DETAIL_MONO).getSize2D()).isEqualTo(23.3f);
        assertThat(typography.font(Typography.Role.PROMPT_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
    }
    private static int inkWidth(java.awt.Font font,String text) {
        var image=new java.awt.image.BufferedImage(1000,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {g.scale(2,2);g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setFont(font);g.setColor(java.awt.Color.WHITE);g.drawString(text,4,30);}finally{g.dispose();}
        int min=1000,max=0;for(int y=0;y<100;y++)for(int x=0;x<1000;x++)if((image.getRGB(x,y)>>>24)>51){min=Math.min(min,x);max=Math.max(max,x);}
        return max-min+1;
    }

    @Test void nativeChromeRolesFollowTheReferenceWorkspaceSizesAndScaleWithTheUserSize() {
        // Desktop workspace references: title context and status path 12, toolbar labels 11.5, status text 10,
        // palette headings and hint 11; all regular weight on the platform text family.
        var roles = java.util.Map.of(Typography.Role.TITLE_CONTEXT, 12f, Typography.Role.TOOLBAR_LABEL, 11.5f, Typography.Role.STATUS_TEXT, 10f,
            Typography.Role.STATUS_PATH, 12f, Typography.Role.RESULTS_HEADING, 11f, Typography.Role.PALETTE_HINT, 11f);
        var typography = platform();
        roles.forEach((role, size) -> {
            assertThat(typography.font(role).getSize2D()).as(role.name()).isEqualTo(size);
            assertThat(typography.font(role).getAttributes().get(java.awt.font.TextAttribute.WEIGHT)).as(role.name())
                .isEqualTo(typography.font(Typography.Role.LABEL).getAttributes().get(java.awt.font.TextAttribute.WEIGHT));
            assertThat(sized(26).font(role).getSize2D()).as(role + " doubles with a 26pt body").isEqualTo(size * 2);
            assertThat(typography.font(role).getFamily()).isEqualTo(typography.font(Typography.Role.LABEL).getFamily());
        });
    }

    @Test void selectedResultsKeepEveryDeclaredRoleIncludingWeight() {
        for (var typography : List.of(platform(), named(Font.MONOSPACED, 32)))
            for (var role : Typography.Role.values()) {
                var base = typography.font(role); var selected = typography.resultFont(role, true);
                assertThat(selected.getFamily()).isEqualTo(base.getFamily());
                assertThat(selected.getSize2D()).isEqualTo(base.getSize2D());
                assertThat(selected).isEqualTo(base);
                assertThat(typography.resultFont(role, false)).isEqualTo(base);
            }
    }

    @Test void measuredMediumPolicyPreservesUserFamiliesAndProportionalSizes() {
        var user = named(java.awt.Font.MONOSPACED, 26);
        assertThat(user.font(Typography.Role.PROMPT_TITLE).getFamily()).isEqualTo(java.awt.Font.MONOSPACED);
        assertThat(user.font(Typography.Role.PROMPT_TITLE).getSize2D()).isEqualTo(30);
        assertThat(user.font(Typography.Role.PROMPT_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isNull();
        assertThat(named("Helvetica Neue", 19).font(Typography.Role.BODY).getSize2D()).isEqualTo(19);
        var face = new java.awt.Font("HelveticaNeue-Medium", java.awt.Font.PLAIN, 1);
        if (face.getFamily(java.util.Locale.ROOT).equals("Helvetica Neue")) {
            assertThat(named("Helvetica Neue", 19).font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            if (platform().family().equals("Helvetica Neue"))
                assertThat(platform().font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            var calibrated = named("Helvetica Neue", 26).font(Typography.Role.PROMPT_TITLE);
            assertThat(calibrated.getFontName(java.util.Locale.ROOT)).isEqualTo(face.getFontName(java.util.Locale.ROOT));
            assertThat(calibrated.getSize2D()).isEqualTo(30);
            assertThat(calibrated.getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
        }
    }

    /**
     * The platform family is resolved once per process. Each installed-family lookup creates native font objects that
     * macOS never releases (about twenty CFonts per call); per-paint lookups exhausted the test heap after Task 5.
     */
    @Test void platformFamilyIsResolvedOnceNotPerTypography() {
        var first = platform();
        int lookups = Typography.platformLookups();
        for (int i = 0; i < 100; i++) assertThat(platform().family()).isEqualTo(first.family());
        assertThat(Typography.platformLookups()).isEqualTo(lookups).isLessThanOrEqualTo(1);
    }

    /** Every typography is a scale and faces with the user's settings; a user size wins over the scale's body. */
    @Test void typographyComesOnlyFromAScaleFacesAndTheUserFont() {
        assertThat(Typography.class.getConstructors()).isEmpty();
        assertThat(platform().bodySize()).as("no user size: the scale's body").isEqualTo(13f);
        var large = TypeScale.DEFAULT.withBody(20);
        assertThat(Typography.of(large, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.BODY).getSize2D()).isEqualTo(20f);
        assertThat(Typography.of(large, FaceSet.PLATFORM, new Typography.UserFont(Optional.empty(), Optional.of(18f)))
            .font(Typography.Role.BODY).getSize2D()).isEqualTo(18f);
        for (float size : new float[] {Float.NaN, 5.9f, 96.1f})
            assertThatThrownBy(() -> sized(size)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid font family or body size");
        assertThatThrownBy(() -> named(" ", 13)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The kit seam moved no font. For every role, selected or not, under no user font, a size-only setting and named
     * families (the platform's own among them, as the calibration fixtures name it), {@code Typography.of} over the
     * default scale and platform faces gives exactly the font the former constructors gave: {@code former} is
     * Typography.font before the seam, over the frozen default-type.tsv.
     */
    @Test void everyRoleResolvesAsTheFormerTypographyDid() throws Exception {
        var table = TypeScaleTest.defaultTable();
        String platform = platform().family();
        record Mode(Typography typography, String family, float body, boolean platformDefault) {}
        var modes = List.of(new Mode(platform(), platform, 13, true), new Mode(sized(20), platform, 20, true),
            new Mode(named("Serif", 20), "Serif", 20, false), new Mode(named(platform, 19), platform, 19, false),
            new Mode(named("Helvetica Neue", 26), "Helvetica Neue", 26, false),
            new Mode(named(Font.MONOSPACED, 32), Font.MONOSPACED, 32, false));
        for (var mode : modes)
            for (var role : Typography.Role.values())
                for (boolean selected : new boolean[] {false, true}) {
                    var row = table.get(role.name());
                    assertThat(mode.typography().font(role, selected)).as("%s %s selected=%s", mode.family(), role, selected)
                        .isEqualTo(former(role, selected, mode.family(), mode.body(), mode.platformDefault(), row.size(), row.weight()));
                }
    }

    private static final boolean APPLE_TEXT = !new Font(".AppleSystemUIFont", Font.PLAIN, 1).getFamily(Locale.ROOT).equals(Font.DIALOG);
    private static final Font HELVETICA_MEDIUM = new Font("HelveticaNeue-Medium", Font.PLAIN, 1);
    private static final boolean MEDIUM = HELVETICA_MEDIUM.getFamily(Locale.ROOT).equals("Helvetica Neue");

    /** Typography.font at 0af7b83e2 (Typography.java:44-60), with the role's frozen size and weight passed in. */
    private static Font former(Typography.Role role, boolean selected, String family, float bodySize, boolean platformDefault,
                               float size, int declaredWeight) {
        String resolved = role == Typography.Role.MONO || role == Typography.Role.MONO_SMALL || role == Typography.Role.MONO_CAPTION
            || role == Typography.Role.PANEL_DETAIL_MONO ? Font.MONOSPACED : family;
        int weight = selected && role == Typography.Role.LABEL ? 500 : declaredWeight;
        if (platformDefault && role != Typography.Role.BODY && weight < 600 && resolved.equals("Helvetica Neue") && APPLE_TEXT)
            resolved = ".AppleSystemUIFont";
        if (weight == 600 && resolved.equals("Helvetica Neue") && MEDIUM)
            return HELVETICA_MEDIUM.deriveFont(Map.of(TextAttribute.SIZE, size * bodySize / 13f,
                TextAttribute.TRACKING, platformDefault && role == Typography.Role.SECTION_LABEL ? .060f : .018f));
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, size * bodySize / 13f,
            TextAttribute.WEIGHT, weight == 600 ? TextAttribute.WEIGHT_SEMIBOLD
                : weight == 500 ? TextAttribute.WEIGHT_MEDIUM : TextAttribute.WEIGHT_REGULAR));
    }
}
