package dev.jasper.sdk.testing;
import dev.jasper.sdk.ui.*;
import javax.swing.*;
final class FakeKitContent {
 private FakeKitContent() {}
 static void validate(CustomContent content) {
  java.awt.Component root=content.component();
  if(root instanceof JScrollPane scroll)root=scroll.getViewport().getView();
  boolean editor=root instanceof JTextArea || root instanceof JTextPane || root instanceof JEditorPane;
  boolean canvas=root instanceof JComponent && !(root instanceof JPanel) && !(root instanceof javax.swing.text.JTextComponent) && !(root instanceof AbstractButton) && !(root instanceof JLabel) && !(root instanceof JList) && !(root instanceof JTable) && !(root instanceof JTree) && !(root instanceof JComboBox) && !(root instanceof JSpinner) && !(root instanceof JSlider);
  if(content.kind()==Nodes.CustomKind.EDITOR ? !editor : !canvas)throw new IllegalArgumentException("Ordinary controls require descriptors");
 }
}
