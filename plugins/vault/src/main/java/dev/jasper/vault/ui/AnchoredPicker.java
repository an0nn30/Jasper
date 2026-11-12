package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.*;
import dev.jasper.vault.api.*;
import dev.jasper.vault.service.AnchoredPickPrompt;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Vault producer: popover -> owned real editor -> same pending picker; creation never selects or grants. A choice (a
 * credential's row, None or New Credential…, pressed or entered) that comes within {@link #SETTLE_NANOS} of the popover
 * showing, each time it shows, is ignored and the picker stays open, so a press meant for what was under it, as a
 * double-click's second press, chooses nothing (Browser plan 8's L20); Cancel, Escape and the search are never ignored. A
 * chooser opened with a starting search, a page's, has no active row until the user moves to one, and typing leaves none
 * again, so Enter on what the user types chooses nothing and only the row the user moved to can be chosen (L36); without
 * one the first row is active.
 */
public final class AnchoredPicker {
    public static final String MENU="credential.menu",NONE="credential.none",CREATE="credential.create",CANCEL="credential.cancel";
    /** How long after the popover shows a choice is ignored: 500 ms. */
    public static final long SETTLE_NANOS=500_000_000L;
    private final PluginContext context;
    private final VaultManager manager;
    private final AnchoredPickPrompt prompt;
    private final EntryPresenter editors;
    private PluginPopover popup;
    private UiView view;
    private String query,active="";
    private boolean creating;
    /** Whether the request had a starting search, when no row is active until the user moves to one. */
    private final boolean searched;
    // The clock a choice is timed by, in nanoseconds, and when the popover last showed on it.
    private final LongSupplier clock;
    private long shownAt;
    /** The picker of {@code prompt}, timing a choice by {@code clock}, in nanoseconds: {@code System::nanoTime}, or a test's. */
    public AnchoredPicker(PluginContext context,VaultManager manager,AnchoredPickPrompt prompt,LongSupplier clock){
        this.context=context;this.manager=manager;this.prompt=prompt;this.clock=Objects.requireNonNull(clock,"clock");editors=new EntryPresenter(context);
        query=prompt.request().search();searched=!query.isBlank();
        prompt.onDismiss(()->{editors.close();if(popup!=null)popup.close();});
    }
    public void show(){
        if(prompt.settled()||!prompt.request().anchor().isOpen())return;
        var shown=context.windows().popover(prompt.request().anchor());popup=shown;
        view=context.kit().create(new ViewSpec(menu()),this::handle);shown.setContent(view);
        shown.onClosed(()->{if(popup==shown)popup=null;if(!creating&&!prompt.settled())prompt.cancel();});
        shown.show();shownAt=clock.getAsLong();view.focus(MENU);
    }
    private Nodes.PickerMenu menu(){
        var items=new ArrayList<Nodes.PickerItem>();
        if(query.isBlank()&&prompt.request().allowClear())items.add(new Nodes.PickerItem(NONE,"None","","none",Nodes.Hue.NONE,Nodes.PickerItemKind.UTILITY));
        var needle=query.toLowerCase(Locale.ROOT);
        for(var d:prompt.choices())if((d.name()+" "+CredentialPresentation.caption(d)).toLowerCase(Locale.ROOT).contains(needle))items.add(CredentialPresentation.item(d));
        if(items.stream().noneMatch(i->i.id().equals(active))&&!active.equals(CREATE))active=searched?"":items.isEmpty()?CREATE:items.getFirst().id();
        return new Nodes.PickerMenu(MENU,ControlState.READY,query,"Search credentials",items,
            prompt.request().current().map(UUID::toString).orElse(""),active,"No credentials match “"+query+"”","New Credential…",CREATE,CANCEL);
    }
    private void handle(UiEvent event){
        if(prompt.settled()||creating)return;
        switch(event){
            case UiEvent.Edited e when e.id().equals(MENU)->{query=e.text();active="";view.update(new ViewSpec(menu()));}
            // The menu shows the row the user moved to, so the kit, which applies an active row when it changes, drops it
            // when typing resets it.
            case UiEvent.Selected e when e.id().equals(MENU)->{active=e.ids().stream().findFirst().orElse("");view.update(new ViewSpec(menu()));}
            case UiEvent.Action e when e.id().equals(CANCEL)->prompt.cancel();
            case UiEvent.Action _ when settling()->{}
            case UiEvent.Action e when searched&&!e.id().equals(active)->{}
            case UiEvent.Action e when e.id().equals(NONE)->prompt.clear();
            case UiEvent.Action e when e.id().equals(CREATE)->create();
            case UiEvent.Action e->{try{prompt.choose(UUID.fromString(e.id()));}catch(IllegalArgumentException ignored){}}
            default->{}
        }
    }
    /** Whether the popover showed less than {@link #SETTLE_NANOS} ago, when a choice is ignored. */
    private boolean settling(){return clock.getAsLong()-shownAt<SETTLE_NANOS;}
    private void create(){
        creating=true;popup.close();
        if(prompt.settled()||!prompt.request().anchor().isOpen()){creating=false;return;}
        editors.create(prompt.request().anchor(),manager,()->{
            creating=false;if(!prompt.settled()&&prompt.request().anchor().isOpen())show();
        });
    }
}
