package vn.edu.huit.finance.core.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Entity.ActionLog;
import vn.edu.huit.finance.core.Repository.ActionLogRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonthlyJobService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActionLogRepository actionLogRepository;
    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional
    public void checkMonthlyBudgets() {
        System.out.println("=== [CRON JOB] ĐANG QUÉT TÀI CHÍNH THÁNG ===");


        List<User> usersAtRisk = userRepository.findByWalletBalanceLessThan(new BigDecimal("50000"));

        for (User user : usersAtRisk) {

            String warningMsg = "Cảnh báo tự động: Số dư ví của " + user.getEmail() + " đang ở mức nguy hiểm (" + user.getWalletBalance() + " VNĐ)";
            System.out.println(warningMsg);


            ActionLog log = new ActionLog();
            log.setUserEmail("HỆ THỐNG TỰ ĐỘNG");
            log.setAction("AUTO_WARNING");
            log.setDescription(warningMsg);
            log.setTimestamp(LocalDateTime.now());
            actionLogRepository.save(log);
        }

        System.out.println("=== [CRON JOB] HOÀN TẤT QUÉT ===");
    }
}