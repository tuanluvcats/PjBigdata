package vn.edu.huit.finance.core.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Thiếu tham số bắt buộc: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public Object handleInvalidPassword(InvalidPasswordException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidOtpException.class)
    public Object handleInvalidOtp(InvalidOtpException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // 401: no usable token where one is required. A revoked token never gets this far, JwtAuthFilter answers it first.
    @ExceptionHandler(InvalidTokenException.class)
    public Object handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    public Object handleBudgetNotFound(BudgetNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateBudgetException.class)
    public Object handleDuplicateBudget(DuplicateBudgetException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // 400 rather than 404: the category is referenced from the request body, so it is the request that is invalid.
    @ExceptionHandler(InvalidCategoryException.class)
    public Object handleInvalidCategory(InvalidCategoryException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public Object handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({TransactionNotFoundException.class, ReceiptNotFoundException.class})
    public Object handleTransactionOrReceiptNotFound(RuntimeException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 403 and not 404: the caller is signed in and the record exists, but it belongs to somebody else.
    @ExceptionHandler(TransactionAccessDeniedException.class)
    public Object handleTransactionAccessDenied(TransactionAccessDeniedException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler({InsufficientBalanceException.class, InvalidRoleException.class, InvalidSortFieldException.class})
    public Object handleRejectedInput(RuntimeException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    private Object buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            return ResponseEntity.status(status).body(ApiResponse.<String>builder()
                    .code(status.value())
                    .message(message)
                    .build());
        }
        return new ModelAndView("error").addObject("errorMessage", message);
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