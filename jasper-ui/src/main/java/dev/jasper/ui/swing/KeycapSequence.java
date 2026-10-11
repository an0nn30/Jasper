package dev.jasper.ui.swing;

import java.util.List;
import java.util.Objects;

/** Literal physical key labels and one spoken chord; decorative caps are never focus stops. */
public record KeycapSequence(List<String> keys,String accessibleName) {
    public KeycapSequence {
        keys=List.copyOf(keys);Objects.requireNonNull(accessibleName);
        if(keys.isEmpty() || keys.size()>16 || keys.stream().anyMatch(String::isBlank) || accessibleName.isBlank())throw new IllegalArgumentException("A key sequence needs labels and a spoken chord");
    }
}
