package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FileOperationTest {
    @Test void recursiveDeleteNeverFollowsLinksAndTransferReservationsBlockIt(@TempDir Path root) throws Exception {
        root=root.toRealPath();Path outside=Files.writeString(root.resolve("outside"),"keep"),directory=Files.createDirectory(root.resolve("folder"));
        Files.createSymbolicLink(directory.resolve("loop"),Path.of("."));Files.createSymbolicLink(directory.resolve("outside"),outside);Files.writeString(directory.resolve("file"),"delete");
        try(var endpoint=new LocalEndpoint()) {
            Path cache=root.resolve("cache");
            var reservations=new PathReservations();var id=UUID.randomUUID();
            try(var held=reservations.acquire(id,List.of(new PathReservations.Key("local",directory.toString(),false))).orElseThrow()) {
                Path selected=directory;
                assertThatThrownBy(()->FileOperationController.delete(endpoint,List.of(selected.toString()),reservations,new TransferControl(),cache,count->{})).isInstanceOf(java.io.IOException.class).hasMessageContaining("transfer");
            }
            var result=FileOperationController.delete(endpoint,List.of(directory.toString()),reservations,new TransferControl(),cache,count->{});
            assertThat(result.failures()).isZero();assertThat(directory).doesNotExist();assertThat(Files.readString(outside)).isEqualTo("keep");
        }
    }
    @Test void cancellationStopsFurtherDeletionAndKeepsRemainingFiles(@TempDir Path root) throws Exception {
        root=root.toRealPath();Path dir=Files.createDirectory(root.resolve("folder"));for(int i=0;i<100;i++) Files.writeString(dir.resolve("file-"+i),"data");
        try(var endpoint=new LocalEndpoint()) {
            var control=new TransferControl();var result=FileOperationController.delete(endpoint,List.of(dir.toString()),new PathReservations(),control,root.resolve("cache"),count->control.request(TransferJob.Intent.CANCEL));
            assertThat(result.cancelled()).isTrue();assertThat(result.completed()).isEqualTo(1);assertThat(dir).isDirectory();try(var files=Files.list(dir)) { assertThat(files.count()).isEqualTo(99); }
        }
    }

}
