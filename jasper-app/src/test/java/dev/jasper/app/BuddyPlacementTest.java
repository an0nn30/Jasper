package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyPlacementTest {
    private static final Dimension SIZE = new Dimension(84, 96);
    private static final Rectangle MAIN = new Rectangle(0, 25, 1512, 982 - 25 - 70); // menu bar and Dock removed

    @Test void defaultSitsInTheBottomRightOfTheUsableAreaWithAMargin() {
        assertThat(BuddyPlacement.defaultLocation(MAIN, SIZE))
            .isEqualTo(new Point(1512 - 84 - 24, 25 + 887 - 96 - 24));
    }

    @Test void savedPointsThatAreMostlyOnAScreenAreKept() {
        Point saved = new Point(1512 - 30, 400); // 30 px visible of 84: less than half
        assertThat(BuddyPlacement.clamp(new Point(100, 100), List.of(MAIN), SIZE)).isEqualTo(new Point(100, 100));
        assertThat(BuddyPlacement.clamp(new Point(1512 - 42, 400), List.of(MAIN), SIZE)).isEqualTo(new Point(1512 - 42, 400));
        assertThat(BuddyPlacement.clamp(saved, List.of(MAIN), SIZE)).isEqualTo(new Point(1512 - 84, 400));
    }

    @Test void offScreenPointsMoveOntoTheNearestScreenFullyVisible() {
        Rectangle second = new Rectangle(1512, 0, 2560, 1440);
        assertThat(BuddyPlacement.clamp(new Point(-500, -500), List.of(MAIN, second), SIZE)).isEqualTo(new Point(0, 25));
        assertThat(BuddyPlacement.clamp(new Point(5000, 700), List.of(MAIN, second), SIZE))
            .isEqualTo(new Point(1512 + 2560 - 84, 700));
        assertThat(BuddyPlacement.clamp(new Point(2000, 3000), List.of(MAIN, second), SIZE))
            .isEqualTo(new Point(2000, 1440 - 96));
    }

    @Test void withoutScreensTheSavedPointIsReturned() {
        assertThat(BuddyPlacement.clamp(new Point(7, 9), List.of(), SIZE)).isEqualTo(new Point(7, 9));
    }
}
