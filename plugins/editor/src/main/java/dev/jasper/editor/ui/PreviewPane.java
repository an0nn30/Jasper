package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownResult;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** EDT-owned inert document surface. Preparation belongs to the controller's existing render worker. */
final class PreviewPane implements AutoCloseable {
    private final JEditorPane editor = new Surface();
    private final Runnable geometryChanged;
    Runnable beforeReplace = () -> {};
    private final IntConsumer activateLink;
    private final Map<Integer, BufferedImage> images = new HashMap<>();
    private final Map<Integer, String> imageReasons = new HashMap<>();
    private Set<Integer> imageIds = Set.of();
    private final Set<Integer> changedImages = new HashSet<>();
    private boolean imageNotificationPending;
    private long imageNotificationGeneration;
    private Map<String,Integer> anchors = Map.of();
    private MarkdownResult installed;
    private Prepared pending;
    private Style pendingStyle;
    private RuntimeException installFailure;
    private record Style(TerminalTypography typography, WorkspacePalette palette, SyntaxPalette syntax, Font body, double opacity) {}
    private PreviewKit kit;
    private long generation;
    private boolean closed;
    private final List<Runnable> installedListeners = new ArrayList<>();
    boolean closed() { return closed; }
    void onInstalled(Runnable listener) { installedListeners.add(listener); }
    void removeInstalled(Runnable listener) { installedListeners.remove(listener); }

    private WorkspacePalette palette;
    private TerminalTypography typography;
    private SyntaxPalette syntax;
    private Font bodyFont;
    private double opacity=1;

