# Project Index

Project Index is a bundled plugin that finds files in your projects from the command palette and opens them in the
Editor.

## Indexing a project

When a terminal's shell reports a folder inside a project, Jasper asks:

> **Index project?** app · ~/src/app — Git repository
> **Yes** · **No** · **Ignore this path**

- **Yes** lists the project's files and remembers the project.
- **No** stops asking about that project until Jasper restarts. Closing the card counts as No.
- **Ignore this path** stops asking for good. The folder is added to **Ignored paths** in Settings.

A folder is a project when it, or a folder above it, holds a marker such as `.git`, `Cargo.toml`, `pyproject.toml`,
`package.json` or a `*.sln` file. The nearest folder with a strong marker (`.git`, `.hg`, `.svn`, `*.sln`) wins;
otherwise the nearest folder with any marker does. Your home folder is never a project. The shell must report its
folder, which Jasper's shell integration does for zsh, bash and fish.

To index a folder that is not a project, choose **File › Index Folder…**, or **Add Folder…** on the plugin's Settings
page.

## Finding files

Press **⌘P** (**Ctrl+Alt+Shift+P** on Linux and Windows), or open the palette and choose the **Files** tab. Files
from every indexed project are searched; the project you are in comes first. Type part of a name, its initials
(`fsc` finds `FilesScope.java`), or folders and a name separated by `/` (`ui/Files`).

- **Enter** opens the file in the Editor.
- **⌘Enter** (**Ctrl+Enter**) copies its path.
- **⇧Enter** opens a terminal tab in its folder.

## What gets indexed

In a Git work tree, the files Git tracks plus untracked files it does not ignore. Elsewhere, every file, skipping
folders named in **Excluded folders** (`node_modules`, `target`, `build`, `.venv` and others). Excluded folders are
skipped in Git work trees too. A project stops at **Files indexed per project** (200,000 by default); Jasper tells you
when one is cut short.

## Keeping indexes fresh

Indexes are saved and survive restarts. A project is refreshed in the background when a terminal enters it again and
when a command finishes in it, at most once every **Minutes between background refreshes** (5 by default). Choose
**Reindex Current Project** from the palette, or **Reindex** in Settings, to refresh at once.

## Settings

**Settings › Plugins › Project Index** lists the indexed projects (with their file counts, when they were last indexed
and their state) and holds the markers, excluded folders, ignored paths and limits. The same values live in
`plugins/dev.jasper.indexer/dev.jasper.indexer.toml` under Jasper's home; the project list is
`plugins/dev.jasper.indexer/data/projects.toml`.
