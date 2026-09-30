package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChange;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChangedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** A restore replaces every row the cached statistics were computed from. pg_restore is a stand-in script here. */
@ExtendWith(MockitoExtension.class)
@EnabledOnOs({OS.LINUX, OS.MAC})
class BackupServiceRestoreTest {

    @TempDir private Path pgBin;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private BackupService service;

    private void pgRestoreExitsWith(int exitCode) throws IOException {
        Path script = pgBin.resolve("pg_restore");
        Files.writeString(script, "#!/bin/sh\nexit " + exitCode + "\n");
        assertTrue(script.toFile().setExecutable(true));
        ReflectionTestUtils.setField(service, "pgBinPath", pgBin.toString());
        ReflectionTestUtils.setField(service, "dbUser", "finance");
        ReflectionTestUtils.setField(service, "dbPassword", "test-only");
    }

    @Test
    void aRestoreThatSucceededEmptiesEveryStatisticsCache() throws IOException {
        pgRestoreExitsWith(0);

        assertTrue(service.restoreDatabase("FULL_backup_20260930_120000.sql"));

        verify(eventPublisher).publishEvent(StatisticsChangedEvent.of(StatisticsChange.DATABASE_RESTORED));
    }

    @Test
    void aRestoreThatFailedChangedNothingAndEvictsNothing() throws IOException {
        pgRestoreExitsWith(1);

        assertFalse(service.restoreDatabase("FULL_backup_20260930_120000.sql"));

        verifyNoInteractions(eventPublisher);
    }
}
