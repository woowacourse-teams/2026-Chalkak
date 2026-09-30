package com.chalkak.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuntimeSnapshotReaderTest {

    private final MemoryMXBean memory = mock(MemoryMXBean.class);
    private final ThreadMXBean threads = mock(ThreadMXBean.class);
    private final DataSource dataSource = mock(DataSource.class);

    @Test
    @DisplayName("모든 원천이 있으면 GC 시간은 컬렉터별로 합산하되 정의되지 않은 값(-1)은 뺀 스냅샷을 만든다")
    void read_allSourcesPresent_sumsDefinedGcTimes() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, 1_000));
        given(threads.getThreadCount()).willReturn(42);
        givenHikariPool(3, 1);
        RuntimeSnapshotReader reader = new RuntimeSnapshotReader(
                memory,
                List.of(collector(30), collector(12), collector(-1)),
                threads,
                dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot).isEqualTo(new RuntimeSnapshot(400L, 1_000L, 42L, 42, 3, 1));
    }

    @Test
    @DisplayName("힙 상한이 정의되지 않으면(-1) 상한만 비어 있고 나머지는 채워진다")
    void read_undefinedHeapMax_leavesOnlyMaxAbsent() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, -1));
        given(threads.getThreadCount()).willReturn(42);
        givenHikariPool(3, 1);
        RuntimeSnapshotReader reader =
                new RuntimeSnapshotReader(memory, List.of(collector(30)), threads, dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot).isEqualTo(new RuntimeSnapshot(400L, null, 30L, 42, 3, 1));
    }

    @Test
    @DisplayName("GC 빈이 없으면 GC 시간이 비어 있다")
    void read_noGarbageCollectors_leavesGcTimeAbsent() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, 1_000));
        RuntimeSnapshotReader reader =
                new RuntimeSnapshotReader(memory, List.of(), threads, dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot.gcTimeMsTotal()).isNull();
    }

    @Test
    @DisplayName("Hikari가 아닌 DataSource면 Hikari 값이 비어 있다")
    void read_nonHikariDataSource_leavesHikariValuesAbsent() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, 1_000));
        given(dataSource.isWrapperFor(HikariDataSource.class)).willReturn(false);
        RuntimeSnapshotReader reader =
                new RuntimeSnapshotReader(memory, List.of(), threads, dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot.hikariActive()).isNull();
        assertThat(snapshot.hikariPending()).isNull();
    }

    @Test
    @DisplayName("Hikari 풀이 아직 시작 전이면 Hikari 값이 비어 있다")
    void read_poolNotStarted_leavesHikariValuesAbsent() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, 1_000));
        HikariDataSource hikari = mock(HikariDataSource.class);
        given(hikari.getHikariPoolMXBean()).willReturn(null);
        given(dataSource.isWrapperFor(HikariDataSource.class)).willReturn(true);
        given(dataSource.unwrap(HikariDataSource.class)).willReturn(hikari);
        RuntimeSnapshotReader reader =
                new RuntimeSnapshotReader(memory, List.of(), threads, dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot.hikariActive()).isNull();
        assertThat(snapshot.hikariPending()).isNull();
    }

    @Test
    @DisplayName("Hikari 풀을 꺼내다 SQLException이 나면 Hikari 값만 비어 있고 나머지는 채워진다")
    void read_unwrapThrowsSqlException_leavesOnlyHikariValuesAbsent() throws SQLException {
        // Given
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 400, 500, 1_000));
        given(threads.getThreadCount()).willReturn(42);
        given(dataSource.isWrapperFor(HikariDataSource.class)).willThrow(new SQLException());
        RuntimeSnapshotReader reader =
                new RuntimeSnapshotReader(memory, List.of(collector(30)), threads, dataSource);

        // When
        RuntimeSnapshot snapshot = reader.read();

        // Then
        assertThat(snapshot).isEqualTo(new RuntimeSnapshot(400L, 1_000L, 30L, 42, null, null));
    }

    private void givenHikariPool(int active, int pending) throws SQLException {
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        given(pool.getActiveConnections()).willReturn(active);
        given(pool.getThreadsAwaitingConnection()).willReturn(pending);
        HikariDataSource hikari = mock(HikariDataSource.class);
        given(hikari.getHikariPoolMXBean()).willReturn(pool);
        given(dataSource.isWrapperFor(HikariDataSource.class)).willReturn(true);
        given(dataSource.unwrap(HikariDataSource.class)).willReturn(hikari);
    }

    private GarbageCollectorMXBean collector(long collectionTime) {
        GarbageCollectorMXBean collector = mock(GarbageCollectorMXBean.class);
        given(collector.getCollectionTime()).willReturn(collectionTime);
        return collector;
    }
}
