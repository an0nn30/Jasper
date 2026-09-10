package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridSizeTest {

    @Test
    void wholeCellsThatFit() {
        assertThat(GridSize.fit(803, 600, 8, 17)).isEqualTo(new GridSize(100, 35));
    }

    @Test
    void neverSmallerThanOneCell() {
        assertThat(GridSize.fit(3, 0, 8, 17)).isEqualTo(new GridSize(1, 1));
    }
}
