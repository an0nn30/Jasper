// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <QFile>
#include <QLinearGradient>
#include <QTemporaryDir>
#include <cmath>
#include <cstdio>
#include <cstdlib>

static int checks;
static void check(bool ok,const char *name) { ++checks; if(!ok) { std::fprintf(stderr,"FAIL: %s\n",name); std::exit(1); } }
static void fixture(const QString &path,const char *name) {
    QFile input(QStringLiteral(DC_KDE_FIXTURES)+QStringLiteral("/")+QString::fromLatin1(name));
    check(input.open(QIODevice::ReadOnly),"fixture readable");
    QFile output(path); check(output.open(QIODevice::WriteOnly|QIODevice::Truncate),"fixture writable");
    const auto bytes=input.readAll(); check(output.write(bytes)==bytes.size(),"fixture copied");
}
static void pair(const dc_colors &value,int br,int bg,int bb,int fr,int fg,int fb,const char *name) {
    check(value.present && std::abs(value.background.red-br/255.)<.00002 && std::abs(value.background.green-bg/255.)<.00002
        && std::abs(value.background.blue-bb/255.)<.00002 && std::abs(value.foreground.red-fr/255.)<.00002
        && std::abs(value.foreground.green-fg/255.)<.00002 && std::abs(value.foreground.blue-fb/255.)<.00002,name);
}
int main(int argc,char **argv) {
    QTemporaryDir root; check(root.isValid(),"temporary config");
    qputenv("XDG_CONFIG_HOME",root.path().toUtf8());
    qputenv("XDG_CONFIG_DIRS",root.path().toUtf8());
    const auto path=root.filePath(QStringLiteral("kdeglobals"));
    fixture(path,"distinct.colors");
    QApplication app(argc,argv);
    dc_palette out{}; out.generation=123;
    dc_kde_extract(&out);
    check(out.generation==123,"metadata untouched");
    pair(out.colors[DC_WINDOW],11,12,13,211,212,213,"Window native pair");
    pair(out.colors[DC_VIEW],21,22,23,221,222,223,"View native pair");
    pair(out.colors[DC_SELECTION],31,32,33,231,232,233,"Selection native pair");
    pair(out.colors[DC_POPOVER],41,42,43,241,242,243,"Tooltip native pair");
    pair(out.colors[DC_DIALOG],11,12,13,211,212,213,"Dialog uses Window");
    check(out.appearance==DC_DARK,"actual KDE polarity");
    check(!out.colors[DC_SIDEBAR].present,"Sidebar absent");
    check(!out.colors[DC_HEADER].present,"Header absent without theme evidence");
    fixture(path,"incomplete-header.colors"); dc_kde_extract(&out);
    check(!out.colors[DC_HEADER].present,"Header absent without a complete authored pair");
    fixture(path,"header.colors"); dc_kde_extract(&out);
#if DC_HAVE_KCOLORSCHEME_HEADER
    pair(out.colors[DC_HEADER],51,52,53,251,252,253,"Header requires API and theme support");
#else
    check(!out.colors[DC_HEADER].present,"Header absent without API support");
#endif
    pair(out.colors[DC_WINDOW],61,62,63,201,202,203,"same process reparses changed override");
    fixture(path,"high-contrast.colors"); dc_kde_extract(&out);
    pair(out.colors[DC_WINDOW],255,255,255,0,0,0,"high contrast light Window");
    check(out.appearance==DC_LIGHT,"changed actual polarity");
    check(!out.colors[DC_HEADER].present,"removed Header not retained");
    fixture(path,"translucent.colors"); dc_kde_extract(&out);
    check(out.colors[DC_VIEW].present && std::abs(out.colors[DC_VIEW].foreground.alpha-.5)<.003,"translucent ink retained");
    fixture(path,"invalid.colors"); dc_kde_extract(&out);
    check(out.colors[DC_WINDOW].present,"missing KDE keys yield toolkit default rather than guessed absence");
    // KColorScheme may replace malformed config entries with native defaults;
    // inject invalid brushes at the actual shared conversion boundary instead.
    dc_colors invalid{};
    check(!dc_qt_brush_pair(QBrush(QColor()),QBrush(Qt::white),&invalid) && !invalid.present,"invalid KDE color rejects pair");
    check(!dc_qt_brush_pair(QBrush(QLinearGradient(0,0,1,1)),QBrush(Qt::white),&invalid),"non-solid KDE brush rejects pair");
    out.colors[DC_WINDOW]=invalid; dc_qt_polarity(&out);
    check(out.appearance==DC_UNKNOWN,"unavailable Window is unknown");
    std::printf("KDE extraction: %d checks passed\n",checks);
}
