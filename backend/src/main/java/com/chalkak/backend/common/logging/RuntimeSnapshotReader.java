package com.chalkak.backend.common.logging;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JDK 관리 빈과 Hikari 풀 관리 빈에서 {@link RuntimeSnapshot}을 직접 읽는다.
 *
 * <p>
 * Micrometer 레지스트리를 거치지 않는다. 운영 레지스트리는 필터로 미터를 걸러 낼 수 있어,
 * 레지스트리에서 읽으면 필터 설정 하나로 이 로그의 값이 조용히 사라진다.
 */
@Component
@ConditionalOnProperty(prefix = "chalkak.monitoring.runtime-log", name = "enabled", havingValue = "true")
public class RuntimeSnapshotReader {

    private static final long UNDEFINED = -1;

    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMXBeans;
    private final ThreadMXBean threadMXBean;
    private final DataSource dataSource;

    @Autowired
    public RuntimeSnapshotReader(DataSource dataSource) {
        this(
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getGarbageCollectorMXBeans(),
                ManagementFactory.getThreadMXBean(),
                dataSource);
    }

    // 테스트에서 관리 객체를 바꿔 넣기 위해 생성자를 하나 더 둔다.
    RuntimeSnapshotReader(
            MemoryMXBean memoryMXBean,
            List<GarbageCollectorMXBean> garbageCollectorMXBeans,
            ThreadMXBean threadMXBean,
            DataSource dataSource
    ) {
        this.memoryMXBean = memoryMXBean;
        this.garbageCollectorMXBeans = garbageCollectorMXBeans;
        this.threadMXBean = threadMXBean;
        this.dataSource = dataSource;
    }

    public RuntimeSnapshot read() {
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        HikariPoolMXBean pool = hikariPool();
        return new RuntimeSnapshot(
                heap.getUsed(),
                definedOrNull(heap.getMax()),
                gcTimeMsTotal(),
                threadMXBean.getThreadCount(),
                pool == null ? null : pool.getActiveConnections(),
                pool == null ? null : pool.getThreadsAwaitingConnection());
    }

    private Long gcTimeMsTotal() {
        long total = 0;
        boolean found = false;
        for (GarbageCollectorMXBean collector : garbageCollectorMXBeans) {
            long time = collector.getCollectionTime();
            if (time == UNDEFINED) {
                continue;
            }
            total += time;
            found = true;
        }
        if (!found) {
            return null;
        }
        return total;
    }

    private Long definedOrNull(long value) {
        if (value == UNDEFINED) {
            return null;
        }
        return value;
    }

    /** Hikari 풀이 아니거나 아직 시작 전이면 null이다. */
    private HikariPoolMXBean hikariPool() {
        try {
            if (!dataSource.isWrapperFor(HikariDataSource.class)) {
                return null;
            }
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        } catch (SQLException e) {
            return null;
        }
    }
}
