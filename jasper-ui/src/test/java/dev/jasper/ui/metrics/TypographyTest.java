package dev.jasper.ui.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TypographyTest {
    @Test void sectionAndTechnicalCaptionInkUseTheirOwnSourceCalibratedAdvances() {
        var typography=new Typography();
        org.junit.jupiter.api.Assumptions.assumeTrue(typography.family().equals("Helvetica Neue"));
        String[] sections={"Server","Access","Security & Startup"};
        int[][] extents={{71,75},{77,83},{205,209}};
        for(int i=0;i<sections.length;i++)assertThat(inkWidth(typography.font(Typography.Role.SECTION_LABEL),sections[i]))
            .as("source section %s at2x",sections[i]).isBetween(extents[i][0],extents[i][1]);
        assertThat(inkWidth(typography.font(Typography.Role.MONO_CAPTION),"http://127.0.0.1:8080")).isBetween(273,277);
        // Separate technical roles and established title policy do not follow caption calibration.
        assertThat(new Typography(26).font(Typography.Role.MONO).getSize2D()).isEqualTo(24);
        assertThat(new Typography(26).font(Typography.Role.PANEL_DETAIL_MONO).getSize2D()).isEqualTo(23.3f);
        assertThat(typography.font(Typography.Role.HERO_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
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
        var typography = new Typography();
        roles.forEach((role, size) -> {
            assertThat(typography.font(role).getSize2D()).as(role.name()).isEqualTo(size);
            assertThat(typography.font(role).getAttributes().get(java.awt.font.TextAttribute.WEIGHT)).as(role.name())
                .isEqualTo(typography.font(Typography.Role.LABEL).getAttributes().get(java.awt.font.TextAttribute.WEIGHT));
            assertThat(new Typography(26).font(role).getSize2D()).as(role + " doubles with a 26pt body").isEqualTo(size * 2);
            assertThat(typography.font(role).getFamily()).isEqualTo(typography.font(Typography.Role.LABEL).getFamily());
        });
    }

    @Test void selectedResultsKeepEveryDeclaredRoleIncludingWeight() {
        for (var typography : java.util.List.of(new Typography(), new Typography(java.awt.Font.MONOSPACED, 32)))
            for (var role : Typography.Role.values()) {
                var base = typography.font(role); var selected = typography.resultFont(role, true);
                assertThat(selected.getFamily()).isEqualTo(base.getFamily());
                assertThat(selected.getSize2D()).isEqualTo(base.getSize2D());
                assertThat(selected).isEqualTo(base);
                assertThat(typography.resultFont(role, false)).isEqualTo(base);
            }
    }

    @Test void measuredMediumPolicyPreservesUserFamiliesAndProportionalSizes() {
        var user = new Typography(java.awt.Font.MONOSPACED, 26);
        assertThat(user.font(Typography.Role.HERO_TITLE).getFamily()).isEqualTo(java.awt.Font.MONOSPACED);
        assertThat(user.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(30);
        assertThat(user.font(Typography.Role.HERO_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isNull();
        assertThat(new Typography("Helvetica Neue", 19).font(Typography.Role.BODY).getSize2D()).isEqualTo(19);
        var face = new java.awt.Font("HelveticaNeue-Medium", java.awt.Font.PLAIN, 1);
        if (face.getFamily(java.util.Locale.ROOT).equals("Helvetica Neue")) {
            assertThat(new Typography("Helvetica Neue", 19).font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            if (new Typography().family().equals("Helvetica Neue"))
                assertThat(new Typography().font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            var calibrated = new Typography("Helvetica Neue", 26).font(Typography.Role.HERO_TITLE);
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
        var first = new Typography();
        int lookups = Typography.platformLookups();
        for (int i = 0; i < 100; i++) assertThat(new Typography().family()).isEqualTo(first.family());
        assertThat(Typography.platformLookups()).isEqualTo(lookups).isLessThanOrEqualTo(1);
    }
}
