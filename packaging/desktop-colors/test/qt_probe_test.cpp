// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <QApplication>
#include <QLinearGradient>
#include <QPixmap>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
static int checks;
static void check(bool ok, const char *name) { ++checks; if (!ok) { std::fprintf(stderr,"FAIL: %s\n",name); std::exit(1); } }
int main(int argc, char **argv) {
    QApplication app(argc, argv);
    QPalette p;
    for (int g = 0; g < QPalette::NColorGroups; ++g)
        for (int r = 0; r < QPalette::NColorRoles; ++r)
            if (r != QPalette::NoRole) p.setColor(QPalette::ColorGroup(g), QPalette::ColorRole(r), QColor(20+r*7,30+g*60,40+r*3));
    p.setColor(QPalette::Active,QPalette::Window,QColor("#202020"));
    p.setColor(QPalette::Active,QPalette::WindowText,QColor("#eeeeee"));
    p.setColor(QPalette::Inactive,QPalette::ToolTipBase,QColor("#123456"));
    p.setColor(QPalette::Inactive,QPalette::ToolTipText,QColor("#ffffff"));
    dc_palette out{}; out.generation=123; out.implementation="owned elsewhere";
    dc_qt_extract(p,&out);
    check(out.generation==123 && std::string(out.implementation)=="owned elsewhere","metadata untouched");
    check(out.appearance==DC_DARK,"actual Window polarity");
    check(!out.colors[DC_HEADER].present && !out.colors[DC_SIDEBAR].present,"unsupported roles absent");
    const dc_pair pairs[]={DC_WINDOW,DC_VIEW,DC_DIALOG,DC_POPOVER,DC_SELECTION};
    const QPalette::ColorRole bg[]={QPalette::Window,QPalette::Base,QPalette::Window,QPalette::ToolTipBase,QPalette::Highlight};
    const QPalette::ColorRole fg[]={QPalette::WindowText,QPalette::Text,QPalette::WindowText,QPalette::ToolTipText,QPalette::HighlightedText};
    for(int i=0;i<5;++i) {
        auto group=i==3?QPalette::Inactive:QPalette::Active;
        auto b=p.color(group,bg[i]).toRgb(), f=p.color(group,fg[i]).toRgb();
        const auto &c=out.colors[pairs[i]];
        check(c.present,"mapped pair present");
        check(std::abs(c.background.red-b.redF())<1e-7 && std::abs(c.background.green-b.greenF())<1e-7 && std::abs(c.background.blue-b.blueF())<1e-7,"background mapping");
        check(std::abs(c.foreground.red-f.redF())<1e-7 && std::abs(c.foreground.green-f.greenF())<1e-7 && std::abs(c.foreground.blue-f.blueF())<1e-7,"foreground mapping");
    }
    QColor alpha=QColor::fromRgbF(.2,.3,.4,.375); p.setColor(QPalette::Active,QPalette::Text,alpha);
    dc_qt_extract(p,&out); check(std::abs(out.colors[DC_VIEW].foreground.alpha-alpha.alphaF())<1e-7,"fractional alpha retained");
    p.setBrush(QPalette::Active,QPalette::Base,QBrush(QLinearGradient(0,0,1,1)));
    dc_qt_extract(p,&out); check(!out.colors[DC_VIEW].present,"gradient rejects entire pair");
    p.setBrush(QPalette::Active,QPalette::Base,QBrush(QPixmap(2,2)));
    dc_qt_extract(p,&out); check(!out.colors[DC_VIEW].present,"texture rejects entire pair");
    p.setColor(QPalette::Active,QPalette::Base,QColor());
    dc_qt_extract(p,&out); check(!out.colors[DC_VIEW].present,"invalid QColor rejects pair");
    p.setColor(QPalette::Active,QPalette::Base,QColor::fromRgbF(1.2,.2,.3));
    dc_qt_extract(p,&out); check(!out.colors[DC_VIEW].present,"out-of-sRGB color rejects pair");
    p.setColor(QPalette::Active,QPalette::Base,QColor::fromHsv(120,255,255));
    dc_qt_extract(p,&out); check(out.colors[DC_VIEW].present && out.colors[DC_VIEW].background.green==1 && out.colors[DC_VIEW].background.red==0,"HSV converted through public RGB API");
    p.setColor(QPalette::Active,QPalette::Window,QColor::fromRgbF(.1,.1,.1,.5));
    dc_qt_extract(p,&out); check(out.appearance==DC_UNKNOWN && out.colors[DC_WINDOW].present,"translucent Window polarity unknown");
    p.setBrush(QPalette::Active,QPalette::Window,QBrush(Qt::NoBrush));
    dc_qt_extract(p,&out); check(!out.colors[DC_WINDOW].present && out.appearance==DC_UNKNOWN,"missing Window is unknown");
    p.setColor(QPalette::Active,QPalette::Window,QColor(Qt::white));
    p.setColor(QPalette::Active,QPalette::WindowText,QColor(Qt::black));
    dc_qt_extract(p,&out); check(out.appearance==DC_LIGHT,"light Window");
    std::printf("Qt extraction: %d checks passed\n",checks);
}
