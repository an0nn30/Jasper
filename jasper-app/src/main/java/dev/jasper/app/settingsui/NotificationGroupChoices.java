package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The notification groups the Notifications page lists, supplied by the application as plain text. EDT only.
 *
 * @param groups every group known now, in the order to list them
 */
public record NotificationGroupChoices(Supplier<List<Group>> groups) {
    /** No groups, for pages built without an application. */
    public static final NotificationGroupChoices NONE = new NotificationGroupChoices(List::of);

    public NotificationGroupChoices {
        Objects.requireNonNull(groups, "groups");
    }

    /**
     * One group.
     *
     * @param id its id: the key in {@code notifications.groups}
     * @param name the name it is listed with
     * @param owner who declared it, as shown: "Jasper" or a plugin's name
     * @param defaultMode how its notifications appear by default: "card", "sticky", "history" or "off"
     * @param tileHue semantic tile backdrop declared by the owner
     * @param selectedInk explicit selected identity-tile policy
     * @param artwork immutable declared owner artwork; empty or invalid artwork uses the generic owner fallback
     */
    public record Group(String id, String name, String owner, String defaultMode, String ownerId, String ownerIcon, java.util.Optional<dev.jasper.ui.icons.EncodedArtwork> artwork, dev.jasper.ui.model.Nodes.Hue tileHue, dev.jasper.ui.icons.IdentityArtwork.SelectedInk selectedInk) {
        public Group(String id,String name,String owner,String defaultMode,String ownerId,String ownerIcon, java.util.Optional<dev.jasper.ui.icons.EncodedArtwork> artwork, dev.jasper.ui.model.Nodes.Hue tileHue) {this(id,name,owner,defaultMode,ownerId,ownerIcon,artwork,tileHue,dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED); }
        public Group(String id,String name,String owner,String defaultMode,String ownerId,String ownerIcon, java.util.Optional<dev.jasper.ui.icons.EncodedArtwork> artwork) {this(id,name,owner,defaultMode,ownerId,ownerIcon,artwork,dev.jasper.ui.model.Nodes.Hue.NEUTRAL);}
        public java.util.Optional<dev.jasper.ui.icons.IdentityArtwork> identityArtwork(){return artwork.map(value -> new dev.jasper.ui.icons.IdentityArtwork(value,tileHue,selectedInk));}
        public Group(String id,String name,String owner,String defaultMode,String ownerId,String ownerIcon) {this(id,name,owner,defaultMode,ownerId,ownerIcon,java.util.Optional.empty());}
        public Group(String id,String name,String owner,String defaultMode) { this(id,name,owner,defaultMode,owner,""); }
        public Group {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(defaultMode, "defaultMode");
            Objects.requireNonNull(selectedInk);Objects.requireNonNull(tileHue);Objects.requireNonNull(artwork);Objects.requireNonNull(ownerId);Objects.requireNonNull(ownerIcon);
        }
    }
}
