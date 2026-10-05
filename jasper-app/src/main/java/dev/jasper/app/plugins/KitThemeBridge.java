package dev.jasper.app.plugins;

import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.*;

/** Plan 1 bounded value bridge. Existing app engine remains the application owner until Plan 2. */
final class KitThemeBridge {
    private KitThemeBridge() {}
    static ThemePair pair(dev.jasper.app.appearance.ResolvedTheme value) {return new ThemePair(theme(value.chrome()),theme(value.workspace()));}
    static GeneratedTheme theme(dev.jasper.app.appearance.GeneratedTheme old) {
        var tokens=new EnumMap<Token,Color>(Token.class);old.tokens().forEach((key,value)->tokens.put(Token.valueOf(key.name()),value));
        var syntax=new EnumMap<SyntaxRole,SyntaxStyle>(SyntaxRole.class);old.syntax().forEach((key,value)->syntax.put(SyntaxRole.valueOf(key.name()),new SyntaxStyle(value.color(),value.bold(),value.italic())));
        var ansi=new HashMap<Integer,Color>();for(int i=0;i<16;i++)ansi.put(i,old.terminal().ansi().get(i));
        var seeds=Map.of(Seed.BACKGROUND,old.terminal().background(),Seed.FOREGROUND,old.terminal().foreground(),Seed.ACCENT,tokens.get(Token.ACCENT),Seed.CURSOR,old.terminal().cursor(),Seed.SELECTION,old.terminal().selection());
        var doc=new ThemeDocument("host",old.name(),Optional.of(old.dark()),Optional.empty(),0,Optional.of(true),Map.of("author",old.author()),seeds,ansi,tokens,syntax,old.uiKeys(),old.icons());
        var generated=ThemeGenerator.generate(doc);
        return new GeneratedTheme(old.name(),old.dark(),old.author(),tokens,syntax,new TerminalColors(old.terminal().background(),old.terminal().foreground(),old.terminal().cursor(),old.terminal().selection(),old.terminal().ansi()),old.uiKeys(),old.icons(),new GlassLevels(old.glass().opacity(),old.glass().blur()),generated.semantic());
    }
    /** Only legacy test hosts lack a resolved appearance; no global Swing color lookup. */
    static ThemePair fallback(boolean dark) {
        var ansi=new HashMap<Integer,Color>();var palette=dev.jasper.terminal.config.Palette.jasperDark();for(int i=0;i<16;i++)ansi.put(i,palette.ansi().get(i));
        var seeds=Map.of(Seed.BACKGROUND,dark?new Color(0x202124):Color.WHITE,Seed.FOREGROUND,dark?Color.WHITE:Color.BLACK,Seed.ACCENT,new Color(0x3574f0));
        var theme=ThemeGenerator.generate(new ThemeDocument("test-host","Host",Optional.of(dark),Optional.empty(),0,Optional.empty(),Map.of(),seeds,ansi,Map.of(),Map.of(),Map.of(),Map.of()));
        return new ThemePair(theme,theme);
    }
}
