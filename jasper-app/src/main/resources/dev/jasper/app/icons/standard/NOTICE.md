# Host-owned modern icon catalog

LOCK.svg and UNLOCK.svg are unmodified copies of Jasper Vault's existing modern artwork.
They keep the plugin's established shapes when using the semantic SDK catalog.

Other icons are Tabler outline artwork from https://github.com/tabler/tabler-icons.
ADD, BOOKMARK, CLOSE, HISTORY, REFRESH, SEARCH and SETTINGS were copied unmodified from
Jasper's former bundled resources, which took Tabler commit
6d128ed935d4546607b1e4d5d08c8b27bdbe7758 with the currentColor stroke replaced by #6e6e6e;
the rest were downloaded unmodified at commit 0239805680a36bab4e1070529b6744924402d804.
Tabler is MIT licensed; the pinned upstream LICENSE is included as LICENSE.txt and covers
every Tabler icon here. No runtime download is performed.

assets.tsv records every original source path/URL and SHA-256 of unmodified SVG bytes.
