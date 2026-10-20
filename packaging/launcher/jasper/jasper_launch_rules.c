/* See jasper_launch_rules.h. */
#include "jasper_launch_rules.h"

#include <stddef.h>
#include <string.h>

static int startsWith(const char* value, const char* prefix) {
    return value != NULL && strncmp(value, prefix, strlen(prefix)) == 0;
}

int jasperAotCacheInUse(int count, const char* const* options) {
    int named = 0;
    int off = 0;
    int i;
    for (i = 0; i < count; ++i) {
        if (startsWith(options[i], JASPER_AOT_CACHE_OPTION)) {
            named = 1;
        } else if (startsWith(options[i], JASPER_AOT_MODE_OPTION)) {
            off = strcmp(options[i], JASPER_AOT_MODE_OPTION "off") == 0;
        }
    }
    return named && !off;
}

int jasperHasVmOptionsDigest(int count, const char* const* options) {
    int i;
    for (i = 0; i < count; ++i) {
        if (startsWith(options[i], JASPER_DIGEST_OPTION)) {
            return 1;
        }
    }
    return 0;
}

const char* jasperNextStartVariable(int count, const char* const* options, int cacheSkipped, int optionsSkipped) {
    if (!cacheSkipped && jasperAotCacheInUse(count, options)) {
        return JASPER_AOT_CACHE_SKIP_VARIABLE;
    }
    if (!optionsSkipped && jasperHasVmOptionsDigest(count, options)) {
        return JASPER_VM_OPTIONS_SKIP_VARIABLE;
    }
    return NULL;
}
