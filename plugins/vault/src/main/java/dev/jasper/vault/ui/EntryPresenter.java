package dev.jasper.vault.ui;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.*;
import dev.jasper.vault.workstation.Layout;
import java.util.*;
import java.util.function.Function;

/** Vault-private native editor ownership, file chooser wiring and close-time secret cleanup. */
final class EntryPresenter implements AutoCloseable {
    private final PluginContext context;
    private final Map<PluginDialog,EditorForm> forms=new IdentityHashMap<>();
    private final List<PluginDialog> selectors=new ArrayList<>();
    private final Layout layout;
    EntryPresenter(PluginContext context){this(context,Layout.ROWS);}
    /** Editors in {@code layout}'s composition: under CLASSIC each sheet takes its form's layout and its kind's swatch (plan 10b). */
    EntryPresenter(PluginContext context,Layout layout){this.context=context;this.layout=layout;}
    void show(WindowOwner owner,String title,Function<Runnable,? extends EditorForm> factory,Runnable finished){show(owner,title,Nodes.Hue.NONE,factory,finished);}
    void show(WindowOwner owner,String title,Nodes.Hue hue,Function<Runnable,? extends EditorForm> factory,Runnable finished){
        var dialog=context.windows().dialog(new DialogSpec(title,owner,true,DialogSpec.Presentation.ATTACHED_SHEET,layout==Layout.CLASSIC?hue:Nodes.Hue.NONE));
        EditorForm form;
        try{form=factory.apply(dialog::close);}
        catch(RuntimeException failure){dialog.close();finished.run();throw failure;}
        try{form.layout(layout);form.setTitle(title);form.setFileChooser(dialog::chooseFile);dialog.setContent(form.view());}
        catch(RuntimeException failure){form.close();dialog.close();context.notifications().group().error("Could not open the form",title+": "+EditorForm.message(failure),failure);finished.run();return;}
        forms.put(dialog,form);
        dialog.onClosed(()->{form.close();forms.remove(dialog);finished.run();});dialog.show();
    }
    void create(WindowOwner owner,VaultManager manager,Runnable finished){
        var dialog=context.windows().dialog(new DialogSpec("New Credential",owner,true,DialogSpec.Presentation.ATTACHED_SHEET));selectors.add(dialog);String[] selected={""};
        var actions=List.of(new Nodes.Button("create.login",ControlState.READY,"Login","user",Nodes.ButtonKind.SECONDARY,"create.login",""),
            new Nodes.Button("create.key",ControlState.READY,"SSH path key","key",Nodes.ButtonKind.SECONDARY,"create.key",""),
            new Nodes.Button("create.cancel",ControlState.READY,"Cancel","",Nodes.ButtonKind.SECONDARY,"create.cancel",""));
        var view=context.kit().create(new ViewSpec(new Nodes.Form("create",ControlState.READY,Nodes.FormKind.SHEET,"New Credential",Optional.empty(),List.of(
            new Nodes.Text("create.help",ControlState.READY,"Create a login or reference an existing SSH key file. A path key does not import key material into Vault.",Nodes.TextRole.DESCRIPTION)),Optional.empty(),actions,"create.cancel")),event->{if(event instanceof UiEvent.Action action){selected[0]=action.id();dialog.close();}});
        dialog.setContent(view);dialog.onClosed(()->{selectors.remove(dialog);if(owner instanceof UiAnchor anchor&&!anchor.isOpen()){finished.run();return;}switch(selected[0]){
            case "create.login"->show(owner,"Add Login",Nodes.Hue.BLUE,close->EntryEditor.login(context.kit(),null,manager::saveAccount,close),finished);
            case "create.key"->show(owner,"Add SSH Key",Nodes.Hue.GREEN,close->EntryEditor.key(context.kit(),null,manager::importKey,close),finished);
            default->finished.run();
        }});dialog.show();
    }
    @Override public void close(){for(var dialog:List.copyOf(selectors))dialog.close();for(var dialog:List.copyOf(forms.keySet()))dialog.close();}
}
