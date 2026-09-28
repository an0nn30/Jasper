package dev.jasper.editor.vim;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordsTest {
    @Test void readsVimsDefault() {
        Keywords k = Keywords.parse("@,48-57,_,192-255");
        assertThat(k.isWord('a')).isTrue();
        assertThat(k.isWord('Z')).isTrue();
        assertThat(k.isWord('5')).isTrue();
        assertThat(k.isWord('_')).isTrue();
        assertThat(k.isWord('-')).isFalse();
        assertThat(k.isWord(0xe9)).isTrue();
        assertThat(k.charClass(' ')).isZero();
        assertThat(k.charClass('.')).isEqualTo(1);
        assertThat(k.charClass('a')).isEqualTo(2);
        assertThat(k.charClass(0x1F600)).isEqualTo(3);
        assertThat(k.charClass(0x65e5)).isEqualTo(0x4e00);
    }

    @Test void readsRangesExclusionsAndCharacters() {
        Keywords k = Keywords.parse("a-c,-,^b");
        assertThat(k.isWord('a')).isTrue();
        assertThat(k.isWord('b')).isFalse();
        assertThat(k.isWord('-')).isTrue();
        assertThat(Keywords.parse("@-@").isWord('@')).isTrue();
        assertThatThrownBy(() -> Keywords.parse("300")).hasMessageContaining("E474");
    }
}
