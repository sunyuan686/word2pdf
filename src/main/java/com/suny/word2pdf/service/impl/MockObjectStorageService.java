package com.suny.word2pdf.service.impl;

import com.suny.word2pdf.service.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 模拟对象存储服务实现
 * 
 * 实际项目中应替换为真实的云存储服务（如阿里云OSS、AWS S3等）
 * 
 * @author suny
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "mock", matchIfMissing = true)
public class MockObjectStorageService implements ObjectStorageService {
    
    @Value("${app.storage.mock.base-path:/tmp/word2pdf/storage}")
    private String basePath;
    
    @Value("${app.storage.mock.base-url:http://localhost:8080/api/download}")
    private String baseUrl;
    
    @Value("${app.storage.bucket-name:word2pdf-bucket}")
    private String bucketName;
    
    /**
     * 初始化存储目录
     */
    private void initializeStorageDirectory() {
        try {
            Path storagePath = Paths.get(basePath);
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
                log.info("Created storage directory: {}", storagePath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", basePath, e);
            throw new RuntimeException("Storage initialization failed", e);
        }
    }
    
    @Override
    public boolean uploadFile(File file, String objectKey) {
        if (file == null || !file.exists()) {
            log.warn("File does not exist: {}", file);
            return false;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            return uploadFile(fis, objectKey, file.length());
        } catch (IOException e) {
            log.error("Failed to upload file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
    
    @Override
    public boolean uploadFile(InputStream inputStream, String objectKey, long contentLength) {
        initializeStorageDirectory();
        
        try {
            Path targetPath = Paths.get(basePath, sanitizeObjectKey(objectKey));
            
            // 确保目标目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 复制文件
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("Successfully uploaded file to mock storage: {} -> {}", 
                    objectKey, targetPath.toAbsolutePath());
            return true;
            
        } catch (IOException e) {
            log.error("Failed to upload file to mock storage: {}", objectKey, e);
            return false;
        }
    }
    
    @Override
    public String getDownloadUrl(String objectKey) {
        return baseUrl + "/" + sanitizeObjectKey(objectKey);
    }
    
    @Override
    public String getPreSignedDownloadUrl(String objectKey, int expirationMinutes) {
        // 模拟预签名URL，添加时间戳和过期时间
        String timestamp = String.valueOf(System.currentTimeMillis());
        String expiration = String.valueOf(System.currentTimeMillis() + expirationMinutes * 60 * 1000L);
        
        return String.format("%s/%s?timestamp=%s&expires=%s&signature=mock_%s", 
                baseUrl, sanitizeObjectKey(objectKey), timestamp, expiration, 
                Integer.toHexString(objectKey.hashCode()));
    }
    
    @Override
    public boolean deleteFile(String objectKey) {
        try {
            Path filePath = Paths.get(basePath, sanitizeObjectKey(objectKey));
            boolean deleted = Files.deleteIfExists(filePath);
            
            if (deleted) {
                log.info("Successfully deleted file from mock storage: {}", objectKey);
            } else {
                log.warn("File not found for deletion: {}", objectKey);
            }
            
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file from mock storage: {}", objectKey, e);
            return false;
        }
    }
    

    @Override
    public String getBucketName() {
        return bucketName;
    }
    
    /**
     * 清理对象键，确保路径安全
     * 
     * @param objectKey 原始对象键
     * @return 清理后的对象键
     */
    private String sanitizeObjectKey(String objectKey) {
        if (objectKey == null) {
            return "unknown_" + UUID.randomUUID();
        }
        
        // 移除路径遍历字符和其他危险字符
        return objectKey
                .replace("..", "")
                .replace("\\", "/")
                .replaceAll("^/+", "") // 移除开头的斜杠
                .replaceAll("/+", "/"); // 合并多个斜杠
    }
    
    /**
     * 生成唯一的对象键
     * 
     * @param originalFileName 原始文件名
     * @param prefix 前缀
     * @return 唯一对象键
     */
    public String generateUniqueObjectKey(String originalFileName, String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd/HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        String fileName = originalFileName != null ? originalFileName : "file.pdf";
        String extension = "";
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
            fileName = fileName.substring(0, lastDotIndex);
        }
        
        return String.format("%s/%s/%s_%s%s", 
                prefix != null ? prefix : "pdf", 
                timestamp, 
                sanitizeFileName(fileName), 
                uuid, 
                extension);
    }
    
    /**
     * 清理文件名，移除特殊字符
     * 
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        
        return fileName
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]", "_") // 保留中文字符
                .replaceAll("_{2,}", "_") // 合并多个下划线
                .replaceAll("^_+|_+$", ""); // 移除首尾下划线
    }
} 