    PreviewPane(IntConsumer activateLink, Runnable geometryChanged) {
        edt();this.activateLink=Objects.requireNonNull(activateLink);this.geometryChanged=Objects.requireNonNull(geometryChanged);
        editor.setEditable(false);editor.setOpaque(false);editor.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));
        editor.addComponentListener(new java.awt.event.ComponentAdapter(){
            @Override public void componentResized(java.awt.event.ComponentEvent event){if(!closed){if(kit!=null)kit.imagesChanged();geometryChanged.run();}}
        });
        editor.setFocusTraversalKeysEnabled(false);
        bind("TAB", "preview.next-link", () -> nextLink(true));
        bind("shift TAB", "preview.previous-link", () -> nextLink(false));
        bind("ENTER", "preview.activate-link", this::activateSelectedLink);
        editor.addHyperlinkListener(event -> {
            if(event.getEventType()==HyperlinkEvent.EventType.ACTIVATED) activate(event.getDescription());
        });
    }
    private void bind(String stroke, String id, Runnable action) {
        editor.getInputMap().put(KeyStroke.getKeyStroke(stroke), id);
        editor.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }
    private void activate(String description) {
        if (closed || installed == null) return;
        int id = resourceId(description, "jasper-link:");
        if (installed.links().stream().anyMatch(link -> link.id() == id)) activateLink.accept(id);
    }
    private List<Element> linkElements() {
        var found = new ArrayList<Element>();
        if (!(editor.getDocument() instanceof HTMLDocument document)) return found;
        var iterator = new ElementIterator(document);
        for (Element e=iterator.first();e!=null;e=iterator.next())
            if (e.isLeaf() && e.getAttributes().getAttribute(HTML.Tag.A) instanceof AttributeSet attrs
                && resourceId((String)attrs.getAttribute(HTML.Attribute.HREF), "jasper-link:") >= 0) found.add(e);
        return found;
    }
    void nextLink(boolean forward) {
        var links = linkElements();
        if (links.isEmpty()) return;
        Element selected = null;
        if (forward) {
            for (var e : links) if (e.getStartOffset() >= editor.getSelectionEnd()) { selected=e;break; }
            if (selected == null) selected=links.getFirst();
        } else {
            for (var e : links) if (e.getEndOffset() <= editor.getSelectionStart()) selected=e;
            if (selected == null) selected=links.getLast();
        }
        editor.select(selected.getStartOffset(),Math.min(editor.getDocument().getLength(),selected.getEndOffset()));
    }
    void activateSelectedLink() {
        for (var e : linkElements()) if (editor.getSelectionStart() >= e.getStartOffset() && editor.getSelectionStart() < e.getEndOffset()) {
            var attrs=(AttributeSet)e.getAttributes().getAttribute(HTML.Tag.A);
            activate((String)attrs.getAttribute(HTML.Attribute.HREF));return;
        }
    }
    JEditorPane editor() { return editor; }
    OptionalInt anchor(String name) { edt();Integer offset=anchors.get(name);return offset==null?OptionalInt.empty():OptionalInt.of(offset); }
    Optional<RuntimeException> installFailure() { edt();return Optional.ofNullable(installFailure); }
    boolean installing() { edt();return pending!=null; }
    Optional<MarkdownResult> installedResult() { edt();return Optional.ofNullable(installed); }
    void image(int id, BufferedImage image) {
        edt(); if (closed || !imageIds.contains(id)) return;
        var previous = image == null ? images.remove(id) : images.put(id, image);
        if (previous != image) { changedImages.add(id); imageNotification(); }
    }
    void imageReason(int id, String reason) {
        edt(); if (closed || !imageIds.contains(id)) return;
        if (!Objects.equals(imageReasons.put(id, reason), reason)) imageNotification();
    }
    void images(dev.jasper.editor.markdown.resources.ResourceSession.Update update) {
        edt(); if (closed) return;
        if (update.clear()) {
            changedImages.addAll(images.keySet());
            boolean repaint = !images.isEmpty() || !imageReasons.isEmpty();
            images.clear(); imageReasons.clear();
            if (repaint) imageNotification();
        }
        update.images().forEach((id, state) -> { image(id, state.image()); imageReason(id, state.reason()); });
    }
    private void imageNotification() {
        if (imageNotificationPending) return;
        imageNotificationPending = true;
        long token = imageNotificationGeneration;
        SwingUtilities.invokeLater(() -> {
            if (closed || token != imageNotificationGeneration) return;
            imageNotificationPending = false;
            if (!changedImages.isEmpty()) {
                var changed = Set.copyOf(changedImages); changedImages.clear();
                if (kit != null) kit.imagesChanged(changed);
                editor.revalidate(); geometryChanged.run();
            }
            editor.repaint();
        });
    }
    void cancelInstall() { edt();generation++;pending=null;pendingStyle=null;installFailure=null; }

    /** Immutable parser output. No Swing component or document is created by preparation. */
    static final class Prepared {
        private final MarkdownResult result;
        private final List<Event> events;
        private final Set<Integer> imageIds;
        private Prepared(MarkdownResult result,List<Event> events,Set<Integer> imageIds){this.result=result;this.events=List.copyOf(events);this.imageIds=Set.copyOf(imageIds);}
        MarkdownResult result(){return result;}
    }
    private record Event(int kind, HTML.Tag tag, Map<HTML.Attribute,String> attributes, String text, int position) {
        void deliver(HTMLEditorKit.ParserCallback reader) {
            var attrs=new SimpleAttributeSet();attributes.forEach(attrs::addAttribute);
            switch(kind){case 0 -> reader.handleStartTag(tag,attrs,position);case 1 -> reader.handleEndTag(tag,position);
                case 2 -> reader.handleSimpleTag(tag,attrs,position);case 3 -> reader.handleText(text.toCharArray(),position);case 4 -> ((PreviewKit.DocumentReader)reader).mark(text);default -> throw new AssertionError();}
        }
    }
    /** Run after MarkdownRenderer on its worker, never on the EDT. Interruption cancels preparation. */
    static Prepared prepare(MarkdownResult result) {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Prepare preview on the render worker");
        Objects.requireNonNull(result);
        if(Thread.currentThread().isInterrupted())throw new CancellationException();
        if(result.html().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new IllegalArgumentException("Preview HTML limit");
        var events=new ArrayList<Event>();
        Set<Integer> imageIds=new HashSet<>(),linkIds=new HashSet<>();
        result.images().forEach(image -> imageIds.add(image.id()));result.links().forEach(link -> linkIds.add(link.id()));
        Set<HTML.Tag> tags=Set.of(HTML.Tag.HTML,HTML.Tag.HEAD,HTML.Tag.BODY,HTML.Tag.H1,HTML.Tag.H2,HTML.Tag.H3,HTML.Tag.H4,HTML.Tag.H5,HTML.Tag.H6,
            HTML.Tag.P,HTML.Tag.IMPLIED,HTML.Tag.BLOCKQUOTE,HTML.Tag.UL,HTML.Tag.OL,HTML.Tag.LI,HTML.Tag.PRE,HTML.Tag.HR,HTML.Tag.CODE,
            HTML.Tag.EM,HTML.Tag.STRONG,HTML.Tag.STRIKE,HTML.Tag.BR,HTML.Tag.A,HTML.Tag.IMG,HTML.Tag.TABLE,HTML.Tag.TR,HTML.Tag.TH,HTML.Tag.TD,HTML.Tag.FONT,HTML.Tag.B,HTML.Tag.I);
        var callback=new HTMLEditorKit.ParserCallback(){
            boolean namedAnchor;
            void add(int kind,HTML.Tag tag,MutableAttributeSet attrs,int pos){
                if(cancelled() || !tags.contains(tag))return;
                if(tag==HTML.Tag.A && kind==0 && attrs.isDefined(HTML.Attribute.NAME)){
                    events.add(new Event(4,null,Map.of(),attrs.getAttribute(HTML.Attribute.NAME).toString(),pos));namedAnchor=true;return;
                }
                if(tag==HTML.Tag.A && kind==1 && namedAnchor){namedAnchor=false;return;}
                var safe=new HashMap<HTML.Attribute,String>();
                for(var key:List.of(HTML.Attribute.NAME,HTML.Attribute.HREF,HTML.Attribute.SRC,HTML.Attribute.ALT,HTML.Attribute.START,HTML.Attribute.ALIGN,HTML.Attribute.COLOR)) {
                    Object value=attrs==null?null:attrs.getAttribute(key);if(value!=null)safe.put(key,value.toString());
                }
                // Resource IDs must exist in metadata. No URL is ever delegated to Swing.
                if(tag==HTML.Tag.IMG){int id=resourceId(safe.get(HTML.Attribute.SRC),"jasper-image:");
                    safe.remove(HTML.Attribute.SRC);if(imageIds.contains(id))safe.put(HTML.Attribute.SRC,"jasper-image:"+id);}
                if(tag==HTML.Tag.A){int id=resourceId(safe.get(HTML.Attribute.HREF),"jasper-link:");
                    safe.remove(HTML.Attribute.HREF);if(linkIds.contains(id))safe.put(HTML.Attribute.HREF,"jasper-link:"+id);}
                events.add(new Event(kind,tag,Map.copyOf(safe),"",pos));
            }
            @Override public void handleStartTag(HTML.Tag tag,MutableAttributeSet a,int p){add(0,tag,a,p);}
            @Override public void handleEndTag(HTML.Tag tag,int p){add(1,tag,null,p);}
            @Override public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet a,int p){add(2,tag,a,p);}
            @Override public void handleText(char[] text,int p){
                for(int i=0;i<text.length;){if(cancelled())return;int end=Math.min(text.length,i+2048);
                    if(end<text.length && Character.isHighSurrogate(text[end-1]))end--;
                    events.add(new Event(3,null,Map.of(),new String(text,i,end-i),p+i));i=end;}
            }
            boolean cancelled(){return Thread.currentThread().isInterrupted();}
        };
        try {new ParserDelegator().parse(new StringReader(result.html()),callback,true);}
        catch(IOException impossible){throw new UncheckedIOException(impossible);}
        if(Thread.currentThread().isInterrupted())throw new CancellationException();
        return new Prepared(result,events,imageIds);
    }
    /** Starts a detached build. Completion publishes installedResult before invoking geometryChanged. */
    void install(Prepared prepared,TerminalTypography typography,WorkspacePalette palette,SyntaxPalette syntax,Font bodyFont,double opacity) {
        edt();if(closed)return;Objects.requireNonNull(prepared);Objects.requireNonNull(bodyFont);
        Objects.requireNonNull(typography);Objects.requireNonNull(palette);Objects.requireNonNull(syntax);
        if(!Double.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Opacity must be from 0 to 1");
        cancelInstall();pending=prepared;pendingStyle=new Style(typography,palette,syntax,bodyFont,opacity);long token=generation;
        var nextKit=new PreviewKit(typography,palette,bodyFont,images,imageReasons);
        var document=nextKit.createDefaultDocument();document.putProperty("IgnoreCharsetDirective",Boolean.TRUE);
        var reader=((HTMLDocument)document).getReader(0);
        Runnable[] continuation={null};int[] index={0};
        continuation[0]=() -> {
            if(closed || token!=generation)return;
            long deadline=System.nanoTime()+4_000_000;int count=0;
            try {
                while(index[0]<prepared.events.size() && count++<128 && System.nanoTime()<deadline)prepared.events.get(index[0]++).deliver(reader);
                if(index[0]<prepared.events.size()){SwingUtilities.invokeLater(continuation[0]);return;}
                reader.flush();
                if(closed || token!=generation)return;
                var nextAnchors=collectAnchors((HTMLDocument)document,prepared.result);
                var nextFont=bodyFont.deriveFont((float)typography.size());
                var previousKit=editor.getEditorKit();var previousDocument=editor.getDocument();
                try {beforeReplace.run();editor.setEditorKit(nextKit);editor.setDocument(document);}
                catch(RuntimeException failure){editor.setEditorKit(previousKit);editor.setDocument(previousDocument);throw failure;}
                this.typography=typography;this.palette=palette;this.syntax=syntax;this.bodyFont=bodyFont;this.opacity=opacity;
                images.clear();imageReasons.clear();imageIds=prepared.imageIds;changedImages.clear();imageNotificationGeneration++;imageNotificationPending=false;kit=nextKit;installed=prepared.result;pending=null;pendingStyle=null;
                editor.setForeground(palette.text());editor.setSelectionColor(palette.selection());editor.setSelectedTextColor(palette.selectionText());editor.setCaretColor(palette.text());
                editor.setFont(nextFont);anchors=nextAnchors;
                editor.setCaretPosition(0);editor.revalidate();editor.repaint();
                List.copyOf(installedListeners).forEach(Runnable::run);
            } catch(BadLocationException | RuntimeException failure){
                if(closed || token!=generation)return;
                pending=null;pendingStyle=null;installFailure=failure instanceof RuntimeException runtime?runtime:new IllegalStateException("Could not install prepared Markdown",failure);
            }
            geometryChanged.run();
        };
        SwingUtilities.invokeLater(continuation[0]);
    }
    /** Updates style in place, retaining content, selection and loaded images. */
    void restyle(TerminalTypography typography,WorkspacePalette palette,SyntaxPalette syntax,Font bodyFont,double opacity) {
        edt();if(closed || bodyFont==null)return;
        if(pending!=null)install(pending,typography,palette,syntax,bodyFont,opacity);
        if(kit==null)return;
        this.typography=typography;this.palette=palette;this.syntax=syntax;this.bodyFont=bodyFont;this.opacity=opacity;
        kit.restyle((HTMLDocument)editor.getDocument(),typography,palette,bodyFont);
        editor.setFont(bodyFont.deriveFont((float)typography.size()));editor.setForeground(palette.text());editor.setSelectionColor(palette.selection());editor.setSelectedTextColor(palette.selectionText());
        editor.revalidate();editor.repaint();geometryChanged.run();
    }
    void bodyFont(Font font) {
        edt();if(closed||font==null)return;
        if(pendingStyle!=null){var style=pendingStyle;restyle(style.typography,style.palette,style.syntax,font,style.opacity);}
        else if(kit!=null)restyle(typography,palette,syntax,font,opacity);
    }
    private static Map<String,Integer> collectAnchors(HTMLDocument doc,MarkdownResult result) {
        var found=new HashMap<String,Integer>();
        var iterator=new ElementIterator(doc);
        for(Element e=iterator.first();e!=null;e=iterator.next()){
            Object names=e.getAttributes().getAttribute(PreviewKit.MARKERS);
            if(names instanceof List<?> values)for(Object name:values)found.putIfAbsent(name.toString(),e.getStartOffset());
        }
        var bySource=new HashMap<Integer,Integer>();
        for(var block:result.blocks())if(found.containsKey(block.id()))bySource.putIfAbsent(block.sourceStart(),found.get(block.id()));
        result.headings().forEach((name,source) -> {if(bySource.containsKey(source))found.put(name,bySource.get(source));});
        return Map.copyOf(found);
    }
    static int resourceId(String text,String prefix) {
        if(text==null || !text.startsWith(prefix))return -1;
        try {int value=Integer.parseInt(text.substring(prefix.length()));return value<0?-1:value;}catch(NumberFormatException e){return -1;}
    }
    private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Preview requires EDT");}
    @Override public void close(){edt();if(closed)return;cancelInstall();closed=true;installedListeners.clear();imageNotificationGeneration++;changedImages.clear();imageIds=Set.of();images.clear();imageReasons.clear();anchors=Map.of();installed=null;kit=null;editor.setEditorKit(new DefaultEditorKit());}
    private final class Surface extends JEditorPane {
        @Override public boolean getScrollableTracksViewportWidth(){return getParent() instanceof JViewport view && getMinimumSize().width<=view.getWidth();}
        @Override protected void paintComponent(Graphics graphics) {
            if(palette!=null){var g=(Graphics2D)graphics.create();try{g.setComposite(AlphaComposite.Src);var c=palette.background();g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)Math.round(255*opacity)));g.fillRect(0,0,getWidth(),getHeight());}finally{g.dispose();}}
            super.paintComponent(graphics);
        }
    }
}
