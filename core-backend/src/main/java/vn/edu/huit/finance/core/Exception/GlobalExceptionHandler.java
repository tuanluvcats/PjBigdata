package vn.edu.huit.finance.core.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.huit.finance.core.Dto.ApiResponse;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {
    private boolean isJsonRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.contains("/ajax/");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        if (isJsonRequest(request)) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .code(400)
                    .message("Dữ liệu không hợp lệ: " + errorMessage)
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", errorMessage);
    }

    @ExceptionHandler(RuntimeException.class)
    public Object handleRuntimeExceptions(RuntimeException ex, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .code(400)
                    .message(ex.getMessage())
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Object handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return ResponseEntity.status(403).body(ApiResponse.<String>builder()
                    .code(403)
                    .message("Bảo mật: Bạn không có quyền (Role) để thực hiện hành động này!")
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", "Bạn không có quyền truy cập tính năng này.");
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public Object handleDataIntegrity(Exception ex, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return ResponseEntity.status(409).body(ApiResponse.<String>builder()
                    .code(409)
                    .message("Lỗi dữ liệu: Dữ liệu đã tồn tại hoặc đang vướng khóa ngoại (Đã có giao dịch).")
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", "Lỗi ràng buộc dữ liệu cơ sở dữ liệu.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleAllExceptions(Exception ex, HttpServletRequest request) {
        ex.printStackTrace();

        if (isJsonRequest(request)) {
            return ResponseEntity.status(500).body(ApiResponse.<String>builder()
                    .code(500)
                    .message("Hệ thống sự cố: " + ex.getMessage())
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", "Đã xảy ra lỗi hệ thống: " + ex.getMessage());
    }
}