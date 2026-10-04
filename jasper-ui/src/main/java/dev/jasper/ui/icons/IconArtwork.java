package dev.jasper.ui.icons;

import com.github.weisj.jsvg.geometry.path.BuildHistory;
import com.github.weisj.jsvg.geometry.path.PathParser;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

/** Color-free outline artwork on a 16-grid, with a kit-owned stroke. No resources or SVG markup. */
public final class IconArtwork {
    private static final Pattern TOKEN = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    private final String pathData;
    private final float stroke;
    private final Path2D path;
    public IconArtwork(String pathData) { this(pathData, 16, 1.5f); }
    public IconArtwork(String pathData, int gridSize, float stroke) {
        this.pathData = Objects.requireNonNull(pathData);
        if (gridSize != 16 || !Float.isFinite(stroke) || stroke < 1.4f || stroke > 1.6f)
            throw new IllegalArgumentException("Icons require a 16-grid and stroke 1.4..1.6");
        this.stroke = stroke;
        int expected = validate(pathData);
        var commands = new PathParser(pathData).parsePathCommand();
        if (commands.length != expected) throw new IllegalArgumentException("Incomplete icon path");
        path = new Path2D.Float();
        var history = new BuildHistory();
        for (var command : commands) command.appendPath(path, history);
        var bounds = path.getBounds2D();
        if (bounds.getMinX() < 0 || bounds.getMinY() < 0 || bounds.getMaxX() > 16 || bounds.getMaxY() > 16
            || (bounds.getWidth() == 0 && bounds.getHeight() == 0)) throw new IllegalArgumentException("Path must fit the 16-grid and contain artwork");
    }
    public String pathData() { return pathData; }
    public int gridSize() { return 16; }
    public float stroke() { return stroke; }
    Path2D path() { return (Path2D)path.clone(); }

    /** Validate all tokens and parameter groups before the tolerant vendor parser can truncate bad input. */
    private static int validate(String text) {
        if (text.length() > 8192 || text.isBlank()) throw new IllegalArgumentException("Icon path is empty or too large");
        var tokens = new ArrayList<String>();
        var matcher = TOKEN.matcher(text);
        int end = 0;
        while (matcher.find()) {
            String token = matcher.group();
            boolean command = Character.isLetter(token.charAt(0));
            boolean afterCommand = tokens.isEmpty() || Character.isLetter(tokens.getLast().charAt(0));
            String separator = text.substring(end, matcher.start());
            if (!separator.matches(command || afterCommand ? "[\\s]*" : "[\\s]*,?[\\s]*"))
                throw new IllegalArgumentException("Invalid path separator");
            tokens.add(token); end = matcher.end();
        }
        if (!text.substring(end).matches("[\\s]*") || tokens.isEmpty() || !tokens.getFirst().matches("[Mm]"))
            throw new IllegalArgumentException("Path must start with a move");
        int at = 0, count = 0; char command = 0;
        while (at < tokens.size()) {
            String token = tokens.get(at);
            if (Character.isLetter(token.charAt(0))) { command = token.charAt(0); at++; }
            else if (command == 0) throw new IllegalArgumentException("Expected a path command");
            int args = switch (Character.toUpperCase(command)) {
                case 'M', 'L', 'T' -> 2;
                case 'H', 'V' -> 1;
                case 'C' -> 6;
                case 'S', 'Q' -> 4;
                case 'A' -> 7;
                case 'Z' -> 0;
                default -> throw new IllegalArgumentException("Unknown path command");
            };
            if (++count > 2048) throw new IllegalArgumentException("Too many path commands");
            for (int i = 0; i < args; i++) {
                if (at >= tokens.size() || Character.isLetter(tokens.get(at).charAt(0))) throw new IllegalArgumentException("Missing path parameter");
                String number = tokens.get(at);
                boolean arcFlag = Character.toUpperCase(command) == 'A' && (i == 3 || i == 4);
                // SVG arc flags may be adjacent to each other and the following coordinate (e.g. 0114).
                if (arcFlag) {
                    if (number.charAt(0) != '0' && number.charAt(0) != '1') throw new IllegalArgumentException("Arc flag must be 0 or 1");
                    if (number.length() > 1) tokens.set(at, number.substring(1)); else at++;
                    number = number.substring(0, 1);
                } else at++;
                double value = Double.parseDouble(number);
                if (!Double.isFinite(value) || Math.abs(value) > 4096) throw new IllegalArgumentException("Invalid path parameter");
                if (Character.toUpperCase(command) == 'A') {
                    if ((i == 0 || i == 1) && value < 0) throw new IllegalArgumentException("Negative arc radius");
                    if ((i == 3 || i == 4) && !number.matches("[01]")) throw new IllegalArgumentException("Arc flag must be 0 or 1");
                }
            }
            if (command == 'M') command = 'L'; else if (command == 'm') command = 'l';
            if (args == 0) command = 0;
        }
        return count;
    }
}
