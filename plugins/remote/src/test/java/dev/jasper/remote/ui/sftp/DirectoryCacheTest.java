package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** The directory cache sorts the whole folder by the chosen column, folders mixed in with everything else. */
class DirectoryCacheTest {
    static FileEntry file(String name,long size) { return new FileEntry(name,FileEntry.Kind.FILE,size,1000,0644,""); }
    static FileEntry folder(String name,long size) { return new FileEntry(name,FileEntry.Kind.DIRECTORY,size,1000,0755,""); }

    @Test void ordersByTheChosenColumnWithFoldersMixedIn(@TempDir Path root) throws Exception {
        try(var cache=new DirectoryCache(root)) {
            cache.append(List.of(file("b.txt",10),folder("A",0),folder("c",5),file("a.log",99)));
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("name",true))).extracting(FileEntry::name).containsExactly("A","a.log","b.txt","c");
            assertThat(cache.page(0,200)).as("the default order is the name, ascending").extracting(FileEntry::name).containsExactly("A","a.log","b.txt","c");
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("size",false))).extracting(FileEntry::name).containsExactly("a.log","b.txt","A","c");
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("name",false))).extracting(FileEntry::name).containsExactly("c","b.txt","a.log","A");
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("unknown",true))).as("an unknown column sorts by name")
                .extracting(FileEntry::name).containsExactly("A","a.log","b.txt","c");
        }
    }

    /** The size a Size sort compares: folders and unknown sizes, which show no size, below every file. */
    static long sizeKey(FileEntry entry) { return entry.kind()==FileEntry.Kind.DIRECTORY||entry.size()<0?-1:entry.size(); }

    @Test void sizeOrderGroupsFoldersAndUnknownSizesAtTheSmallEnd(@TempDir Path root) throws Exception {
        try(var cache=new DirectoryCache(root)) {
            cache.append(List.of(file("big",5000),folder("src",4096),file("empty",0),file("odd",-1),folder("bin",4096),file("mid",4096)));
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("size",true))).extracting(FileEntry::name)
                .containsExactly("bin","odd","src","empty","mid","big");
            assertThat(cache.page(0,200,false,true,new DirectoryCache.Order("size",false))).extracting(FileEntry::name)
                .containsExactly("big","mid","empty","bin","odd","src");
        }
    }

    @Test void secondPageContinuesTheOrderWithHiddenItemsOff(@TempDir Path root) throws Exception {
        var all=new ArrayList<FileEntry>();
        for(int i=0;i<450;i++) all.add(i%5==0?file(".dot-"+i,(i*37)%101):i%7==0?folder("dir-"+i,(i*13)%97):file("file-"+i,(i*37)%101));
        try(var cache=new DirectoryCache(root)) {
            for(int from=0;from<all.size();from+=256) cache.append(all.subList(from,Math.min(all.size(),from+256)));
            var order=new DirectoryCache.Order("size",false);var paged=new ArrayList<String>();
            for(long offset:List.of(0L,200L,400L)) paged.addAll(cache.page(offset,200,false,false,order).stream().map(FileEntry::name).toList());
            var expected=all.stream().filter(entry->!entry.name().startsWith("."))
                .sorted(Comparator.comparingLong(DirectoryCacheTest::sizeKey).reversed().thenComparing(FileEntry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(FileEntry::name))
                .map(FileEntry::name).toList();
            assertThat(paged).containsExactlyElementsOf(expected);
            assertThat((long)paged.size()).isEqualTo(cache.count(false,false));
        }
    }
}
