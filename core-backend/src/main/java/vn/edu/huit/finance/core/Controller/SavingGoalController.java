package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.SavingGoalDto;
import vn.edu.huit.finance.core.Entity.SavingGoal;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.SavingGoalRepository;
import vn.edu.huit.finance.core.Repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/savings")
public class SavingGoalController {

    @Autowired
    private SavingGoalRepository savingGoalRepo;

    @Autowired
    private UserRepository userRepository;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.findByEmail(username).orElseThrow());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavingGoalDto>>> getMySavings() {
        List<SavingGoal> goals = savingGoalRepo.findByUserIdOrderByCreatedAtDesc(currentUser().getId());
        List<SavingGoalDto> data = goals.stream().map(g -> SavingGoalDto.builder()
                .id(g.getId()).title(g.getTitle()).target(g.getTargetAmount())
                .current(g.getCurrentAmount()).deadline(g.getDeadline()).build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<SavingGoalDto>>builder().code(200).message("OK").data(data).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createSaving(@RequestBody SavingGoalDto dto) {
        SavingGoal goal = new SavingGoal();
        goal.setTitle(dto.getTitle());
        goal.setTargetAmount(dto.getTarget());
        goal.setCurrentAmount(dto.getCurrent());
        goal.setDeadline(dto.getDeadline());
        goal.setUser(currentUser());
        savingGoalRepo.save(goal);
        return ResponseEntity.ok(ApiResponse.<String>builder().code(200).message("Thêm thành công").build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> updateCurrentAmount(@PathVariable Long id, @RequestBody SavingGoalDto dto) {
        SavingGoal goal = savingGoalRepo.findById(id).orElseThrow();
        if (!goal.getUser().getId().equals(currentUser().getId())) throw new RuntimeException("Không có quyền");
        goal.setCurrentAmount(dto.getCurrent());
        savingGoalRepo.save(goal);
        return ResponseEntity.ok(ApiResponse.<String>builder().code(200).message("Cập nhật thành công").build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteSaving(@PathVariable Long id) {
        SavingGoal goal = savingGoalRepo.findById(id).orElseThrow();
        if (!goal.getUser().getId().equals(currentUser().getId())) throw new RuntimeException("Không có quyền");
        savingGoalRepo.delete(goal);
        return ResponseEntity.ok(ApiResponse.<String>builder().code(200).message("Xóa thành công").build());
    }
}