package dev.jasper.ui.icons;

import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/** One bounded decode, with no resource lookup or raster allocation in receiving paint. */
final class DecodedArtwork {
    private static final Pattern URL=Pattern.compile("url\\s*\\(([^)]*)\\)",Pattern.CASE_INSENSITIVE);
    final BufferedImage image;
    private final double aspect;
    private final com.github.weisj.jsvg.SVGDocument vector;
    private final java.util.Map<Integer,java.awt.Image> snapshots=new java.util.LinkedHashMap<>();
    DecodedArtwork(EncodedArtwork artwork) {
        try {
            byte[] data=artwork.data();
            if(artwork.format()==EncodedArtwork.Format.PNG) {
                vector=null;
                try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                    var readers=ImageIO.getImageReaders(input);
                    if(!readers.hasNext())throw new IOException("Not an image");
                    var reader=readers.next();
                    try {
                        if(!reader.getFormatName().equalsIgnoreCase("png"))throw new IOException("Not PNG");
                        reader.setInput(input); dimensions(reader.getWidth(0),reader.getHeight(0));
                        image=reader.read(0);
                        aspect=(double)image.getWidth()/image.getHeight();
                    } finally {reader.dispose();}
                }
            } else {
                preflight(data);
                var context=LoaderContext.builder().externalResourcePolicy(ResourcePolicy.DENY_ALL)
                    .resourceLoader((document,uri)->{throw new IOException("External artwork resource denied");}).build();
                var document=new SVGLoader().load(new ByteArrayInputStream(data),null,context);
                if(document==null)throw new IOException("Invalid SVG");
                vector=document;
                var size=document.size(); dimensions(size.width,size.height);
                aspect=(double)size.width/size.height;
                // Bounded validation thumbnail; intrinsic aspect is retained independently.
                // Receiving snapshots render directly from the vector, never from this thumbnail.
                double factor=64/Math.max(size.width,size.height);
                int width=Math.max(1,(int)Math.ceil(size.width*factor)), height=Math.max(1,(int)Math.ceil(size.height*factor));
                image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                var g=image.createGraphics();
                try {g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);document.render(null,g,new ViewBox(0,0,width,height));}
                finally {g.dispose();}
            }
        } catch(Exception failure) {throw new IllegalArgumentException("Invalid or unsafe encoded artwork",failure);}
    }
    /** Prepared receiving snapshots, never created from paint. SVG uses the actual device coverage. */
    synchronized java.awt.Image snapshot(int extent) {
        var cached=snapshots.get(extent); if(cached!=null)return cached;
        int width=aspect>=1?extent:Math.max(1,(int)Math.round(extent*aspect));
        int height=aspect<=1?extent:Math.max(1,(int)Math.round(extent/aspect));
        var variants=new java.awt.Image[3];
        for(int scale=1;scale<=3;scale++) {
            int w=width*scale,h=height*scale;
            var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
            var g=result.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if(vector!=null)vector.render(null,g,new ViewBox(0,0,w,h));
                else {
                    // Progressive reduction integrates coverage before the final fractional reduction.
                    BufferedImage source=image;
                    while(source.getWidth()/2>=w && source.getHeight()/2>=h) {
                        var reduced=new BufferedImage(Math.max(w,source.getWidth()/2),Math.max(h,source.getHeight()/2),BufferedImage.TYPE_INT_ARGB);
                        var down=reduced.createGraphics();
                        try {down.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);down.drawImage(source,0,0,reduced.getWidth(),reduced.getHeight(),null);}finally{down.dispose();}
                        source=reduced;
                    }
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(source,0,0,w,h,null);
                }
            }finally{g.dispose();}
            variants[scale-1]=result;
        }
        var result=new java.awt.image.BaseMultiResolutionImage(variants);
        if(snapshots.size()==8)snapshots.remove(snapshots.keySet().iterator().next());
        snapshots.put(extent,result);return result;
    }
    /** Flattens already-composited alpha at every prepared density, outside paint; no derived global cache. */
    static java.awt.Image foreground(java.awt.Image snapshot, java.awt.Color color) {
        var sources=((java.awt.image.MultiResolutionImage)snapshot).getResolutionVariants();
        var variants=new java.awt.Image[sources.size()];
        for(int n=0;n<sources.size();n++) {
            var source=(BufferedImage)sources.get(n);
            int width=source.getWidth(),height=source.getHeight();
            int[] pixels=source.getRGB(0,0,width,height,null,0,width);
            for(int i=0;i<pixels.length;i++)pixels[i]=(pixels[i]&0xff000000)|(color.getRGB()&0xffffff);
            var result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            result.setRGB(0,0,width,height,pixels,0,width);variants[n]=result;
        }
        return new java.awt.image.BaseMultiResolutionImage(variants);
    }

    private static void dimensions(double width,double height)throws IOException {
        if(!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0||width>4096||height>4096||width*height>4_194_304)throw new IOException("Artwork dimensions exceed limits");
    }
    private static void preflight(byte[] data)throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var builder=factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}});
        var document=builder.parse(new ByteArrayInputStream(data));
        if(!"svg".equals(document.getDocumentElement().getLocalName()))throw new IOException("Not SVG");
        inspect(document,0);
    }
    private static void inspect(Node node,int depth)throws IOException {
        if(depth>128)throw new IOException("Artwork nesting exceeds limit");
        if(node.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE)throw new IOException("Processing instruction denied");
        String name=node.getLocalName();
        if(name!=null && java.util.Set.of("script","foreignobject","animate","animatetransform","set").contains(name.toLowerCase(Locale.ROOT)))throw new IOException("Active SVG denied");
        var attributes=node.getAttributes();
        if(attributes!=null)for(int i=0;i<attributes.getLength();i++) {
            var attribute=attributes.item(i);String key=attribute.getLocalName();String value=attribute.getNodeValue().trim();
            if(key!=null && (key.toLowerCase(Locale.ROOT).startsWith("on") || key.equals("base")))throw new IOException("Active SVG attribute denied");
            if("href".equals(key) && !value.matches("#[A-Za-z_][A-Za-z0-9_.:-]*"))throw new IOException("Nonlocal resource denied");
            css(value);
        }
        if("style".equals(name))css(node.getTextContent());
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())inspect(child,depth+1);
    }
    private static void css(String value)throws IOException {
        if(value.indexOf('\\')>=0 || value.indexOf('@')>=0)throw new IOException("External or escaped CSS denied");
        var urls=URL.matcher(value);
        while(urls.find())if(!urls.group(1).trim().replace("\"","").replace("'","").matches("#[A-Za-z_][A-Za-z0-9_.:-]*"))throw new IOException("Nonlocal paint resource denied");
    }
}
