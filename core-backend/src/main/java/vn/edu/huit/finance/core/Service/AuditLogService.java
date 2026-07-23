package vn.edu.huit.finance.core.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.edu.huit.finance.core.Entity.ActionLog;
import vn.edu.huit.finance.core.Repository.ActionLogRepository;

import java.time.LocalDateTime;

@Service
public class AuditLogService {
    @Autowired
    private ActionLogRepository logRepo;

    public void log(Long userId, String email, String action, String description, String ip) {
        ActionLog log = new ActionLog();
        log.setUserId(userId);
        log.setUserEmail(email);
        log.setAction(action);
        log.setDescription(description);
        log.setIpAddress(ip);
        logRepo.save(log);
    }

    public void logAction(String userEmail, String action, String description) {
        logAction(userEmail, action, description, null);
    }
    public void logAction(String userEmail, String action, String description, String ipAddress) {
        ActionLog log = new ActionLog();
        log.setUserEmail(userEmail != null && !userEmail.isEmpty() ? userEmail : "HỆ THỐNG_AUTO");
        log.setAction(action);
        log.setDescription(description);
        log.setIpAddress(ipAddress);
        log.setTimestamp(LocalDateTime.now());
        logRepo.save(log);
    }
}