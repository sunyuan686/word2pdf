package com.suny.word2pdf.exception;

import com.suny.word2pdf.dto.ConversionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 增强的全局异常处理器
 * 
 * 处理各种转换异常、网络异常和系统异常
 * 特别优化了 Content-Type 冲突和客户端连接中断的情况
 * 
 * @author suny
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理客户端连接中断异常
     * 
     * @param e 客户端中断异常
     * @param request HTTP 请求
     * @param response HTTP 响应
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e, 
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        log.warn("Client aborted connection during {} {}: {}", 
                method, requestUri, e.getMessage());
        
        // 客户端已断开连接，不需要返回响应
        // 只记录日志便于监控和调试
        
        // 记录请求的详细信息用于分析
        logRequestDetails(request, "CLIENT_ABORT");
    }
    
    /**
     * 处理 HTTP 消息转换异常（Content-Type 冲突）
     * 
     * @param e HTTP 消息转换异常
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 错误响应
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<?> handleHttpMessageNotWritableException(
            HttpMessageNotWritableException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        String requestUri = request.getRequestURI();
        log.error("HTTP message conversion failed for {}: {}", requestUri, e.getMessage());
        
        // 检查是否是 Content-Type 冲突
        String contentType = response.getContentType();
        boolean isPdfContentType = contentType != null && contentType.contains("application/pdf");
        
        if (isPdfContentType) {
            // 如果已设置为 PDF Content-Type，说明这是转换过程中的异常
            // 尝试重置响应并返回 JSON 错误信息
            try {
                response.reset();
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                
                Map<String, Object> errorResponse = createErrorResponse(
                    "CONVERSION_ERROR",
                    "Document conversion failed during processing",
                    requestUri
                );
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorResponse);
                        
            } catch (Exception resetException) {
                log.error("Failed to reset response after Content-Type conflict", resetException);
                // 如果无法重置响应，返回简单的错误信息
                return createFallbackErrorResponse();
            }
        } else {
            // 普通的消息转换异常
            Map<String, Object> errorResponse = createErrorResponse(
                "MESSAGE_CONVERSION_ERROR", 
                e.getMessage(),
                requestUri
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }
    }
    
    /**
     * 处理文件上传大小超限异常
     * 
     * @param e 异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e,
                                                         HttpServletRequest request) {
        
        log.error("File upload size exceeded for {}: {}", request.getRequestURI(), e.getMessage());
        
        // 根据请求路径决定返回格式
        if (isApiRequest(request)) {
            ConversionResult result = ConversionResult.failure("Unknown", 0L, 
                    "File size exceeds maximum limit");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } else {
            Map<String, Object> errorResponse = createErrorResponse(
                "FILE_TOO_LARGE",
                "File size exceeds maximum limit", 
                request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }
    }
    
    /**
     * 处理运行时异常
     * 
     * @param e 异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e,
                                                   HttpServletRequest request) {
        
        log.error("Runtime exception occurred for {}: {}", request.getRequestURI(), e.getMessage(), e);
        
        // 记录详细的请求信息
        logRequestDetails(request, "RUNTIME_EXCEPTION");
        
        // 根据请求路径决定返回格式
        if (isApiRequest(request)) {
            ConversionResult result = ConversionResult.failure("Unknown", 0L, 
                    "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } else {
            Map<String, Object> errorResponse = createErrorResponse(
                "RUNTIME_ERROR",
                "Internal server error: " + e.getMessage(),
                request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }
    }
    
    /**
     * 处理通用异常
     * 
     * @param e 异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception e,
                                                   HttpServletRequest request) {
        
        log.error("Unexpected exception occurred for {}: {}", request.getRequestURI(), e.getMessage(), e);
        
        // 记录详细的请求信息
        logRequestDetails(request, "GENERIC_EXCEPTION");
        
        // 根据请求路径决定返回格式
        if (isApiRequest(request)) {
            ConversionResult result = ConversionResult.failure("Unknown", 0L, 
                    "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } else {
            Map<String, Object> errorResponse = createErrorResponse(
                "UNEXPECTED_ERROR",
                "Unexpected error: " + e.getMessage(),
                request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse);
        }
    }
    
    /**
     * 处理 IO 异常（包括网络中断）
     * 
     * @param e IO 异常
     * @param request HTTP 请求
     * @return 错误响应
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleIOException(IOException e,
                                              HttpServletRequest request) {
        
        String requestUri = request.getRequestURI();
        
        // 判断是否为客户端连接中断
        if (e.getMessage() != null && 
            (e.getMessage().contains("Broken pipe") || 
             e.getMessage().contains("Connection reset"))) {
            
            log.warn("Client connection interrupted for {}: {}", requestUri, e.getMessage());
            
            // 客户端连接中断，不返回响应
            return null;
        }
        
        log.error("IO exception occurred for {}: {}", requestUri, e.getMessage(), e);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "IO_ERROR",
            "IO operation failed: " + e.getMessage(),
            requestUri
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }
    
    /**
     * 创建标准化的错误响应
     * 
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param path 请求路径
     * @return 错误响应 Map
     */
    private Map<String, Object> createErrorResponse(String errorCode, String message, String path) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put("error", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("path", path);
        return errorResponse;
    }
    
    /**
     * 创建降级错误响应（当无法重置响应时使用）
     * 
     * @return 简单的错误响应
     */
    private ResponseEntity<?> createFallbackErrorResponse() {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "INTERNAL_ERROR");
        errorResponse.put("message", "An internal error occurred during request processing");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Internal Server Error");
    }
    
    /**
     * 判断是否为 API 请求
     * 
     * @param request HTTP 请求
     * @return 是否为 API 请求
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.startsWith("/api/convert/") || 
               requestUri.startsWith("/convert/");
    }
    
    /**
     * 记录详细的请求信息用于调试
     * 
     * @param request HTTP 请求
     * @param exceptionType 异常类型
     */
    private void logRequestDetails(HttpServletRequest request, String exceptionType) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String userAgent = request.getHeader("User-Agent");
            String contentType = request.getContentType();
            String remoteAddr = request.getRemoteAddr();
            
            log.debug("Exception details [{}]: method={}, uri={}, query={}, userAgent={}, contentType={}, remoteAddr={}", 
                    exceptionType, method, uri, queryString, userAgent, contentType, remoteAddr);
                    
        } catch (Exception e) {
            log.debug("Failed to log request details: {}", e.getMessage());
        }
    }
} 