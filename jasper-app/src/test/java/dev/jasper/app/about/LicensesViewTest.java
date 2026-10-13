package dev.jasper.app.about;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class LicensesViewTest {
    private static final ThirdPartyNotices.Component WITH_NOTICE = new ThirdPartyNotices.Component("Alpha", "1.0", "Apache-2.0",
        "https://alpha.example/project", List.of(), "apache-2.0.txt", Optional.of("mina.NOTICE.txt"));
    private static final ThirdPartyNotices.Component WITHOUT_NOTICE = new ThirdPartyNotices.Component("Beta", "", "MIT",
        "https://beta.example", List.of(), "jsvg.LICENSE.txt", Optional.empty());
    private final List<URI> browsed = new ArrayList<>();
    private final List<LicensesView> opened = new ArrayList<>();
    private int dones;

    @AfterEach void closeViews() { opened.forEach(LicensesView::close); }

    private LicensesView view(List<ThirdPartyNotices.Component> components) {
        var view = new LicensesView(KitTestSupport.context(), components, new LicensesView.Hooks(browsed::add, () -> dones++));
        opened.add(view);
        view.setSize(760, 520);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(view);
        return view;
    }

    private static String title(LicensesView view) { return KitTestSupport.require(view, LicensesView.TITLE, JLabel.class).getText(); }

    @Test void jasperIsListedFirstAndShownFirst() {
        var view = view(ThirdPartyNotices.listed("1.2.0"));
        assertThat(view.rowTitle(0)).isEqualTo("Jasper 1.2.0");
        assertThat(title(view)).isEqualTo("Jasper 1.2.0");
        assertThat(KitTestSupport.require(view, LicensesView.LICENSE_NAME, JLabel.class).getText()).isEqualTo("PolyForm Noncommercial 1.0.0");
        assertThat(view.licenseText()).startsWith("# PolyForm Noncommercial License 1.0.0");
    }

    @Test void selectingAComponentShowsItsNoticeAndTextFromTheTop() {
        var view = view(List.of(WITHOUT_NOTICE, WITH_NOTICE));
        view.select(1);
        assertThat(title(view)).isEqualTo("Alpha 1.0");
        assertThat(view.noticeText()).isEqualTo(ThirdPartyNotices.read("mina.NOTICE.txt"));
        assertThat(view.licenseText()).isEqualTo(ThirdPartyNotices.read("apache-2.0.txt"));
        assertThat(view.licenseCaret()).isZero();
    }

    @Test void aComponentWithoutANoticeHasNoNoticeCardAndTheNoticeReturns() {
        var view = view(List.of(WITH_NOTICE, WITHOUT_NOTICE));
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNotNull();
        view.select(1);
        assertThat(title(view)).isEqualTo("Beta");
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNull();
        view.select(0);
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNotNull();
        assertThat(view.noticeText()).isEqualTo(ThirdPartyNotices.read("mina.NOTICE.txt"));
    }

    @Test void theHomepageLinkOpensInTheBrowser() {
        var view = view(List.of(WITH_NOTICE));
        var link = KitTestSupport.require(view, LicensesView.HOMEPAGE, AbstractButton.class);
        assertThat(link.getText()).isEqualTo("alpha.example/project");
        link.doClick();
        assertThat(browsed).containsExactly(URI.create("https://alpha.example/project"));
    }

    @Test void doneClosesTheWindow() {
        var view = view(List.of(WITH_NOTICE));
        KitTestSupport.require(view, LicensesView.DONE, AbstractButton.class).doClick();
        assertThat(dones).isEqualTo(1);
    }

    @Test void aMissingTextSaysSoInsteadOfFailing() {
        var view = view(List.of(new ThirdPartyNotices.Component("Gamma", "2", "MIT", "https://gamma.example", List.of(),
            "missing.txt", Optional.empty())));
        assertThat(view.licenseText()).contains("missing.txt");
    }

    @Test void aSelectionOutsideTheListIsIgnored() {
        var view = view(List.of(WITH_NOTICE, WITHOUT_NOTICE));
        view.select(7);
        view.select(-1);
        assertThat(title(view)).isEqualTo("Alpha 1.0");
    }
}
