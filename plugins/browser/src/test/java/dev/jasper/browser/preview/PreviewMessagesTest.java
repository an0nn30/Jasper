package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.preview.PreviewMessages.Found;
import dev.jasper.browser.preview.PreviewMessages.Laid;
import dev.jasper.browser.preview.PreviewMessages.Link;
import dev.jasper.browser.preview.PreviewMessages.Ready;
import dev.jasper.browser.preview.PreviewMessages.Scrolled;
import dev.jasper.browser.preview.PreviewMessages.Selected;
import dev.jasper.browser.preview.PreviewMessages.Shown;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a preview's page may tell Jasper: one flat JSON object of an op {@link PreviewMessages#OPS} names, each value a
 * string or a boolean but for {@code find.js}'s answers, whose ids and counts are integers. Anything else is dropped.
 */
class PreviewMessagesTest {
    @Test void eachOpParses() {
        assertThat(PreviewMessages.read("{\"op\":\"ready\"}")).contains(new Ready());
        assertThat(PreviewMessages.read("{\"op\":\"shown\",\"rev\":\"12\"}")).contains(new Shown(12));
        var laid = PreviewMessages.read("{\"op\":\"geometry\",\"rev\":\"3\",\"tops\":\"0,NaN,48.5,1e+21\",\"height\":\"900\",\"viewport\":\"400.25\"}");
        assertThat(laid).hasValueSatisfying(message -> {
            PreviewEvent.Geometry geometry = ((Laid) message).geometry();
            assertThat(geometry.revision()).isEqualTo(3);
            assertThat(geometry.blocks()).isEqualTo(4);
            assertThat(geometry.top(0)).isEqualTo(OptionalDouble.of(0));
            assertThat(geometry.top(1)).as("NaN: a block with no box").isEmpty();
            assertThat(geometry.top(2)).isEqualTo(OptionalDouble.of(48.5));
            assertThat(geometry.top(3)).isEqualTo(OptionalDouble.of(1e21));
            assertThat(geometry.height()).isEqualTo(900);
            assertThat(geometry.viewport()).isEqualTo(400.25);
        });
        assertThat(PreviewMessages.read("{\"op\":\"geometry\",\"rev\":\"0\",\"tops\":\"\",\"height\":\"0\",\"viewport\":\"0\"}"))
            .as("no blocks").hasValueSatisfying(message -> assertThat(((Laid) message).geometry().blocks()).isZero());
        assertThat(PreviewMessages.read("{\"op\":\"geometry\",\"rev\":\"0\",\"tops\":\"NaN\",\"height\":\"0\",\"viewport\":\"0\"}"))
            .as("one block, with no box: not read as none").hasValueSatisfying(message -> {
                PreviewEvent.Geometry geometry = ((Laid) message).geometry();
                assertThat(geometry.blocks()).isEqualTo(1);
                assertThat(geometry.top(0)).isEmpty();
            });
        assertThat(PreviewMessages.read("{\"op\":\"scrolled\",\"y\":\"120.5\",\"user\":true}")).contains(new Scrolled(120.5, true));
        assertThat(PreviewMessages.read("{\"op\":\"scrolled\",\"y\":\"-0\",\"user\":false}")).contains(new Scrolled(-0.0, false));
        assertThat(PreviewMessages.read("{\"op\":\"link\",\"id\":\"7\"}")).contains(new Link(7));
        assertThat(PreviewMessages.read("{\"op\":\"find\",\"search\":4,\"count\":10,\"invalid\":false}")).contains(new Found(4, 10, false));
        assertThat(PreviewMessages.read("{\"op\":\"find\",\"search\":5,\"count\":0,\"invalid\":true}")).contains(new Found(5, 0, true));
        assertThat(PreviewMessages.read("{\"op\":\"find\",\"search\":6,\"selection\":\"word\"}")).contains(new Selected(6, "word"));
        assertThat(PreviewMessages.read(" { \"rev\" : \"1\" , \"op\" : \"shown\" } ")).as("members in any order, with white space")
            .contains(new Shown(1));
    }

