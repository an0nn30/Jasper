package dev.jasper.app.themesui;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javax.swing.*;

/** Concrete, owner-bound dialogs. All text is literal; all methods EDT only. */
final class ThemeDialogs implements AutoCloseable {
    private final AuxiliaryWindows windows;
    private final Supplier<AuxiliarySurface> owner;
    private final List<AuxiliarySurface> open=new ArrayList<>();
    ThemeDialogs(AuxiliaryWindows windows,Supplier<AuxiliarySurface> owner){this.windows=windows;this.owner=owner;}
    boolean available(){var parent=owner.get();return parent!=null&&!parent.closed()&&windows.open().contains(parent);}
    void view(String title,BiFunction<Runnable,Runnable,JComponent> contents,Runnable cancel){
        if(!available()){cancel.run();return;}
        var surface=windows.dialog(title,true,owner.get());open.add(surface);var answered=new AtomicBoolean();
        Runnable accept=()->{answered.set(true);surface.close();};
        Runnable reject=()->surface.close();
        surface.onClosed(()->{open.remove(surface);if(answered.compareAndSet(false,true))cancel.run();});
        surface.setContent(contents.apply(accept,reject));surface.show();
    }
    /** Shows literal {@code text} with a Close button. */
    void message(String title,String text){view(title,(accept,reject)->panel(text,accept),()->{});}
    private static JPanel panel(String text,Runnable close){
        var panel=new JPanel(new BorderLayout(8,12));panel.setBorder(BorderFactory.createEmptyBorder(16,16,12,16));
        var body=new JTextArea(text);body.setEditable(false);body.setLineWrap(true);body.setWrapStyleWord(true);body.setColumns(52);body.setRows(Math.min(20,Math.max(3,text.split("\n").length)));
        panel.add(new JScrollPane(body),BorderLayout.CENTER);
        var buttons=new JPanel(new FlowLayout(FlowLayout.TRAILING));var ok=new JButton("Close");buttons.add(ok);panel.add(buttons,BorderLayout.SOUTH);
        ok.addActionListener(e->close.run());return panel;
    }
    @Override public void close(){for(var surface:List.copyOf(open))surface.close();}
}
