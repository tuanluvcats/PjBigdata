package vn.edu.huit.finance.core.Repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.huit.finance.core.Entity.ActionLog;

import java.util.List;
import java.util.Map;

@Repository
public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
    List<ActionLog> findTop200ByActionContainingOrderByTimestampDesc(String keyword);

    List<ActionLog> findTop200ByActionNotContainingOrderByTimestampDesc(String keyword);

    @Query("SELECT a.userEmail as dbUser, 'HỆ THỐNG' as tableName, a.action as actionType, COUNT(a.id) as totalActions, MAX(a.timestamp) as lastAction " +
            "FROM ActionLog a GROUP BY a.userEmail, a.action ORDER BY totalActions DESC, lastAction DESC")
    List<Map<String, Object>> getAuditSummary();
}