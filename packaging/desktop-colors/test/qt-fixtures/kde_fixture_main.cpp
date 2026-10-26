// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <QEvent>
#include <QFile>
#include <QFileSystemWatcher>
#include <QWidget>
#include <QWindow>
#include <cstdio>

static QString control;
class WindowEvents final : public QObject {
public:
    int shown=0,activated=0;
    bool eventFilter(QObject *object,QEvent *event) override {
        const auto widget=qobject_cast<QWidget *>(object);
        const bool window=qobject_cast<QWindow *>(object) || (widget && widget->isWindow());
        if(window && event->type()==QEvent::Show) ++shown;
        if(window && event->type()==QEvent::WindowActivate) ++activated;
        return false;
    }
};
static void extract(dc_palette *out) {
    dc_kde_extract(out);
    QFile file(control);
    if(file.open(QIODevice::ReadOnly) && file.readAll().trimmed()=="missing") {
        dc_qt_brush_pair(QBrush(QColor()),QBrush(Qt::white),&out->colors[DC_WINDOW]);
        out->colors[DC_DIALOG]=out->colors[DC_WINDOW];
        dc_qt_polarity(out);
    }
}
int main(int argc,char **argv) {
    QApplication app(argc,argv);
    if(argc!=2) return 2;
    WindowEvents windows; app.installEventFilter(&windows);
    control=QString::fromLocal8Bit(argv[1]);
    QFileSystemWatcher watcher; watcher.addPath(control);
    QObject::connect(&watcher,&QFileSystemWatcher::fileChanged,&app,[&](const QString &) {
        QFile file(control); if(!file.open(QIODevice::ReadOnly)) return;
        const auto mode=file.readAll().trimmed();
        QEvent event(mode=="style"?QEvent::StyleChange:mode=="palette"?QEvent::PaletteChange:QEvent::ApplicationPaletteChange);
        QCoreApplication::sendEvent(&app,&event);
        if(!watcher.files().contains(control)) watcher.addPath(control);
    });
    const int result=dc_qt_run(QT_VERSION_MAJOR==5?DC_KDE5:DC_KDE6,app,extract);
    if(windows.shown || windows.activated) return 9;
    for(auto *window:QGuiApplication::allWindows()) if(window->isVisible()) return 9;
    std::fprintf(stderr,"mapped-windows=0 activated-windows=0\n");
    return result;
}
