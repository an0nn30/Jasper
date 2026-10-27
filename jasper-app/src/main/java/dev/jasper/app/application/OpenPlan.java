package dev.jasper.app.application;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where an outside request's paths open (workspace-tabs spec 5.3, 5.4 and 6.4): in the most recently active window, each directory as a
 * terminal tab, each file in the editor a plugin provides; anything else is skipped and a repeated path opens once. With
 * no window open one opens first, in the first directory when there is one. Pure.
 *
 * @param newWindow whether a window opens first
 * @param windowDirectory where the new window's tab starts; null for where a new shell starts
 * @param tabs the directories that open as terminal tabs in the target window, in order
 * @param files the files that open in the target window, in order
 */
record OpenPlan(boolean newWindow, Path windowDirectory, List<Path> tabs, List<Path> files) {
    OpenPlan {
        tabs = List.copyOf(tabs);
        files = List.copyOf(files);
    }

    /**
     * Plans {@code paths}.
     *
     * @param paths absolute paths, in the order the request names them
     * @param windowOpen whether a window is open
     * @param directory whether a path is a directory
     * @param file whether a path is a regular file
     * @return the plan
     */
    static OpenPlan of(List<Path> paths, boolean windowOpen, Predicate<Path> directory, Predicate<Path> file) {
        List<Path> directories = paths.stream().distinct().filter(directory).toList();
        List<Path> files = paths.stream().distinct().filter(directory.negate()).filter(file).toList();
        if (windowOpen) return new OpenPlan(false, null, directories, files);
        if (directories.isEmpty()) return new OpenPlan(true, null, List.of(), files);
        return new OpenPlan(true, directories.getFirst(), directories.subList(1, directories.size()), files);
    }
}
