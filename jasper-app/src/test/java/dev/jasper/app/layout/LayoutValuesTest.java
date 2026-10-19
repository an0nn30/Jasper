package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 7.1 and 7.2: the layout's values and their invariants. */
class LayoutValuesTest {
    @Test void idsAreUuidsWithFreshFactoriesAndStrictParsing() {
        assertThat(TabId.random()).isNotEqualTo(TabId.random());
        assertThat(GroupId.random()).isNotEqualTo(GroupId.random());
        TabId tab = TabId.random();
        assertThat(tab.toString()).isEqualTo(tab.value().toString());
        assertThat(TabId.parse(tab.toString())).isEqualTo(tab);
        assertThat(GroupId.parse("3F1E0000-0000-4000-8000-00000000000A")).as("either case")
            .isEqualTo(new GroupId(UUID.fromString("3f1e0000-0000-4000-8000-00000000000a")));
        for (String bad : List.of("", "1-2-3-4-5", "3f1e0000-0000-4000-8000-00000000000", "3f1e0000x0000-4000-8000-00000000000a",
                "not a uuid at all, thirty-six chars!", "+0000001-0000-4000-8000-00000000000a",
                "00000001-0-0-0-000000000000000000001")) {
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> TabId.parse(bad));
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> GroupId.parse(bad));
        }
        assertThatNullPointerException().isThrownBy(() -> new TabId(null));
        assertThatNullPointerException().isThrownBy(() -> new GroupId(null));
        assertThatNullPointerException().isThrownBy(() -> GroupId.parse(null));
    }

    @Test void aGroupSelectsOneOfItsTabsAndAnEmptyGroupSelectsNothing() {
        assertThat(group(1, 2, 1, 2).selected()).contains(tab(2));
        assertThat(new Group(groupId(1), List.of(), Optional.empty()).tabs()).isEmpty();
        assertThatIllegalArgumentException().as("a tab it does not hold").isThrownBy(() -> group(1, 3, 1, 2));
        assertThatIllegalArgumentException().as("an empty group selects nothing")
            .isThrownBy(() -> new Group(groupId(1), List.of(), Optional.of(tab(1))));
        assertThatIllegalArgumentException().as("a group with tabs selects one")
            .isThrownBy(() -> new Group(groupId(1), List.of(tab(1)), Optional.empty()));
        assertThatIllegalArgumentException().as("a tab twice").isThrownBy(() -> group(1, 1, 1, 1));
        assertThatNullPointerException().isThrownBy(() -> new Group(null, List.of(), Optional.empty()));
        var tabs = new ArrayList<>(List.of(tab(1)));
        Group copied = new Group(groupId(1), tabs, Optional.of(tab(1)));
        tabs.add(tab(2));
        assertThat(copied.tabs()).containsExactly(tab(1));
    }

    @Test void childWeightsArePositiveAndFinite() {
        for (double bad : new double[] {0, -0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThatIllegalArgumentException().as("%s", bad).isThrownBy(() -> new Child(group(1), bad)).withMessageContaining("weight");
        assertThatNullPointerException().isThrownBy(() -> new Child(null, 1));
        assertThat(new Child(group(1), 1e-9).weight()).isEqualTo(1e-9);
    }

    @Test void aSplitNeedsTwoChildrenNormalizesItsWeightsAndFlattensItsOwnOrientation() {
        Group a = group(1), b = group(2), c = group(3);
        assertThatIllegalArgumentException().isThrownBy(() -> h(child(a, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Split(Orientation.HORIZONTAL, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new Split(null, List.of(child(a, 1), child(b, 1))));
        assertThat(h(child(a, 1), child(b, 3)).children()).extracting(Child::weight).containsExactly(0.25, 0.75);
        double third = 1.0 / 3;
        assertThat(h(child(a, third), child(b, third), child(c, third)).children()).extracting(Child::weight)
            .as("already normalized: kept bit for bit").containsExactly(third, third, third);
        Split tenths = h(child(a, 0.7), child(b, 0.2), child(c, 0.1));
        assertThat(tenths.children()).extracting(Child::weight)
            .as("summing to 1 within the tolerance but not exactly: kept bit for bit").containsExactly(0.7, 0.2, 0.1);
        assertThat(new Split(Orientation.HORIZONTAL, tenths.children())).as("normalizing twice changes nothing").isEqualTo(tenths);
        assertThat(h(child(a, 0.5), child(h(child(b, 0.5), child(c, 0.5)), 0.5))).as("a child of the same orientation is flattened")
            .isEqualTo(h(child(a, 0.5), child(b, 0.25), child(c, 0.25)));
        assertThat(h(child(a, 2), child(h(child(b, 1), child(c, 3)), 2))).as("flattened, then normalized")
            .isEqualTo(h(child(a, 0.5), child(b, 0.125), child(c, 0.375)));
        Split nested = h(child(a, 0.5), child(v(child(b, 0.5), child(c, 0.5)), 0.5));
        assertThat(nested.children()).hasSize(2);
        assertThat(nested.children().get(1).node()).isInstanceOf(Split.class);
        var source = new ArrayList<>(List.of(child(a, 0.5), child(b, 0.5)));
        Split copied = new Split(Orientation.VERTICAL, source);
        source.clear();
        assertThat(copied.children()).hasSize(2);
    }

    @Test void sidesKnowTheirOrientationAndOrder() {
        assertThat(Side.LEFT.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(Side.RIGHT.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(Side.UP.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(Side.DOWN.orientation()).isEqualTo(Orientation.VERTICAL);
        assertThat(Side.LEFT.leading()).isTrue();
        assertThat(Side.UP.leading()).isTrue();
        assertThat(Side.RIGHT.leading()).isFalse();
        assertThat(Side.DOWN.leading()).isFalse();
    }

    @Test void aRegionKnowsItsEdgesAndCentre() {
        Region region = new Region(0.25, 0.5, 0.5, 0.25);
        assertThat(region.right()).isEqualTo(0.75);
        assertThat(region.bottom()).isEqualTo(0.75);
        assertThat(region.centerX()).isEqualTo(0.5);
        assertThat(region.centerY()).isEqualTo(0.625);
    }
}
