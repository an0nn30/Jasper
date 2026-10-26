// SPDX-License-Identifier: MIT
// A compatibility experiment, never a packaged helper. All writes stay in a temporary root.
#include <QApplication>
#include <QEvent>
#include <QFile>
#include <QPalette>
#include <QTemporaryDir>
#include <QTextStream>
#include <QWidget>
#include <QWindow>

#ifdef DC_KDE
#include <KColorScheme>
#include <KConfig>
#include <KSharedConfig>
#if QT_VERSION_MAJOR == 5
#include <kconfigwidgets_version.h>
#define DC_KF_VERSION KCONFIGWIDGETS_VERSION_STRING
#else
#include <kcolorscheme_version.h>
#define DC_KF_VERSION KCOLORSCHEME_VERSION_STRING
#endif
#endif

class PaletteEvents final : public QObject {
public:
    int application = 0;
    int widget = 0;
    bool eventFilter(QObject *, QEvent *event) override {
        if (event->type() == QEvent::ApplicationPaletteChange) ++application;
        if (event->type() == QEvent::PaletteChange) ++widget;
        return false;
    }
};

static int visibleWindows() {
    int count = 0;
    for (auto *window : QGuiApplication::allWindows()) if (window->isVisible()) ++count;
    return count;
}

static void pair(QTextStream &out, const char *name, const QPalette &palette,
                 QPalette::ColorGroup group, QPalette::ColorRole background,
                 QPalette::ColorRole foreground) {
    out << name << '=' << palette.color(group, background).name(QColor::HexArgb)
        << ',' << palette.color(group, foreground).name(QColor::HexArgb) << '\n';
}

#ifdef DC_KDE
static bool writeFixture(const QString &path, bool changed) {
    QFile file(path);
    if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate)) return false;
    const QByteArray content = QByteArray("[Colors:Window]\nBackgroundNormal=")
        + (changed ? "51,52,53" : "11,12,13")
        + "\nForegroundNormal=211,212,213\n"
          "[Colors:View]\nBackgroundNormal=21,22,23\nForegroundNormal=221,222,223\n"
          "[Colors:Selection]\nBackgroundNormal=31,32,33\nForegroundNormal=231,232,233\n"
          "[Colors:Tooltip]\nBackgroundNormal=41,42,43\nForegroundNormal=241,242,243\n";
    return file.write(content) == content.size();
}

static bool kdePairs(QTextStream &out, const KSharedConfig::Ptr &config, bool changed) {
    const KColorScheme::ColorSet sets[] = {KColorScheme::Window, KColorScheme::View,
        KColorScheme::Selection, KColorScheme::Tooltip};
    const char *names[] = {"kde.window", "kde.view", "kde.selection", "kde.tooltip"};
    const QColor backgrounds[] = {changed ? QColor(51,52,53) : QColor(11,12,13),
        QColor(21,22,23), QColor(31,32,33), QColor(41,42,43)};
    const QColor foregrounds[] = {QColor(211,212,213), QColor(221,222,223),
        QColor(231,232,233), QColor(241,242,243)};
    for (int i = 0; i < 4; ++i) {
        const KColorScheme scheme(QPalette::Active, sets[i], config);
        const QColor background = scheme.background(KColorScheme::NormalBackground).color();
        const QColor foreground = scheme.foreground(KColorScheme::NormalText).color();
        out << names[i] << '=' << background.name(QColor::HexArgb) << ','
            << foreground.name(QColor::HexArgb) << '\n';
        if (background != backgrounds[i] || foreground != foregrounds[i]) return false;
    }
    return true;
}
#endif

int main(int argc, char **argv) {
    QTemporaryDir root;
    if (!root.isValid()) return 10;
    qputenv("XDG_CONFIG_HOME", root.path().toUtf8());
    qputenv("XDG_CONFIG_DIRS", root.path().toUtf8());
    qputenv("XDG_CACHE_HOME", root.path().toUtf8());
    qputenv("XDG_DATA_HOME", root.path().toUtf8());
    qputenv("HOME", root.path().toUtf8());
#ifdef DC_KDE
    const QString configPath = root.filePath(QStringLiteral("kdeglobals"));
    if (!writeFixture(configPath, false)) return 11;
#endif
    QApplication app(argc, argv);
    app.setQuitOnLastWindowClosed(false);
    if (visibleWindows() != 0) return 2;
    const auto palette = QApplication::palette();
    if (!palette.color(QPalette::Active, QPalette::Window).isValid()) return 3;
    if (!palette.color(QPalette::Active, QPalette::WindowText).isValid()) return 4;
    if (QT_VERSION_MAJOR != qVersion()[0] - '0') return 5;
    QTextStream out(stdout);
    out << "qt.compiled=" << QT_VERSION_STR << "\nqt.runtime=" << qVersion() << '\n';
    pair(out, "qt.window", palette, QPalette::Active, QPalette::Window, QPalette::WindowText);
    pair(out, "qt.view", palette, QPalette::Active, QPalette::Base, QPalette::Text);
    pair(out, "qt.selection", palette, QPalette::Active, QPalette::Highlight, QPalette::HighlightedText);
    pair(out, "qt.tooltip", palette, QPalette::Inactive, QPalette::ToolTipBase, QPalette::ToolTipText);
    PaletteEvents events;
    app.installEventFilter(&events);
    QWidget widget; // No show(), winId(), activation or native-window request.
    QPalette changed = palette;
    changed.setColor(QPalette::Active, QPalette::Window, QColor(7, 19, 31));
    QApplication::setPalette(changed);
    QCoreApplication::processEvents();
    if (events.application == 0 || events.widget == 0) return 6;
    QApplication::setPalette(palette);
    out << "events.application=" << events.application << "\nevents.widget=" << events.widget << '\n';
#ifdef DC_KDE
    out << "kf.compiled=" << DC_KF_VERSION << "\nkf.runtime=unknown-no-public-query\n";
    const auto config = KSharedConfig::openConfig(QStringLiteral("kdeglobals"), KConfig::FullConfig);
    if (!kdePairs(out, config, false)) return 12;
    if (!writeFixture(configPath, true)) return 13;
    config->reparseConfiguration();
    if (!kdePairs(out, config, true)) return 14;
#if DC_HAVE_KCOLORSCHEME_HEADER
    const KColorScheme header(QPalette::Active, KColorScheme::Header, config);
    out << "header.api=present\nheader.theme="
        << (config->hasGroup(QStringLiteral("Colors:Header")) ? "present" : "absent") << '\n';
    (void) header;
#else
    out << "header.api=absent\n";
#endif
#else
    out << "header.api=not-applicable\n";
#endif
    QFile maps(QStringLiteral("/proc/self/maps"));
    if (maps.open(QIODevice::ReadOnly)) {
        for (const auto &line : maps.readAll().split('\n')) {
            if (line.contains("libQt") || line.contains("libKF")) out << "runtime.map=" << line << '\n';
        }
    }
    const int mapped = visibleWindows();
    out << "mapped-windows=" << mapped << '\n';
    return mapped == 0 ? 0 : 2;
}
