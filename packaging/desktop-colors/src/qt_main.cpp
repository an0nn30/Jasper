// SPDX-License-Identifier: MIT
#include "qt_probe.h"
int main(int argc,char **argv) {
    return dc_qt_main(QT_VERSION_MAJOR==5?DC_QT5:DC_QT6,argc,argv);
}
