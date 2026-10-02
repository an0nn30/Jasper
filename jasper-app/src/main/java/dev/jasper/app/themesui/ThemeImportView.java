package dev.jasper.app.themesui;

import dev.jasper.app.themeimport.ThemeImporter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;

/** Immutable providers and explicit per-provider choices. No file operations. */
final class ThemeImportView extends JPanel {
    final Map<String,JComboBox<ThemeImporter.ConflictChoice>> decisions = new LinkedHashMap<>();
    final JTextArea details = new JTextArea();
    final JButton commit = new JButton("Import");
    ThemeImportView(ThemeImporter.ImportPlan plan, Consumer<Map<String,ThemeImporter.ConflictChoice>> accept, Runnable cancel) {
        super(new BorderLayout(8,8)); setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        var rows = new JPanel(); rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        var text = new StringBuilder();
        for (var item : plan.items()) {
            var row = new JPanel(new BorderLayout(8,4));
            row.add(ThemePicker.literal(item.conversion().document().name() + " — " + item.conversion().sourceEntry()),BorderLayout.CENTER);
            var choice = new JComboBox<>(item.target().isPresent() ? ThemeImporter.ConflictChoice.values()
                : new ThemeImporter.ConflictChoice[]{ThemeImporter.ConflictChoice.KEEP_BOTH,ThemeImporter.ConflictChoice.SKIP});
            choice.setSelectedItem(item.target().isPresent() ? ThemeImporter.ConflictChoice.SKIP : ThemeImporter.ConflictChoice.KEEP_BOTH);
            choice.setRenderer(new DefaultListCellRenderer(){@Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                super.getListCellRendererComponent(list,value,index,selected,focus); setText(switch((ThemeImporter.ConflictChoice)value){case REPLACE -> "Replace";case KEEP_BOTH -> "Keep both";case SKIP -> "Skip";});return this;}});
            choice.getAccessibleContext().setAccessibleName(item.conversion().document().name()+" conflict choice");
            decisions.put(item.id(),choice); row.add(choice,BorderLayout.EAST); rows.add(row);
            for(var warning:item.conversion().warnings())text.append(item.conversion().sourceEntry()).append(": ").append(warning).append('\n');
            if(item.shadowsBundled())text.append(item.conversion().document().name()).append(": overrides a bundled theme\n");
        }
        for(var failure:plan.failures())text.append(failure.sourceEntry()).append(": ").append(failure.message()).append('\n');
        details.setText(text.toString()); details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        rows.add(details); var scroll=new JScrollPane(rows);scroll.setPreferredSize(new Dimension(600,360));add(scroll);
        var buttons=new JPanel(new FlowLayout(FlowLayout.TRAILING));var back=new JButton("Cancel");buttons.add(back);buttons.add(commit);add(buttons,BorderLayout.SOUTH);
        commit.setEnabled(!plan.items().isEmpty());commit.addActionListener(e -> {commit.setEnabled(false);accept.accept(choices());});back.addActionListener(e -> cancel.run());
    }
    Map<String,ThemeImporter.ConflictChoice> choices(){var values=new LinkedHashMap<String,ThemeImporter.ConflictChoice>();decisions.forEach((id,box)->values.put(id,(ThemeImporter.ConflictChoice)box.getSelectedItem()));return Map.copyOf(values);}
}
