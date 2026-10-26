// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <QEvent>
#include <QSocketNotifier>
#include <QStyle>
#include <QTimer>
#include <QWidget>
#include <cerrno>
#include <cmath>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <mutex>
#include <unistd.h>

static bool color(const QBrush &brush, dc_rgba *out) {
    if (brush.style()!=Qt::SolidPattern || !brush.color().isValid()) return false;
    // QColor::toRgb() clamps ExtendedRgb. Reject out-of-gamut input before
    // conversion so an unsupported brush never becomes a plausible solid pair.
    const QColor original=brush.color();
    if(original.spec()==QColor::ExtendedRgb) {
        const double extended[]={original.redF(),original.greenF(),original.blueF(),original.alphaF()};
        for(double value:extended) if(!std::isfinite(value) || value<0 || value>1) return false;
    }
    const QColor rgb=original.toRgb();
    const double values[]={rgb.redF(),rgb.greenF(),rgb.blueF(),rgb.alphaF()};
    for(double value:values) if(!std::isfinite(value) || value<0 || value>1) return false;
    *out={values[0],values[1],values[2],values[3]};
    return true;
}
bool dc_qt_brush_pair(const QBrush &background,const QBrush &foreground,dc_colors *out) {
    *out={};
    dc_colors pair{};
    if(!color(background,&pair.background) || !color(foreground,&pair.foreground)) return false;
    pair.present=1; *out=pair; return true;
}
bool dc_qt_pair(const QPalette &palette,QPalette::ColorGroup group,
                QPalette::ColorRole background,QPalette::ColorRole foreground,dc_colors *out) {
    return dc_qt_brush_pair(palette.brush(group,background),palette.brush(group,foreground),out);
}
static double luminance(dc_rgba c) {
    double rgb[]={c.red,c.green,c.blue};
    for(double &value:rgb) value=value<=.04045?value/12.92:std::pow((value+.055)/1.055,2.4);
    return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
}
void dc_qt_extract(const QPalette &palette,dc_palette *out) {
    std::memset(out->colors,0,sizeof out->colors);
    dc_qt_pair(palette,QPalette::Active,QPalette::Window,QPalette::WindowText,&out->colors[DC_WINDOW]);
    dc_qt_pair(palette,QPalette::Active,QPalette::Base,QPalette::Text,&out->colors[DC_VIEW]);
    out->colors[DC_DIALOG]=out->colors[DC_WINDOW];
    dc_qt_pair(palette,QPalette::Inactive,QPalette::ToolTipBase,QPalette::ToolTipText,&out->colors[DC_POPOVER]);
    dc_qt_pair(palette,QPalette::Active,QPalette::Highlight,QPalette::HighlightedText,&out->colors[DC_SELECTION]);
    dc_qt_polarity(out);
}
void dc_qt_polarity(dc_palette *out) {
    const auto &window=out->colors[DC_WINDOW];
    out->appearance=DC_UNKNOWN;
    if(window.present && window.background.alpha==1) {
        dc_rgba ink=window.foreground;
        ink.red=ink.red*ink.alpha+window.background.red*(1-ink.alpha);
        ink.green=ink.green*ink.alpha+window.background.green*(1-ink.alpha);
        ink.blue=ink.blue*ink.alpha+window.background.blue*(1-ink.alpha);
        out->appearance=luminance(window.background)<luminance(ink)?DC_DARK:DC_LIGHT;
    }
}

class Observer final : public QObject {
public:
    QTimer changed;
    bool eventFilter(QObject *,QEvent *event) override {
        if(event->type()==QEvent::ApplicationPaletteChange || event->type()==QEvent::PaletteChange || event->type()==QEvent::StyleChange)
            if(!changed.isActive()) changed.start();
        return false;
    }
};

