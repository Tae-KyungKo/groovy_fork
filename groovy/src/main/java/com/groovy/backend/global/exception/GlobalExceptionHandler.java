package com.groovy.backend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.groovy.backend.common.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.of("FAIL", e.getMessage()));
	}

	// 존재하지 않는 enum 값, 잘못된 날짜/시간 형식 등 JSON 역직렬화 자체가 실패하는 경우.
	// Bean Validation(MethodArgumentNotValidException) 이전 단계라 별도로 잡아야 500으로 새지 않는다.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.of("FAIL", "요청 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ApiResponse.of("FAIL", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse("유효하지 않은 요청입니다.");

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.of("FAIL", message));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.of("ERROR", "서버 내부 오류가 발생했습니다."));
	}
}
