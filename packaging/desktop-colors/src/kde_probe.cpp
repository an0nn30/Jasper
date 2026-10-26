// SPDX-License-Identifier: MIT
#include "qt_probe.h"
#include <KColorScheme>
#include <KConfigGroup>
#include <KSharedConfig>
#include <cstring>
#if QT_VERSION_MAJOR == 5
#include <kconfigwidgets_version.h>
#define DC_KF_VERSION KCONFIGWIDGETS_VERSION_STRING
#else
#include <kcolorscheme_version.h>
#define DC_KF_VERSION KCOLORSCHEME_VERSION_STRING
#endif

const char *dc_kde_compiled_version() { return DC_KF_VERSION; }

static void pair(const KSharedConfig::Ptr &config,KColorScheme::ColorSet set,dc_colors *out) {
    const KColorScheme scheme(QPalette::Active,set,config);
    dc_qt_brush_pair(scheme.background(KColorScheme::NormalBackground),scheme.foreground(KColorScheme::NormalText),out);
}

void dc_kde_extract(dc_palette *out) {
    std::memset(out->colors,0,sizeof out->colors);
    // KConfig owns precedence and parsing. Opening the system cascade never
    // activates a named scheme or marks entries dirty for synchronization.
    const auto config=KSharedConfig::openConfig(QStringLiteral("kdeglobals"),KConfig::FullConfig);
    config->reparseConfiguration();
    pair(config,KColorScheme::Window,&out->colors[DC_WINDOW]);
    pair(config,KColorScheme::View,&out->colors[DC_VIEW]);
    pair(config,KColorScheme::Tooltip,&out->colors[DC_POPOVER]);
    pair(config,KColorScheme::Selection,&out->colors[DC_SELECTION]);
    out->colors[DC_DIALOG]=out->colors[DC_WINDOW];
#if DC_HAVE_KCOLORSCHEME_HEADER
    const auto header=config->group(QStringLiteral("Colors:Header"));
    if(header.hasKey("BackgroundNormal") && header.hasKey("ForegroundNormal"))
        pair(config,KColorScheme::Header,&out->colors[DC_HEADER]);
#endif
    dc_qt_polarity(out);
}
