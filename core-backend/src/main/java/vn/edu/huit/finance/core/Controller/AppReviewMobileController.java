package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Entity.AppReview;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.AppReviewRepository;
import vn.edu.huit.finance.core.Repository.UserRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class AppReviewMobileController {

    @Autowired
    private AppReviewRepository appReviewRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> submitReview(@RequestBody Map<String, Object> req) {
        try {
            Long userId = Long.valueOf(req.get("userId").toString());
            int rating = Integer.parseInt(req.get("rating").toString());
            String comment = req.get("comment").toString();

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

            AppReview review = new AppReview();
            review.setUser(user);
            review.setUserEmail(user.getEmail());
            review.setRating(rating);
            review.setComment(comment);
            review.setStatus("PENDING");

            appReviewRepository.save(review);

            return ResponseEntity.ok(Map.of("code", 200, "message", "Gửi đánh giá góp ý thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }
}