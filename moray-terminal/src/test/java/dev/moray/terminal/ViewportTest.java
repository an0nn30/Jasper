package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViewportTest {
    // Ten lines of scrollback above a three-row screen whose top is absolute row 10.
    private static final ScreenSnapshot LIVE = snapshot(10, 0);

    @Test
    void startsFollowingTheOutput() {
        Viewport viewport = new Viewport();

        assertThat(viewport.following()).isTrue();
        assertThat(viewport.topRow()).isEqualTo(ScreenSnapshot.FOLLOW_OUTPUT);
    }

    @Test
    void scrollingBackAnchorsAtAnAbsoluteRow() {
        Viewport viewport = new Viewport();

        viewport.scrollBy(-3, LIVE);

        assertThat(viewport.topRow()).isEqualTo(7);
        assertThat(viewport.following()).isFalse();
    }

    @Test
    void scrollingStopsAtTheOldestLine() {
        Viewport viewport = new Viewport();

        viewport.scrollBy(-50, LIVE);

        assertThat(viewport.topRow()).isZero();
    }

    @Test
    void scrollingDownToTheLiveScreenFollowsAgain() {
        Viewport viewport = new Viewport();
        viewport.scrollBy(-3, LIVE);

        viewport.scrollBy(5, snapshot(7, 3));

        assertThat(viewport.following()).isTrue();
    }

    @Test
    void showAtTopIsClamped() {
        Viewport viewport = new Viewport();

        viewport.showAtTop(4, LIVE);
        assertThat(viewport.topRow()).isEqualTo(4);

        viewport.showAtTop(99, LIVE);
        assertThat(viewport.following()).isTrue();
    }

    @Test
    void revealMovesOnlyAsFarAsNeeded() {
        Viewport viewport = new Viewport();
        viewport.showAtTop(5, LIVE);
        ScreenSnapshot showingRowsFiveToSeven = snapshot(5, 5);

        viewport.reveal(6, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(5);

        viewport.reveal(9, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(7);

        viewport.reveal(2, showingRowsFiveToSeven);
        assertThat(viewport.topRow()).isEqualTo(2);
    }

    @Test
    void followReturnsToTheLiveScreen() {
        Viewport viewport = new Viewport();
        viewport.showAtTop(3, LIVE);

        viewport.follow();

        assertThat(viewport.following()).isTrue();
    }

    private static ScreenSnapshot snapshot(long firstRow, int scrollOffset) {
        return new ScreenSnapshot(10, 3, List.of(), 0, 0, true, null, firstRow, scrollOffset, 10, false);
    }
}
