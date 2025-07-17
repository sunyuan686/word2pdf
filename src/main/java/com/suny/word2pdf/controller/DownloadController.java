package com.suny.word2pdf.controller;

import com.suny.word2pdf.config.ApplicationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * 下载控制器 - 支持中文文件名
 * 
 * @author suny
 */
@Slf4j
@RestController
@RequestMapping("/download")
@RequiredArgsConstructor
public class DownloadController {
    
    private final ApplicationConfig applicationConfig;
    
    @Value("${app.storage.mock.base-path:/tmp/word2pdf/storage}")
    private String storagePath;
    
    /**
     * 下载PDF文件
     * 
     * @param filename 文件名
     * @return PDF文件资源
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String filename) {
        
        log.info("Download request for file: {}", filename);
        
        if (!filename.endsWith(".pdf")) {
            log.warn("Invalid file extension for download: {}", filename);
            return ResponseEntity.badRequest().build();
        }
        
        File file = Paths.get(applicationConfig.getTempDir(), filename).toFile();
        
        if (!file.exists()) {
            log.warn("File not found for download: {}", file.getAbsolutePath());
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(file);
        
        // 处理中文文件名编码
        String contentDisposition = createContentDisposition(filename);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(resource);
    }
    
    /**
     * 从对象存储下载文件
     * 
     * @param request HTTP请求
     * @return 文件资源
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> downloadFromStorage(HttpServletRequest request) {
        try {
            // 获取完整的路径（去除 /api/download/ 前缀）
            String requestPath = request.getRequestURI();
            String objectKey = requestPath.substring("/api/download/".length());
            
            log.info("Downloading from storage with object key: {}", objectKey);
            
            // 构建文件路径
            File file = Paths.get(storagePath, objectKey).toFile();
            
            if (!file.exists()) {
                log.warn("Storage file not found: {}", file.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            // 获取文件名用于Content-Disposition
            String fileName = file.getName();
            
            // 处理中文文件名编码
            String contentDisposition = createContentDisposition(fileName);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(file.length())
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Error downloading from storage", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 创建支持中文的Content-Disposition头
     * 
     * @param filename 文件名
     * @return Content-Disposition头值
     */
    private String createContentDisposition(String filename) {
        try {
            // 使用RFC 5987标准格式处理中文文件名
            // 格式: attachment; filename*=UTF-8''encoded-filename
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            
            // 同时提供两种格式以确保最大兼容性
            // 1. 标准ASCII格式（兼容老旧浏览器）
            // 2. RFC 5987 UTF-8格式（支持中文）
            String asciiFilename = filename.replaceAll("[^\\x00-\\x7F]", "_");
            
            return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", 
                    asciiFilename, encodedFilename);
                    
        } catch (Exception e) {
            log.warn("Failed to encode filename: {}, using fallback", filename, e);
            // 如果编码失败，使用时间戳作为文件名
            String fallbackFilename = "download_" + System.currentTimeMillis() + ".pdf";
            return "attachment; filename=\"" + fallbackFilename + "\"";
        }
    }
} 