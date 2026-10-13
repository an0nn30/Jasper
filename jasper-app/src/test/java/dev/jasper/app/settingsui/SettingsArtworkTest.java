package dev.jasper.app.settingsui;

import dev.jasper.app.testsupport.*;
import dev.jasper.ui.icons.EncodedArtwork;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeRoute;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class SettingsArtworkTest {
    private static int[] pixels(javax.swing.Icon icon){var image=new java.awt.image.BufferedImage(icon.getIconWidth(),icon.getIconHeight(),2);var g=image.createGraphics();try{icon.paintIcon(null,g,0,0);}finally{g.dispose();}return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}
    private static EncodedArtwork art() {return new EncodedArtwork(EncodedArtwork.Format.SVG,"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"8\"><rect width=\"16\" height=\"8\" fill=\"#12a4e0\"/></svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void actualPageTreePublishesDeclaredArtworkWithoutBuildingPagesAndRetiresOnClose() {
        try(var bindings=KitTestSupport.bindings(true)) {
            var context=bindings.context(ThemeRoute.CHROME);var names=context.icons().names();var tree=new PageTree();tree.artwork(context.icons());
            var page=new SettingsPage() {
                public String id(){return "external.owner";}public String title(){return "Tools";}public Optional<String> parent(){return Optional.empty();}
                public Optional<dev.jasper.ui.icons.IdentityArtwork> identityArtwork(){return Optional.of(new dev.jasper.ui.icons.IdentityArtwork(art(),Nodes.Hue.GREEN));}public List<String> searchTerms(){return List.of();}
                public JComponent component(){throw new AssertionError("Artwork must not create plugin UI");}public boolean modified(){return false;}public void reset(){}
                public CompletableFuture<Void> apply(){return CompletableFuture.completedFuture(null);}public void highlight(String value){}public void dispose(){}
            };
            tree.show(List.of(page),value->true,Map.of());tree.publish(()->{});
            String name=tree.node().roots().getFirst().icon().orElseThrow();assertThat(name).startsWith("received");assertThat(context.icons().names()).contains(name);
            var tile=context.icons().tile(name,context.theme().semantic(),Nodes.Hue.BLUE,28,false);
            var expectedName="test:expected";try(var registration=context.icons().register(expectedName,new dev.jasper.ui.icons.IdentityArtwork(art(),Nodes.Hue.GREEN))) {
                assertThat(pixels(tile)).isEqualTo(pixels(context.icons().tile(expectedName,context.theme().semantic(),Nodes.Hue.PURPLE,28,false)));
            }
            tree.show(List.of(page),value->false,Map.of());tree.publish(()->{});assertThat(context.icons().names()).contains(name);
            tree.close();tree.close();assertThat(context.icons().names()).isEqualTo(names);
        }
    }
    @Test void actualNotificationOwnerUsesMetadataAndKeepsReceiverUntilItsViewCloses() {
        try(var bindings=KitTestSupport.bindings(true)) {
            var context=bindings.context(ThemeRoute.CHROME);var names=context.icons().names();
            var groups=List.of(new NotificationGroupChoices.Group("arbitrary.events","Events","Third party","card","unrelated.owner","plugin",Optional.of(art()),Nodes.Hue.PURPLE));
            var current=new java.util.concurrent.atomic.AtomicReference<>(groups);
            var editor=new NotificationGroupsEditor(new NotificationGroupChoices(current::get),()->context);
            var view=new KitView(context,new ViewSpec(editor.node("groups",ControlState.READY)),event->{});
            view.setSize(600,400);KitTestSupport.layout(view);
            var icon=KitTestSupport.require(view,"groups:owner-0:icon",JLabel.class);assertThat(icon.getIcon()).isNotNull();
            try(var registration=context.icons().register("test:expected",new dev.jasper.ui.icons.IdentityArtwork(art(),Nodes.Hue.PURPLE))) {
                var expected=context.icons().tile("test:expected",context.theme().semantic(),Nodes.Hue.BLUE,icon.getIcon().getIconWidth(),icon.getIcon().getIconHeight(),context.icons().size(Nodes.IconSize.CONTROL),false);
                assertThat(pixels(icon.getIcon())).isEqualTo(pixels(expected));
            }
            assertThat(context.icons().names().size()).isEqualTo(names.size()+1);
            editor.refresh();view.update(new ViewSpec(editor.node("groups",ControlState.READY)));assertThat(context.icons().names().size()).isEqualTo(names.size()+1);
            current.set(List.of(new NotificationGroupChoices.Group("arbitrary.events","Events","Third party","card","unrelated.owner","plugin",Optional.of(art()),Nodes.Hue.GREEN)));
            editor.refresh();view.update(new ViewSpec(editor.node("groups",ControlState.READY)));
            assertThat(context.icons().names().size()).as("notification owner safely retains both generations until dispose").isEqualTo(names.size()+2);
            view.close();editor.dispose();editor.dispose();assertThat(context.icons().names()).isEqualTo(names);
        }
    }
}
