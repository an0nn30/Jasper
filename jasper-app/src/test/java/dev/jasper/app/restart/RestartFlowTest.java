package dev.jasper.app.restart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestartFlowTest {
    private final List<RestartMode> restarts = new ArrayList<>();
    private final List<RestartFlow.State> states = new ArrayList<>();
    private boolean restartable = true;

    private RestartFlow flow() {
        var flow = new RestartFlow(mode -> { restarts.add(mode); return restartable; });
        flow.onChanged(() -> states.add(flow.state()));
        return flow;
    }

    @Test void restartNowKeepsTheLaunchAndAnUnknownCommandLineIsSaidSo() {
        RestartFlow flow = flow();
        flow.restartNow();
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(flow.state()).isEqualTo(RestartFlow.State.IDLE);
        restartable = false;
        flow.restartNow();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.UNAVAILABLE);
    }

    @Test void restartingNormallyLeavesSafeModeAtOnce() {
        RestartFlow flow = flow();
        flow.restartNormally();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.IDLE);
    }

    @Test void cancelForgetsAnUnavailableRestart() {
        restartable = false;
        RestartFlow flow = flow();
        flow.restartNormally();
        flow.cancel();
        assertThat(states).containsExactly(RestartFlow.State.UNAVAILABLE, RestartFlow.State.IDLE);
    }
}
