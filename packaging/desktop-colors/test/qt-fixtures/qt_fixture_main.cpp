// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <QFile>
#include <QFileSystemWatcher>
#include <QWindow>
#include <cstdio>
int main(int argc,char **argv) {
    QApplication app(argc,argv);
    if(argc!=2) return 2;
    const QString path=QString::fromLocal8Bit(argv[1]);
    auto apply=[&] {
        QFile file(path); if(!file.open(QIODevice::ReadOnly)) return;
        const bool dark=file.readAll().trimmed()=="dark";
        QPalette palette=app.palette();
        palette.setColor(QPalette::Active,QPalette::Window,QColor(dark?"#202020":"#f0f0f0"));
        palette.setColor(QPalette::Active,QPalette::WindowText,QColor(dark?"#eeeeee":"#111111"));
        palette.setColor(QPalette::Active,QPalette::Base,QColor("#314159"));
        palette.setColor(QPalette::Active,QPalette::Text,QColor("#ffffff"));
        palette.setColor(QPalette::Inactive,QPalette::ToolTipBase,QColor("#123456"));
        palette.setColor(QPalette::Inactive,QPalette::ToolTipText,QColor("#ffffff"));
        QApplication::setPalette(palette);
    };
    apply();
    QFileSystemWatcher watcher; watcher.addPath(path);
    QObject::connect(&watcher,&QFileSystemWatcher::fileChanged,&app,[&](const QString &) { apply(); if(!watcher.files().contains(path)) watcher.addPath(path); });
    const int result=dc_qt_run(QT_VERSION_MAJOR==5?DC_QT5:DC_QT6,app);
    for(auto *window:QGuiApplication::allWindows()) if(window->isVisible()) return 9;
    std::fprintf(stderr,"mapped-windows=0\n");
    return result;
}
