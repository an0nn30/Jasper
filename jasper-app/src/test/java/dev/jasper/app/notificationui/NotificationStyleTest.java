package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.ui.model.Nodes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Named poster artwork keeps severity colour; absent artwork keeps its severity glyph. */
@ExtendWith(dev.jasper.app.testsupport.EdtTestExtension.class)
class NotificationStyleTest {
    @Test void everySeverityHasItsGlyphTintAndName() {
        assertThat(NotificationStyle.glyph(Severity.INFO)).isEqualTo("info");
        assertThat(NotificationStyle.glyph(Severity.WARNING)).isEqualTo("warning");
        assertThat(NotificationStyle.glyph(Severity.ERROR)).isEqualTo("error");
        assertThat(NotificationStyle.tone(Severity.INFO)).isEqualTo(Nodes.Severity.INFO);
        assertThat(NotificationStyle.tone(Severity.WARNING)).isEqualTo(Nodes.Severity.WARNING);
        assertThat(NotificationStyle.tone(Severity.ERROR)).isEqualTo(Nodes.Severity.DANGER);
        assertThat(NotificationStyle.severityName(Severity.ERROR)).isEqualTo("Error");
    }

    @Test void aNamedPostersIconRetainsTheSeverityTint() {
        var group = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
        var center = new NotificationCenter(Runnable::run, Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC),
            () -> Delivery.Presence.FOCUSED);
        center.register(group);
        Icon poster = dev.jasper.app.platform.AppIcons.semantic("file");
        var entry = center.find(center.post(NotificationRequest.of(group.id(), Severity.WARNING, "Posted").withIcon(poster)).orElseThrow()).orElseThrow();
        var icon = NotificationStyle.icon("severity", entry, new dev.jasper.app.platform.PresentationIcons());
        assertThat(icon.name()).isEqualTo("file");
        assertThat(icon.severity()).contains(Nodes.Severity.WARNING);
        assertThat(icon.accessibleName()).isEqualTo("Warning");
    }
    @Test void expiredCustomArtworkRemountsWithoutTheOriginalRegistryOrAnyPluginCallback() {
        var group = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
        var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
        center.register(group);
        var poster = new dev.jasper.app.platform.PresentationIcon( "owner:custom", "M 2 2 L 14 14");
        var id = center.post(NotificationRequest.of(group.id(), Severity.WARNING, "Posted").withIcon(poster)).orElseThrow();
        center.expire(id);
        var entry=center.find(id).orElseThrow();
        assertThat(entry.expired()).isTrue();
        try(var receiver=new dev.jasper.app.platform.PresentationIcons()) {
            var context=receiver.context(dev.jasper.app.testsupport.KitTestSupport.context());
            var icon=NotificationStyle.icon("icon",entry,receiver);
            assertThat(icon.name()).isEqualTo("owner:custom");assertThat(icon.severity()).contains(Nodes.Severity.WARNING);
            assertThat(context.icons().names()).contains("owner:custom");
            var card=new NotificationCard(context,center,entry,false, id2 -> { });
            card.close();
            // Stale delivery/update after disposal must not re-register or access a closed receiver.
            card.update(entry);assertThat(card.closed()).isTrue();
        }
    }

    @Test void coalescedCardReplacesCustomArtworkWithBuiltinAndDifferentCustomArtwork() {
        for (String nextName : java.util.List.of("file", "owner:next")) {
            var center = new NotificationCenter(Runnable::run, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
            center.register(new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD));
            var request = NotificationRequest.of("test.cards", Severity.WARNING, "Repeated");
            var id = center.post(request.withIcon(new dev.jasper.app.platform.PresentationIcon("owner:old", "M 2 2 L 14 14"))).orElseThrow();
            try (var card = new NotificationCard(dev.jasper.app.testsupport.KitTestSupport.context(), center, center.find(id).orElseThrow(), false, id2 -> { })) {
                var next = new dev.jasper.app.platform.PresentationIcon(nextName, nextName.contains(":") ? "M 2 14 L 14 2" : null);
                assertThat(center.post(request.withIcon(next))).contains(id);
                card.update(center.find(id).orElseThrow());
                assertThat(((Nodes.Icon) card.iconView().spec().root()).name()).isEqualTo(nextName);
                assertThat(card.notification().count()).isEqualTo(2);
            }
        }
    }

    @Test void coalescedCardReplacesThePathWhenTheSemanticNameIsRepublished() {
        var context=dev.jasper.app.testsupport.KitTestSupport.context();
        var center=new NotificationCenter(Runnable::run,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),() -> Delivery.Presence.FOCUSED);
        center.register(new NotificationGroup("test.cards","Cards","jasper",DisplayMode.CARD));
        var request=NotificationRequest.of("test.cards",Severity.WARNING,"Repeated");
        var first=new dev.jasper.app.platform.PresentationIcon("owner:mark","M 2 2 L 14 14");
        var id=center.post(request.withIcon(first)).orElseThrow();
        try(var card=new NotificationCard(context,center,center.find(id).orElseThrow(),false, id2 -> { })) {
            var label=dev.jasper.app.testsupport.KitTestSupport.require(card.iconView(),NotificationCard.ICON,javax.swing.JLabel.class);
            var before=dev.jasper.app.testsupport.IconPixels.of(label.getIcon());
            var next=new dev.jasper.app.platform.PresentationIcon("owner:mark","M 2 14 L 14 2");
            assertThat(center.post(request.withIcon(next))).contains(id);
            card.update(center.find(id).orElseThrow());
            assertThat(card.notification().request().icon()).contains(next);
            assertChangedSnapshot(label,context,before);
        }
    }
    static void assertChangedSnapshot(javax.swing.JLabel label,dev.jasper.ui.swing.KitContext context,int[] before) {
        try(var expected=new dev.jasper.ui.icons.IconRegistry()) {
            expected.register("owner:mark","M 2 14 L 14 2");
            var actual=dev.jasper.app.testsupport.IconPixels.of(label.getIcon());
            assertThat(actual).isNotEqualTo(before).containsExactly(dev.jasper.app.testsupport.IconPixels.of(
                expected.icon("owner:mark",context.theme().semantic(),dev.jasper.ui.theme.SemanticColor.STATUS_WARNING,label.getIcon().getIconWidth())));
        }
    }

}
