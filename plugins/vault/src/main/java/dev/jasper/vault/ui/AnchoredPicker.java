package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.*;
import dev.jasper.vault.api.*;
import dev.jasper.vault.service.AnchoredPickPrompt;
import java.util.*;

/** Vault producer: popover -> owned real editor -> same pending picker; creation never selects or grants. */
public final class AnchoredPicker {
    public static final String MENU="credential.menu",NONE="credential.none",CREATE="credential.create",CANCEL="credential.cancel";
    private final PluginContext context;
    private final VaultManager manager;
    private final AnchoredPickPrompt prompt;
    private final EntryPresenter editors;
    private PluginPopover popup;
    private UiView view;
    private String query="",active="";
    private boolean creating;
    public AnchoredPicker(PluginContext context,VaultManager manager,AnchoredPickPrompt prompt){
        this.context=context;this.manager=manager;this.prompt=prompt;editors=new EntryPresenter(context);
        prompt.onDismiss(()->{editors.close();if(popup!=null)popup.close();});
    }
    public void show(){
        if(prompt.settled()||!prompt.request().anchor().isOpen())return;
        var shown=context.windows().popover(prompt.request().anchor());popup=shown;
        view=context.kit().create(new ViewSpec(menu()),this::handle);shown.setContent(view);
        shown.onClosed(()->{if(popup==shown)popup=null;if(!creating&&!prompt.settled())prompt.cancel();});
        shown.show();view.focus(MENU);
    }
    private Nodes.PickerMenu menu(){
        var items=new ArrayList<Nodes.PickerItem>();
        if(query.isBlank()&&prompt.request().allowClear())items.add(new Nodes.PickerItem(NONE,"None","","none",Nodes.Hue.NONE,Nodes.PickerItemKind.UTILITY));
        var needle=query.toLowerCase(Locale.ROOT);
        for(var d:prompt.choices())if((d.name()+" "+CredentialPresentation.caption(d)).toLowerCase(Locale.ROOT).contains(needle))items.add(CredentialPresentation.item(d));
        if(items.stream().noneMatch(i->i.id().equals(active))&&!active.equals(CREATE))active=items.isEmpty()?CREATE:items.getFirst().id();
        return new Nodes.PickerMenu(MENU,ControlState.READY,query,"Search credentials",items,
            prompt.request().current().map(UUID::toString).orElse(""),active,"No credentials match “"+query+"”","New Credential…",CREATE,CANCEL);
    }
    private void handle(UiEvent event){
        if(prompt.settled()||creating)return;
        switch(event){
            case UiEvent.Edited e when e.id().equals(MENU)->{query=e.text();active="";view.update(new ViewSpec(menu()));}
            case UiEvent.Selected e when e.id().equals(MENU)->active=e.ids().stream().findFirst().orElse("");
            case UiEvent.Action e when e.id().equals(CANCEL)->prompt.cancel();
            case UiEvent.Action e when e.id().equals(NONE)->prompt.clear();
            case UiEvent.Action e when e.id().equals(CREATE)->create();
            case UiEvent.Action e->{try{prompt.choose(UUID.fromString(e.id()));}catch(IllegalArgumentException ignored){}}
            default->{}
        }
    }
    private void create(){
        creating=true;popup.close();
        if(prompt.settled()||!prompt.request().anchor().isOpen()){creating=false;return;}
        editors.create(prompt.request().anchor(),manager,()->{
            creating=false;if(!prompt.settled()&&prompt.request().anchor().isOpen())show();
        });
    }
}
