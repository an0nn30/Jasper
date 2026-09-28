package dev.jasper.editor.vim;

import java.util.List;

/**
 * Every Ex command name Vim 9.1 knows, in the order of its command table (ex_cmds.h), and IdeaVim's
 * {@code :sethandler}, {@code :action} and {@code :actionlist} after them. A typed name stands for the
 * first command in this order that starts with it, as Vim's find_ex_command resolves abbreviations: {@code :s} is
 * {@code :substitute} and {@code :d} is {@code :delete}. Jasper implements some of them ({@link Ex}); the others fail
 * with E319.
 */
final class ExTable {
    private ExTable() { }

    /** The names, in Vim's order. */
    static final List<String> NAMES = List.of(
        "append", "abbreviate", "abclear", "aboveleft", "abstract", "all", "amenu", "anoremenu", "args", "argadd",
        "argdelete", "argdo", "argdedupe", "argedit", "argglobal", "arglocal", "argument", "ascii", "autocmd",
        "augroup", "aunmenu", "buffer", "bNext", "ball", "badd", "balt", "bdelete", "behave", "belowright", "bfirst",
        "blast", "bmodified", "bnext", "botright", "bprevious", "brewind", "break", "breakadd", "breakdel",
        "breaklist", "browse", "buffers", "bufdo", "bunload", "bwipeout", "change", "cNext", "cNfile", "cabbrev",
        "cabclear", "cabove", "caddbuffer", "caddexpr", "caddfile", "cafter", "call", "catch", "cbuffer", "cbefore",
        "cbelow", "cbottom", "cc", "cclose", "cd", "cdo", "center", "cexpr", "cfile", "cfdo", "cfirst", "cgetfile",
        "cgetbuffer", "cgetexpr", "chdir", "changes", "checkpath", "checktime", "chistory", "clist", "clipreset",
        "clast", "class", "close", "clearjumps", "cmap", "cmapclear", "cmenu", "cnext", "cnewer", "cnfile",
        "cnoremap", "cnoreabbrev", "cnoremenu", "copy", "colder", "colorscheme", "command", "comclear", "compiler",
        "continue", "confirm", "const", "copen", "cprevious", "cpfile", "cquit", "crewind", "cscope", "cstag",
        "cunmap", "cunabbrev", "cunmenu", "cwindow", "delete", "delmarks", "debug", "debuggreedy", "def",
        "defcompile", "defer", "delcommand", "delfunction", "display", "diffupdate", "diffget", "diffoff",
        "diffpatch", "diffput", "diffsplit", "diffthis", "digraphs", "disassemble", "djump", "dlist", "doautocmd",
        "doautoall", "drop", "dsearch", "dsplit", "edit", "earlier", "echo", "echoerr", "echohl", "echomsg",
        "echoconsole", "echon", "echowindow", "else", "elseif", "emenu", "endif", "endinterface", "endclass",
        "enddef", "endenum", "endfunction", "endfor", "endtry", "endwhile", "enew", "enum", "eval", "ex", "execute",
        "exit", "export", "exusage", "file", "files", "filetype", "filter", "find", "final", "finally", "finish",
        "first", "fixdel", "fold", "foldclose", "folddoopen", "folddoclosed", "foldopen", "for", "function",
        "global", "goto", "grep", "grepadd", "gui", "gvim", "help", "helpclose", "helpfind", "helpgrep", "helptags",
        "hardcopy", "highlight", "hide", "history", "horizontal", "insert", "iabbrev", "iabclear", "if", "ijump",
        "ilist", "imap", "imapclear", "imenu", "import", "inoremap", "inoreabbrev", "inoremenu", "intro",
        "interface", "iput", "isearch", "isplit", "iunmap", "iunabbrev", "iunmenu", "join", "jumps", "k",
        "keepmarks", "keepjumps", "keeppatterns", "keepalt", "list", "lNext", "lNfile", "last", "labove", "language",
        "laddexpr", "laddbuffer", "laddfile", "lafter", "later", "lbuffer", "lbefore", "lbelow", "lbottom", "lcd",
        "lchdir", "lclose", "lcscope", "ldo", "left", "leftabove", "let", "lexpr", "legacy", "lfile", "lfdo",
        "lfirst", "lgetfile", "lgetbuffer", "lgetexpr", "lgrep", "lgrepadd", "lhelpgrep", "lhistory", "ll", "llast",
        "llist", "lmap", "lmapclear", "lmake", "lnoremap", "lnext", "lnewer", "lnfile", "loadview", "loadkeymap",
        "lockmarks", "lockvar", "lolder", "lopen", "lprevious", "lpfile", "lrewind", "ltag", "lunmap", "lua",
        "luado", "luafile", "lvimgrep", "lvimgrepadd", "lwindow", "ls", "move", "mark", "make", "map", "mapclear",
        "marks", "match", "menu", "menutranslate", "messages", "mkexrc", "mksession", "mkspell", "mkvimrc", "mkview",
        "mode", "mzscheme", "mzfile", "next", "nbkey", "nbclose", "nbstart", "new", "nmap", "nmapclear", "nmenu",
        "nnoremap", "nnoremenu", "noremap", "noautocmd", "nohlsearch", "noreabbrev", "noremenu", "noswapfile",
        "normal", "number", "nunmap", "nunmenu", "open", "oldfiles", "omap", "omapclear", "omenu", "only",
        "onoremap", "onoremenu", "options", "ounmap", "ounmenu", "ownsyntax", "print", "packadd", "packloadall",
        "pbuffer", "pclose", "perl", "perldo", "pedit", "pop", "popup", "ppop", "preserve", "previous", "promptfind",
        "promptrepl", "profile", "profdel", "psearch", "ptag", "ptNext", "ptfirst", "ptjump", "ptlast", "ptnext",
        "ptprevious", "ptrewind", "ptselect", "put", "public", "pwd", "python", "pydo", "pyfile", "py3", "py3do",
        "python3", "py3file", "pyx", "pyxdo", "pythonx", "pyxfile", "quit", "quitall", "qall", "read", "recover",
        "redo", "redir", "redraw", "redrawstatus", "redrawtabline", "redrawtabpanel", "registers", "resize", "retab",
        "return", "rewind", "right", "rightbelow", "runtime", "ruby", "rubydo", "rubyfile", "rundo", "rviminfo",
        "substitute", "sNext", "sargument", "sall", "sandbox", "saveas", "sbuffer", "sbNext", "sball", "sbfirst",
        "sblast", "sbmodified", "sbnext", "sbprevious", "sbrewind", "scriptnames", "scriptencoding", "scriptversion",
        "scscope", "set", "setfiletype", "setglobal", "setlocal", "sfind", "sfirst", "shell", "simalt", "sign",
        "silent", "sleep", "slast", "smagic", "smap", "smapclear", "smenu", "snext", "snomagic", "snoremap",
        "snoremenu", "source", "sort", "split", "spellgood", "spelldump", "spellinfo", "spellrepall", "spellrare",
        "spellundo", "spellwrong", "sprevious", "srewind", "stop", "stag", "startinsert", "startgreplace",
        "startreplace", "static", "stopinsert", "stjump", "stselect", "sunhide", "sunmap", "sunmenu", "suspend",
        "sview", "swapname", "syntax", "syntime", "syncbind", "smile", "t", "tNext", "tag", "tags", "tab",
        "tabclose", "tabdo", "tabedit", "tabfind", "tabfirst", "tabmove", "tablast", "tabnext", "tabnew", "tabonly",
        "tabprevious", "tabNext", "tabrewind", "tabs", "tcd", "tchdir", "tcl", "tcldo", "tclfile", "tearoff",
        "terminal", "tfirst", "throw", "this", "tjump", "tlast", "tlmenu", "tlnoremenu", "tlunmenu", "tmenu", "tmap",
        "tmapclear", "tnext", "tnoremap", "topleft", "tprevious", "trewind", "try", "tselect", "tunmenu", "tunmap",
        "type", "undo", "undojoin", "undolist", "unabbreviate", "unhide", "uniq", "unlet", "unlockvar", "unmap",
        "unmenu", "unsilent", "update", "vglobal", "var", "version", "verbose", "vertical", "visual", "view",
        "vimgrep", "vimgrepadd", "vim9cmd", "vim9script", "viusage", "vmap", "vmapclear", "vmenu", "vnoremap",
        "vnew", "vnoremenu", "vsplit", "vunmap", "vunmenu", "write", "wNext", "wall", "while", "winsize", "wincmd",
        "windo", "winpos", "wlrestore", "wnext", "wprevious", "wq", "wqall", "wundo", "wviminfo", "xit", "xall",
        "xmap", "xmapclear", "xmenu", "xnoremap", "xnoremenu", "xrestore", "xunmap", "xunmenu", "yank", "z", "!",
        "#", "&", "*", "<", "=", ">", "@", "{", "}", "~", "Next", "Print", "X", "++", "--",
        // IdeaVim's own, after Vim's so that no Vim abbreviation changes.
        "sethandler", "action", "actionlist");

    /**
     * @param typed a command name as typed, perhaps abbreviated
     * @return the full name of the first command it abbreviates, or null
     */
    static String resolve(String typed) {
        for (String name : NAMES) if (name.startsWith(typed)) return name;
        return null;
    }
}
