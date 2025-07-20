package com.suny.word2pdf.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web 配置类
 * 
 * 配置请求拦截器、响应头管理等Web相关功能
 * 特别处理文件转换过程中的连接管理和异常预防
 * 
 * @author suny
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加转换请求监控拦截器
        registry.addInterceptor(new ConversionRequestInterceptor())
                .addPathPatterns("/api/convert/**", "/convert/**")
                .excludePathPatterns("/api/convert/converters", "/api/convert/history");
    }

    /**
     * 转换请求监控拦截器
     * 
     * 监控转换请求的生命周期，预防连接中断问题
     */
    @Slf4j
    static class ConversionRequestInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String requestId = generateRequestId();
            String requestUri = request.getRequestURI();
            String method = request.getMethod();
            
            // 在请求属性中存储请求ID
            request.setAttribute("requestId", requestId);
            request.setAttribute("startTime", System.currentTimeMillis());
            
            // 设置响应头以改善客户端连接管理
            setupResponseHeaders(response);
            
            // 检查客户端连接状态
            if (isClientDisconnected(request)) {
                log.warn("Client already disconnected before processing: {} {}", method, requestUri);
                return false;
            }
            
            log.debug("Starting conversion request [{}]: {} {}", requestId, method, requestUri);
            
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                   Object handler, Exception ex) {
            
            String requestId = (String) request.getAttribute("requestId");
            Long startTime = (Long) request.getAttribute("startTime");
            
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                String requestUri = request.getRequestURI();
                String method = request.getMethod();
                int status = response.getStatus();
                
                if (ex != null) {
                    log.warn("Request [{}] completed with exception: {} {} - status: {}, duration: {}ms, error: {}", 
                            requestId, method, requestUri, status, duration, ex.getMessage());
                } else {
                    log.debug("Request [{}] completed successfully: {} {} - status: {}, duration: {}ms", 
                            requestId, method, requestUri, status, duration);
                }
            }
        }

        /**
         * 设置响应头以改善连接管理
         */
        private void setupResponseHeaders(HttpServletResponse response) {
            // 禁用缓存以避免连接问题
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // 设置连接管理头
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Keep-Alive", "timeout=30, max=100");
            
            // 设置 CORS 头（如果需要）
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            // 设置服务器信息
            response.setHeader("X-Application", "Word2PDF-Service");
            response.setHeader("X-Version", "2.0.0");
        }

        /**
         * 检查客户端是否已断开连接
         */
        private boolean isClientDisconnected(HttpServletRequest request) {
            try {
                // 尝试获取一些基本的请求信息来检测连接状态
                request.getRemoteAddr();
                request.getHeader("User-Agent");
                return false;
            } catch (Exception e) {
                log.debug("Client connection check failed: {}", e.getMessage());
                return true;
            }
        }

        /**
         * 生成请求ID
         */
        private String generateRequestId() {
            return String.valueOf(System.currentTimeMillis() % 100000);
        }
    }
} 