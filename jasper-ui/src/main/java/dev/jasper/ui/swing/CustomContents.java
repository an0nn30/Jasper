package dev.jasper.ui.swing;
import dev.jasper.ui.model.Nodes;
import javax.swing.*;
import java.awt.BorderLayout;
/** Explicit specialized slots; registration owns exactly one widget and its lifetime. */
final class CustomContents {
 private CustomContents() {}
 private static void validate(Nodes.CustomKind kind,JComponent component) {
  java.awt.Component root=component;if(root instanceof JScrollPane scroll)root=scroll.getViewport().getView();
  boolean editor=root instanceof JTextArea || root instanceof JTextPane || root instanceof JEditorPane;
  boolean canvas=root instanceof JComponent && !(root instanceof JPanel) && !(root instanceof javax.swing.text.JTextComponent) && !(root instanceof AbstractButton) && !(root instanceof JLabel) && !(root instanceof JList) && !(root instanceof JTable) && !(root instanceof JTree) && !(root instanceof JComboBox) && !(root instanceof JSpinner) && !(root instanceof JSlider);
  if(root instanceof JComponent value && KitOwnership.owns(value) || (kind==Nodes.CustomKind.EDITOR ? !(editor || canvas) : !canvas))throw new IllegalArgumentException("Ordinary controls require descriptors");
 }
 static final class CustomControl extends ControlRegistry.Control {
  private JComponent content;
  CustomControl(KitView view,Nodes.Custom node){super(view,node,new JPanel(new BorderLayout()));component.setOpaque(false);refresh();}
  void register(JComponent content,AutoCloseable lifetime){if(this.content!=null)throw new IllegalArgumentException("Content already registered");if(content.getParent()!=null)throw new IllegalArgumentException("Content is already mounted");validate(((Nodes.Custom)node).kind(),content);this.content=content;component.add(content,BorderLayout.CENTER);own(lifetime);refresh();component.revalidate();}
  @Override void refresh(){state();component.getAccessibleContext().setAccessibleName(((Nodes.Custom)node).accessibleName());}
  @Override JComponent focusTarget(){return content==null?component:content;}
  @Override public void close(){if(closed)return;super.close();component.removeAll();content=null;}
 }
}