    @Test void anythingElseIsDropped() {
        List<String> dropped = List.of(
            "", "null", "[]", "\"ready\"", "{}", "{\"op\":\"ready\"} x",
            "{\"op\":\"reload\"}", "{\"op\":\"READY\"}", "{\"op\":1}",
            "{\"op\":\"ready\",\"op\":\"ready\"}",
            "{\"op\":\"ready\",\"extra\":\"x\"}",
            "{\"op\":\"shown\",\"rev\":{\"n\":\"1\"}}",
            "{\"op\":\"shown\",\"rev\":[\"1\"]}",
            "{\"op\":\"shown\",\"rev\":1}",
            "{\"op\":\"shown\"}",
            "{\"op\":\"shown\",\"rev\":\"one\"}",
            "{\"op\":\"shown\",\"rev\":\"-1\"}",
            "{\"op\":\"shown\",\"rev\":\"01\"}",
            "{\"op\":\"shown\",\"rev\":\"+1\"}",
            "{\"op\":\"shown\",\"rev\":\"1.0\"}",
            "{\"op\":\"shown\",\"rev\":\"99999999999999999999\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0,x\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0,,1\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\",\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"nan\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0\",\"height\":\"NaN\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"Infinity\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0x10\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0\",\"height\":\"-1\",\"viewport\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"0\",\"height\":\"1\"}",
            "{\"op\":\"geometry\",\"rev\":\"x\",\"tops\":\"0\",\"height\":\"1\",\"viewport\":\"1\"}",
            "{\"op\":\"scrolled\",\"y\":\"1e999\",\"user\":true}",
            "{\"op\":\"scrolled\",\"y\":\"1\",\"user\":\"true\"}",
            "{\"op\":\"scrolled\",\"y\":1,\"user\":true}",
            "{\"op\":\"link\",\"id\":\"-1\"}",
            "{\"op\":\"link\",\"id\":\"2147483648\"}",
            "{\"op\":\"link\",\"id\":\"07\"}",
            "{\"op\":\"link\",\"id\":7}",
            "{\"op\":\"find\",\"search\":0,\"count\":1,\"invalid\":false}",
            "{\"op\":\"find\",\"search\":\"1\",\"count\":1,\"invalid\":false}",
            "{\"op\":\"find\",\"search\":1,\"count\":-1,\"invalid\":false}",
            "{\"op\":\"find\",\"search\":1,\"count\":10001,\"invalid\":false}",
            "{\"op\":\"find\",\"search\":1,\"count\":1.5,\"invalid\":false}",
            "{\"op\":\"find\",\"search\":1,\"count\":1}",
            "{\"op\":\"find\",\"search\":1,\"count\":1,\"invalid\":false,\"selection\":\"x\"}",
            "{\"op\":\"find\",\"search\":1,\"selection\":\"" + "x".repeat(1_001) + "\"}",
            "{\"op\":\"find\",\"search\":1,\"selection\":true}");
        for (String request : dropped) assertThat(PreviewMessages.read(request)).as(request).isEmpty();
        assertThat(PreviewMessages.read(null)).isEmpty();
    }

    @Test void aRequestOverFourMibIsDropped() {
        String padding = "0,".repeat((PreviewMessages.LIMIT - 100) / 2);
        String fits = "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"" + padding + "0\",\"height\":\"1\",\"viewport\":\"1\"}";
        assertThat(fits.length()).isLessThanOrEqualTo(PreviewMessages.LIMIT);
        assertThat(PreviewMessages.read(fits)).isPresent();
        String over = "{\"op\":\"geometry\",\"rev\":\"1\",\"tops\":\"" + padding + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0\",\"height\":\"1\",\"viewport\":\"1\"}";
        assertThat(over.length()).isGreaterThan(PreviewMessages.LIMIT);
        assertThat(PreviewMessages.read(over)).isEmpty();
        assertThat(PreviewMessages.LIMIT).as("4 MiB").isEqualTo(4 * 1024 * 1024);
    }
}
