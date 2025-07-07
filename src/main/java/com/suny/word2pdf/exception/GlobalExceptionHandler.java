package com.suny.word2pdf.exception;

import com.suny.word2pdf.dto.ConversionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 
 * @author suny
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理文件上传大小超限异常
     * 
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ConversionResult> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("File upload size exceeded", e);
        ConversionResult result = ConversionResult.failure("Unknown", 0L, "File size exceeds maximum limit");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(result);
    }
    
    /**
     * 处理运行时异常
     * 
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ConversionResult> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception occurred", e);
        ConversionResult result = ConversionResult.failure("Unknown", 0L, "Internal server error: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
    
    /**
     * 处理通用异常
     * 
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ConversionResult> handleGenericException(Exception e) {
        log.error("Unexpected exception occurred", e);
        ConversionResult result = ConversionResult.failure("Unknown", 0L, "Unexpected error: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
} 