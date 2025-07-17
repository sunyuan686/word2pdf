package com.suny.word2pdf.service.impl;

import com.suny.word2pdf.service.ObjectStorageService;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储服务实现
 * 
 * 基于MinIO提供的S3兼容API实现对象存储功能
 * 
 * @author suny
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioObjectStorageService implements ObjectStorageService {
    
    @Value("${app.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;
    
    @Value("${app.storage.minio.access-key:minioadmin}")
    private String accessKey;
    
    @Value("${app.storage.minio.secret-key:minioadmin123}")
    private String secretKey;
    
    @Value("${app.storage.bucket-name:word2pdf-bucket}")
    private String bucketName;
    
    @Value("${app.storage.minio.region:us-east-1}")
    private String region;
    
    private MinioClient minioClient;
    
    /**
     * 初始化MinIO客户端
     */
    @PostConstruct
    public void initializeMinioClient() {
        try {
            minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .region(region)
                    .build();
            
            // 检查并创建存储桶
            ensureBucketExists();
            
            log.info("MinIO client initialized successfully: endpoint={}, bucket={}", 
                    endpoint, bucketName);
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client", e);
            throw new RuntimeException("MinIO initialization failed", e);
        }
    }
    
    /**
     * 确保存储桶存在
     */
    private void ensureBucketExists() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket already exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: {}", bucketName, e);
            throw new RuntimeException("Bucket creation failed", e);
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
        } catch (Exception e) {
            log.error("Failed to upload file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
    
    @Override
    public boolean uploadFile(InputStream inputStream, String objectKey, long contentLength) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(sanitizeObjectKey(objectKey))
                            .stream(inputStream, contentLength, -1)
                            .contentType("application/pdf")
                            .build());
            
            log.info("Successfully uploaded file to MinIO: bucket={}, object={}", 
                    bucketName, objectKey);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: object={}", objectKey, e);
            return false;
        }
    }
    
    @Override
    public String getDownloadUrl(String objectKey) {
        // 对于MinIO，我们返回预签名URL（有效期较长）
        return getPreSignedDownloadUrl(objectKey, 60 * 24 * 7); // 7天有效期
    }
    
    @Override
    public String getPreSignedDownloadUrl(String objectKey, int expirationMinutes) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(sanitizeObjectKey(objectKey))
                            .expiry(expirationMinutes, TimeUnit.MINUTES)
                            .build());
            
            log.debug("Generated presigned URL for object: {}, expires in {} minutes", 
                    objectKey, expirationMinutes);
            return presignedUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for object: {}", objectKey, e);
            return null;
        }
    }
    
    @Override
    public boolean deleteFile(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(sanitizeObjectKey(objectKey))
                            .build());
            
            log.info("Successfully deleted file from MinIO: {}", objectKey);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectKey, e);
            return false;
        }
    }
    
    @Override
    public boolean fileExists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(sanitizeObjectKey(objectKey))
                            .build());
            return true;
        } catch (Exception e) {
            log.debug("File does not exist in MinIO: {}", objectKey);
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
    
    /**
     * 获取MinIO客户端状态信息
     * 
     * @return 状态信息
     */
    public String getClientInfo() {
        return String.format("MinIO Client - Endpoint: %s, Bucket: %s", endpoint, bucketName);
    }
} 