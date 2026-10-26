// SPDX-License-Identifier: MIT
#ifndef JASPER_DESKTOP_QT_PROBE_H
#define JASPER_DESKTOP_QT_PROBE_H
#include "protocol.h"
#include <QApplication>
#include <QPalette>
bool dc_qt_pair(const QPalette &, QPalette::ColorGroup, QPalette::ColorRole, QPalette::ColorRole, dc_colors *);
void dc_qt_extract(const QPalette &, dc_palette *);
int dc_qt_run(dc_source source, QApplication &app);
int dc_qt_main(dc_source source, int argc, char **argv);
#endif
