package com.chalkak.backend.common.logging;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuntimeSnapshotTest {

    @Test
    @DisplayName("로그 필드는 측정된 값만 선언 순서대로 담는다")
    void logFields_someValuesAbsent_keepsOrderAndOmitsNulls() {
        // Given
        RuntimeSnapshot snapshot = new RuntimeSnapshot(400L, null, 42L, 40, null, 1);

        // When
        var fields = snapshot.logFields();

        // Then
        assertThat(fields).containsExactly(
                entry("heapUsedBytes", 400L),
                entry("gcTimeMsTotal", 42L),
                entry("threads", 40),
                entry("hikariPending", 1));
    }
}
