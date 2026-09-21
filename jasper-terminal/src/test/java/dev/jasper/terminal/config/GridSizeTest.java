package dev.jasper.terminal.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridSizeTest {

    @Test
    void wholeCellsThatFit() {
        assertThat(GridSize.fit(803, 600, 8, 17)).isEqualTo(new GridSize(100, 35));
    }

    @Test
    void neverSmallerThanEmulatorMinimum() {
        assertThat(GridSize.fit(3, 0, 8, 17)).isEqualTo(new GridSize(5, 2));
    }
}
