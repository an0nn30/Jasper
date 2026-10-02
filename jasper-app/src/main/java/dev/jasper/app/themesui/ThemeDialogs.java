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
    void confirm(String title,String message,String label,Runnable yes,Runnable cancel){
        view(title,(accept,reject)->panel(message,null,label,()->{accept.run();yes.run();},reject),cancel);
    }
    void name(String proposed,Predicate<String> valid,Consumer<String> acceptName,Runnable cancel){
        view("Duplicate theme",(accept,reject)->{
            var field=new JTextField(proposed,32);field.getAccessibleContext().setAccessibleName("New theme name");
            var error=ThemePicker.literal(" ");var input=new JPanel(new BorderLayout(0,6));input.add(field);input.add(error,BorderLayout.SOUTH);
            return panel("Choose a unique theme name. The copy inherits from the original.",input,"Duplicate",()->{
                String name=field.getText().strip();if(!valid.test(name)){error.setText("Enter a unique name other than match.");return;}accept.run();acceptName.accept(name);
            },reject);
        },cancel);
    }
    void message(String title,String text){view(title,(accept,reject)->panel(text,null,"Close",accept,reject),()->{});}
    private static JPanel panel(String text,JComponent input,String label,Runnable accept,Runnable cancel){
        var panel=new JPanel(new BorderLayout(8,12));panel.setBorder(BorderFactory.createEmptyBorder(16,16,12,16));
        var body=new JTextArea(text);body.setEditable(false);body.setLineWrap(true);body.setWrapStyleWord(true);body.setColumns(52);body.setRows(Math.min(20,Math.max(3,text.split("\n").length)));
        panel.add(new JScrollPane(body),BorderLayout.CENTER);if(input!=null)panel.add(input,BorderLayout.NORTH);
        var buttons=new JPanel(new FlowLayout(FlowLayout.TRAILING));var back=new JButton("Cancel");var ok=new JButton(label);buttons.add(back);buttons.add(ok);panel.add(buttons,BorderLayout.SOUTH);
        back.addActionListener(e->cancel.run());ok.addActionListener(e->accept.run());return panel;
    }
    @Override public void close(){for(var surface:List.copyOf(open))surface.close();}
}
