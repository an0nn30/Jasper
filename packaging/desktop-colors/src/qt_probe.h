// SPDX-License-Identifier: MIT
#ifndef JASPER_DESKTOP_QT_PROBE_H
#define JASPER_DESKTOP_QT_PROBE_H
#include "protocol.h"
#include <QApplication>
#include <QPalette>
bool dc_qt_brush_pair(const QBrush &, const QBrush &, dc_colors *);
void dc_qt_polarity(dc_palette *);
bool dc_qt_pair(const QPalette &, QPalette::ColorGroup, QPalette::ColorRole, QPalette::ColorRole, dc_colors *);
void dc_qt_extract(const QPalette &, dc_palette *);
void dc_kde_extract(dc_palette *);
const char *dc_kde_compiled_version();
int dc_qt_run(dc_source source, QApplication &app);
// Both production providers use this dispatch boundary. Extraction owns only
// the colors and actual appearance; the GUI-thread loop owns the wire envelope.
int dc_qt_run(dc_source source, QApplication &app, void (*extract)(dc_palette *));
int dc_qt_main(dc_source source, int argc, char **argv);
#endif
