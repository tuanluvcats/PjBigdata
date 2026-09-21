package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.NotificationResponseDto;
import vn.edu.huit.finance.core.Service.NotificationService;

import java.util.List;

/** The caller's own notifications. The owner is taken from the JWT inside NotificationService; nothing here reads a user id. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String MSG_LIST_OK = "Lấy danh sách thông báo thành công";

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getMyNotifications() {
        return ApiResponses.respond(HttpStatus.OK, MSG_LIST_OK, notificationService.getMyNotifications());
    }
}
