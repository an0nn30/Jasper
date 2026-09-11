package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTest {

    @Test
    void backwardsDragsAreNormalized() {
        Selection selection = new Selection(5, 3, 2, 7, false);

        assertThat(selection.startRow()).isEqualTo(2);
        assertThat(selection.startColumn()).isEqualTo(7);
        assertThat(selection.endRow()).isEqualTo(5);
        assertThat(selection.endColumn()).isEqualTo(3);
    }

    @Test
    void sameRowBackwardsDragIsNormalized() {
        Selection selection = new Selection(0, 6, 0, 2, false);

        assertThat(selection.startColumn()).isEqualTo(2);
        assertThat(selection.endColumn()).isEqualTo(6);
    }

    @Test
    void streamSelectionContainsWholeMiddleRows() {
        Selection selection = new Selection(1, 4, 3, 2, false);

        assertThat(selection.contains(1, 3)).isFalse();
        assertThat(selection.contains(1, 4)).isTrue();
        assertThat(selection.contains(2, 0)).isTrue();
        assertThat(selection.contains(2, 79)).isTrue();
        assertThat(selection.contains(3, 2)).isTrue();
        assertThat(selection.contains(3, 3)).isFalse();
        assertThat(selection.contains(4, 0)).isFalse();
    }

    @Test
    void blockSelectionIsARectangle() {
        Selection selection = new Selection(1, 5, 3, 2, true);

        assertThat(selection.contains(2, 3)).isTrue();
        assertThat(selection.contains(2, 1)).isFalse();
        assertThat(selection.contains(2, 6)).isFalse();
        assertThat(selection.columnsOn(2, 80)).containsExactly(2, 5);
    }

    @Test
    void columnsOnGivesEachRowItsRange() {
        Selection selection = new Selection(1, 4, 3, 2, false);

        assertThat(selection.columnsOn(0, 10)).isNull();
        assertThat(selection.columnsOn(1, 10)).containsExactly(4, 9);
        assertThat(selection.columnsOn(2, 10)).containsExactly(0, 9);
        assertThat(selection.columnsOn(3, 10)).containsExactly(0, 2);
    }

    @Test
    void atIsOneCellAndWithFocusKeepsTheAnchor() {
        Selection selection = Selection.at(7, 3, false);
        assertThat(selection.contains(7, 3)).isTrue();
        assertThat(selection.contains(7, 4)).isFalse();

        Selection extended = selection.withFocus(8, 5);
        assertThat(extended.anchorRow()).isEqualTo(7);
        assertThat(extended.anchorColumn()).isEqualTo(3);
        assertThat(extended.focusRow()).isEqualTo(8);
        assertThat(extended.focusColumn()).isEqualTo(5);
    }
}
