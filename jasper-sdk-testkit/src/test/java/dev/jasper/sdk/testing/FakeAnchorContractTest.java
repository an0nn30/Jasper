package dev.jasper.sdk.testing;
import java.util.*;
class FakeAnchorContractTest extends AnchorContractTest {
 protected Fixture fixture(){return new Fixture(){
  final FakePluginHost host=new FakePluginHost();
  public Endpoint endpoint(String id){var c=host.start(new dev.jasper.sdk.PluginInfo(id,id,"1.0",Set.of()),Set.of(),Set.of(),ctx->{});return new Endpoint(c.kit(),c.windows(),()->{c.ui.closeAll();c.kit().close();});}
  public void close(){host.close();}
 };}
}
