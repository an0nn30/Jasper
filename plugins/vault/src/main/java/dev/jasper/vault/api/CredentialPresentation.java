package dev.jasper.vault.api;
import dev.jasper.sdk.ui.Nodes;
/** Shared, grant-free credential metadata projection for compact/default consumers and the Vault-owned menu. */
public final class CredentialPresentation {
 private CredentialPresentation(){}
 public static Nodes.PickerFace face(CredentialDescriptor d,Nodes.PickerDensity density){return d==null?new Nodes.PickerFace("","","lock",Nodes.Hue.NONE,density):new Nodes.PickerFace(d.name(),caption(d),icon(d),hue(d),density);}
 public static Nodes.PickerItem item(CredentialDescriptor d){return new Nodes.PickerItem(d.id().toString(),d.name(),caption(d),icon(d),hue(d));}
 private static String icon(CredentialDescriptor d){return d.kind()==Kind.ACCOUNT_PASSWORD?"user":"key";}
 private static Nodes.Hue hue(CredentialDescriptor d){return d.kind()==Kind.ACCOUNT_PASSWORD?Nodes.Hue.BLUE:Nodes.Hue.GREEN;}
 public static String caption(CredentialDescriptor d){String kind=switch(d.kind()){case ACCOUNT_PASSWORD->"Password";case ACCOUNT_KEY->"Account key";case ACCOUNT_KEY_AND_PASSWORD->"Key and password";case SSH_KEY->d.managedKey()?"Stored key":"Path key";};return d.subtitle().isBlank()?kind:kind+" · "+d.subtitle();}
}
