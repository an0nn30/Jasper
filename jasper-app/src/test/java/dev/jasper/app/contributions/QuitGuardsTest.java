package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class QuitGuardsTest {
    private final QuitGuards guards = new QuitGuards();
    private final List<String> asked = new ArrayList<>();

    @Test void asksInRegistrationOrderAndStopsAtTheFirstRefusal() {
        guards.add(() -> { asked.add("first"); return CompletableFuture.completedFuture(true); });
        var second = guards.add(() -> { asked.add("second"); return CompletableFuture.completedFuture(false); });
        guards.add(() -> { asked.add("third"); return CompletableFuture.completedFuture(true); });
        assertThat(guards.mayQuit()).isFalse();
        assertThat(asked).containsExactly("first", "second");
        second.close();
        asked.clear();
        assertThat(guards.mayQuit()).isTrue();
        assertThat(asked).containsExactly("first", "third");
    }

    @Test void waitsForAPendingDecisionWhilePumpingEvents() {
        var decision = new CompletableFuture<Boolean>();
        guards.add(() -> { SwingUtilities.invokeLater(() -> decision.complete(false)); return decision; });
        assertThat(guards.mayQuit()).isFalse();
    }

    @Test void aNestedAskRefuses() {
        boolean[] nested = {true};
        guards.add(() -> { nested[0] = guards.mayQuit(); return CompletableFuture.completedFuture(true); });
        assertThat(guards.mayQuit()).isTrue();
        assertThat(nested[0]).isFalse();
    }
}
