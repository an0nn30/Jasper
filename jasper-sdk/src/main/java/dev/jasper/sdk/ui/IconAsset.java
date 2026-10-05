package dev.jasper.sdk.ui;
import java.util.Objects;
/** Monochrome SVG path data on the kit's 16-grid. No markup, resources, tint or stroke.
 *
 * @param name namespace:name
 * @param pathData bounded validated SVG commands */
public record IconAsset(String name, String pathData) {
 public IconAsset { Objects.requireNonNull(name); Objects.requireNonNull(pathData);
  if (!name.matches("[a-z][a-z0-9.-]*:[a-z][a-z0-9.-]*")) throw new IllegalArgumentException("Icon name must be namespace:name");
  IconPaths.validate(pathData);
 }
}
