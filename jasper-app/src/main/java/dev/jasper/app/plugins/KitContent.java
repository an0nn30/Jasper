package dev.jasper.app.plugins;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.*;
import java.awt.Color;
import java.util.*;
import javax.swing.*;
/** Host-recorded specialized content ownership, presentation and cleanup. */
final class KitContent implements AutoCloseable {
 private static final Map<JComponent,KitContent> OWNERS=new IdentityHashMap<>();
 private final HostedKit owner;private final CustomContent content;private Subscription themeChanges;private Subscription workspaceChanges;private boolean closed;
 KitContent(HostedKit owner,CustomContent content){this.owner=owner;this.content=content;if(OWNERS.containsKey(content.component())||content.component().getParent()!=null)throw new IllegalArgumentException("Content is already owned or mounted");OWNERS.put(content.component(),this);
  try{refresh();if(owner.appearance!=null){themeChanges=owner.appearance.onChanged(v -> refresh());workspaceChanges=owner.appearance.onWorkspaceChanged(v -> refresh());}}catch(Throwable failure){abort();throw failure;}
 }
 private void refresh(){if(closed)return;var appearance=owner.appearance;if(appearance==null)return;
  var component=content.component();var background=appearance.color(SemanticColor.SURFACE_INPUT);var foreground=appearance.color(SemanticColor.TEXT_PRIMARY);double opacity=appearance.workspaceOpacity(component);
  component.setForeground(foreground);component.setBackground(new Color(background.getRed(),background.getGreen(),background.getBlue(),(int)Math.round(background.getAlpha()*opacity)));component.setOpaque(opacity==1);component.repaint();
 }
 void abort(){if(closed)return;closed=true;OWNERS.remove(content.component(),this);if(workspaceChanges!=null)workspaceChanges.close();if(themeChanges!=null)themeChanges.close();}
 @Override public void close(){if(closed)return;abort();try{content.lifetime().close();}catch(Throwable failure){owner.failed();}}
 static Nodes.Custom find(Nodes.Node root,String contentId){if(root instanceof Nodes.Custom c && c.contentId().equals(contentId))return c;for(var child:children(root)){var result=find(child,contentId);if(result!=null)return result;}return null;}
    static List<Nodes.Node> children(Nodes.Node node) {
        var result = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel n -> { result.add(n.header()); n.searchOrNavigation().ifPresent(result::add); result.add(n.body()); n.footer().ifPresent(result::add); }
            case Nodes.Header n -> { n.context().ifPresent(result::add); n.primary().ifPresent(result::add); }
            case Nodes.Stack n -> result.addAll(n.children());
            case Nodes.ItemList n -> { n.addAction().ifPresent(result::add); for(var item:n.items()) { item.leading().ifPresent(result::add);result.addAll(item.title());result.addAll(item.subtitle());result.addAll(item.trailing()); } }
            case Nodes.Card n -> result.addAll(n.children());
            case Nodes.Split n -> result.addAll(n.children());
            case Nodes.FormRow n -> { result.add(n.control()); n.trailing().ifPresent(result::add); }
            case Nodes.SettingRow n -> result.add(n.control());
            case Nodes.Section n -> result.add(n.body());
            case Nodes.Empty n -> { result.add(n.icon()); result.addAll(n.actions()); }
            case Nodes.Form n -> { n.hero().ifPresent(result::add); result.addAll(n.sections()); n.preview().ifPresent(result::add); result.addAll(n.actions()); }
            default -> {}
        }
        return result;
    }
}