int dc_qt_run(dc_source source,QApplication &app) {
#ifdef DC_KDE
    return dc_qt_run(source,app,dc_kde_extract);
#else
    return dc_qt_run(source,app,[](dc_palette *out) { dc_qt_extract(QApplication::palette(),out); });
#endif
}
int dc_qt_run(dc_source source,QApplication &app,void (*extract)(dc_palette *)) {
    if(!extract) return 2;
    // Reject accidental cross-major/source dispatch before publishing an identity.
#ifdef DC_KDE
    if(source!=(QT_VERSION_MAJOR==5?DC_KDE5:DC_KDE6)) return 2;
#else
    if(source!=(QT_VERSION_MAJOR==5?DC_QT5:DC_QT6)) return 2;
#endif
    const int flags=fcntl(STDIN_FILENO,F_GETFL,0);
    if(flags<0 || fcntl(STDIN_FILENO,F_SETFL,flags|O_NONBLOCK)<0) return 2;
    auto previousSignal=std::signal(SIGPIPE,SIG_IGN);
    app.setQuitOnLastWindowClosed(false);
    Observer observer;
    observer.changed.setSingleShot(true); observer.changed.setInterval(100);
    app.installEventFilter(&observer);
    QWidget probe; // Receives style/palette notifications, never shown or made native.
    int64_t generation=0;
    bool failed=false;
    auto snapshot=[&] {
        dc_palette palette{}; palette.generation=generation; palette.source=source;
        extract(&palette);
#ifdef DC_KDE
        const QByteArray implementation=QByteArray("KDE Frameworks ")+QByteArray::number(QT_VERSION_MAJOR)+" / Qt "+QByteArray::number(QT_VERSION_MAJOR)+" / "+app.style()->metaObject()->className();
        const QByteArray version=QByteArray("KF built against ")+dc_kde_compiled_version()+" / Qt runtime "+qVersion();
#else
        const QByteArray implementation=QByteArray("Qt ")+QByteArray::number(QT_VERSION_MAJOR)+" application palette / "+app.style()->metaObject()->className();
        const QByteArray version=qVersion();
#endif
        palette.implementation=implementation.constData(); palette.version=version.constData();
        palette.reason=palette.colors[DC_WINDOW].present?"":"window pair unavailable";
        if(dc_write_palette(stdout,&palette)!=0) { failed=true; app.exit(2); }
    };
    QObject::connect(&observer.changed,&QTimer::timeout,&app,snapshot);
    QTimer periodic;
    periodic.setInterval(30000);
    QObject::connect(&periodic,&QTimer::timeout,&app,snapshot);
    periodic.start();
    std::string pending; pending.reserve(DC_MAX_MESSAGE);
    QSocketNotifier input(STDIN_FILENO,QSocketNotifier::Read,&app);
    QObject::connect(&input,&QSocketNotifier::activated,&app,[&] {
        // Limit work per GUI turn, including a producer continuously writing commands.
        size_t consumed=0;
        char bytes[4096];
        while(consumed<4*DC_MAX_MESSAGE) {
            const ssize_t count=read(STDIN_FILENO,bytes,sizeof bytes);
            if(count<0 && errno==EINTR) continue;
            if(count<0 && (errno==EAGAIN || errno==EWOULDBLOCK)) return;
            if(count<=0) { input.setEnabled(false); app.exit(count==0 && pending.empty()?0:2); return; }
            consumed+=size_t(count);
            for(ssize_t i=0;i<count;++i) {
                if(bytes[i]=='\n') {
                    dc_command command{};
                    if(dc_read_command(pending.data(),pending.size(),&command)!=0) { input.setEnabled(false); app.exit(2); return; }
                    pending.clear();
                    if(command.kind==DC_PING) { if(dc_write_pong(stdout,command.generation)!=0) { input.setEnabled(false); app.exit(2); return; } }
                    else { generation=command.generation; snapshot(); if(failed) { input.setEnabled(false); return; } }
                } else {
                    if(pending.size()==DC_MAX_MESSAGE) { input.setEnabled(false); app.exit(2); return; }
                    pending.push_back(bytes[i]);
                }
            }
        }
    });
    QTimer::singleShot(0,&app,snapshot);
    const int result=app.exec();
    app.removeEventFilter(&observer);
    fcntl(STDIN_FILENO,F_SETFL,flags);
    std::signal(SIGPIPE,previousSignal);
    return failed?2:result;
}

static void boundedMessage(QtMsgType,const QMessageLogContext &,const QString &message) {
    static std::mutex lock;
    std::lock_guard<std::mutex> guard(lock);
    static size_t used=0;
    constexpr size_t limit=4096;
    if(used>=limit) return;
    const QByteArray bytes=message.toUtf8().left(int(limit-used-1));
    std::fwrite(bytes.constData(),1,size_t(bytes.size()),stderr); std::fputc('\n',stderr);
    used+=size_t(bytes.size())+1;
}
int dc_qt_main(dc_source source,int argc,char **argv) {
    qInstallMessageHandler(boundedMessage);
    QApplication app(argc,argv);
    return dc_qt_run(source,app);
}
