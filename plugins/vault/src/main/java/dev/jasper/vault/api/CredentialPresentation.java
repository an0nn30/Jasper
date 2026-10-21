package dev.jasper.vault.api;
import dev.jasper.sdk.ui.Nodes;
/**
 * How a credential looks in a picker, the same in a consumer's own controls as in Vault's chooser: its name, a caption,
 * the {@code user} icon in blue for a password account and the {@code key} icon in green for every other kind. It
 * reads only the descriptor and grants nothing.
 */
public final class CredentialPresentation {
 private CredentialPresentation(){}
 /**
  * The face of a {@link Nodes.ChoiceKind#PICKER} choice showing a credential.
  *
  * @param d the credential, or null for none
  * @param density the picker's density
  * @return the credential's name, caption, icon and hue; for null, the placeholder face with the {@code lock} icon
  */
 public static Nodes.PickerFace face(CredentialDescriptor d,Nodes.PickerDensity density){return d==null?new Nodes.PickerFace("","","lock",Nodes.Hue.NONE,density):new Nodes.PickerFace(d.name(),caption(d),icon(d),hue(d),density);}
 /**
  * A credential's row in a picker menu, as Vault's chooser lists it.
  *
  * @param d the credential
  * @return a row whose id is the credential's id as a string, with its name, caption, icon and hue
  */
 public static Nodes.PickerItem item(CredentialDescriptor d){return new Nodes.PickerItem(d.id().toString(),d.name(),caption(d),icon(d),hue(d));}
 private static String icon(CredentialDescriptor d){return d.kind()==Kind.ACCOUNT_PASSWORD?"user":"key";}
 private static Nodes.Hue hue(CredentialDescriptor d){return d.kind()==Kind.ACCOUNT_PASSWORD?Nodes.Hue.BLUE:Nodes.Hue.GREEN;}
 /**
  * A credential's caption: its kind as {@code Password}, {@code Account key}, {@code Key and password},
  * {@code Stored key} (an SSH key stored in Vault) or {@code Path key} (a key file Vault refers to), then
  * {@code " · "} and the subtitle when the subtitle is not blank.
  *
  * @param d the credential
  * @return the caption, for example {@code Password · deploy}
  */
 public static String caption(CredentialDescriptor d){String kind=switch(d.kind()){case ACCOUNT_PASSWORD->"Password";case ACCOUNT_KEY->"Account key";case ACCOUNT_KEY_AND_PASSWORD->"Key and password";case SSH_KEY->d.managedKey()?"Stored key":"Path key";};return d.subtitle().isBlank()?kind:kind+" · "+d.subtitle();}
}
