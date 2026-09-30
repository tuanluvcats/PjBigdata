package vn.edu.huit.finance.core.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChange;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChangedEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

@Service
public class BackupService {
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final String dbName = "finance_db";

    private final String backupDir = System.getProperty("user.dir") + "/backups/";

    @Value("${postgres.bin.path:}")
    private String pgBinPath;

    private String getCommand(String cmd) {
        if (pgBinPath == null || pgBinPath.trim().isEmpty()) {
            return cmd;
        }
        if (!pgBinPath.endsWith("/") && !pgBinPath.endsWith("\\")) {
            return pgBinPath + File.separator + cmd;
        }
        return pgBinPath + cmd;
    }

    public boolean backupDatabase(String type) {
        try {
            String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = backupDir + type + "_backup_" + timeStamp + ".sql";
            File dir = new File(backupDir);
            if (!dir.exists()) dir.mkdirs();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    getCommand("pg_dump"),
                    "-U", dbUser,
                    "-F", "c",
                    "-b",
                    "-v",
                    "-f", fileName,
                    dbName
            );

            processBuilder.environment().put("PGPASSWORD", dbPassword);
            processBuilder.inheritIO();

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                auditLogService.logAction(getCurrentUser(), "CREATE_BACKUP", "Thực hiện sao lưu Database (Loại: " + type + ")");
            }
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean restoreDatabase(String fileName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    getCommand("pg_restore"),
                    "-U", dbUser,
                    "-d", dbName,
                    "-1",
                    "--clean",
                    "--if-exists",
                    backupDir + fileName
            );
            processBuilder.environment().put("PGPASSWORD", dbPassword);
            processBuilder.inheritIO();

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                auditLogService.logAction(getCurrentUser(), "RESTORE_DB", "Phục hồi Database từ file: " + fileName);
                // Every cached statistic now describes data that is gone.
                eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.DATABASE_RESTORED));
            }

            return exitCode == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String latestBackup(String type) {
        File dir = new File(backupDir);
        File[] files = dir.listFiles((d, n) -> n.startsWith(type) && n.endsWith(".sql"));
        if (files == null || files.length == 0) return null;
        return Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .map(File::getName)
                .orElse(null);
    }

    public boolean restoreLatest(String type) {
        String fileName = latestBackup(type);
        if (fileName == null) return false;
        return restoreDatabase(fileName);
    }

    private String getCurrentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "HỆ THỐNG";
    }
}