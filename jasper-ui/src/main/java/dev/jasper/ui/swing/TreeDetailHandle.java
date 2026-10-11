package dev.jasper.ui.swing;

import dev.jasper.ui.model.ViewSpec;
import java.util.Objects;

/** One tree-owned detail generation. Closing an old handle never affects a later attachment. EDT only. */
public final class TreeDetailHandle implements AutoCloseable {
    final Trees.TreeControl owner;
    final String itemId;
    final KitView body;
    final Runnable detached;
    boolean attached=true;
    TreeDetailHandle(Trees.TreeControl owner,String itemId,KitView body,Runnable detached){this.owner=owner;this.itemId=itemId;this.body=body;this.detached=detached;}
    public void update(ViewSpec spec){requireAttached();Trees.validateDetail(Objects.requireNonNull(spec).root());body.update(spec);owner.detailChanged();}
    public void focus(String nodeId){requireAttached();reveal();body.focus(nodeId);}
    public void reveal(){requireAttached();int index=owner.indexOf(itemId);owner.canvas.scrollRectToVisible(new java.awt.Rectangle(0,owner.top(index),owner.canvas.getWidth(),owner.heightAt(index)));}
    public boolean isAttached(){return attached;}
    private void requireAttached(){KitView.requireEdt();if(!attached)throw new IllegalStateException("Tree detail detached");}
    @Override public void close(){KitView.requireEdt();if(attached)owner.detach(this);}
}
