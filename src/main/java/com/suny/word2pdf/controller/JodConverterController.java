package com.suny.word2pdf.controller;

import com.suny.word2pdf.config.ApplicationConfig;
import com.suny.word2pdf.converter.impl.JodConverterWordToPdfConverter;
import com.suny.word2pdf.dto.ConversionResult;
import com.suny.word2pdf.service.ObjectStorageService;
import com.suny.word2pdf.service.impl.MockObjectStorageService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * JODConverter转换控制器
 * <p>
 * 提供基于JODConverter的Word转PDF接口
 *
 * @author suny
 */
@Slf4j
@RestController
@RequestMapping("/jodconverter")
public class JodConverterController {

    private final JodConverterWordToPdfConverter jodConverter;
    private final ObjectStorageService objectStorageService;
    private final ApplicationConfig applicationConfig;

    public JodConverterController(JodConverterWordToPdfConverter jodConverter,
                                  ObjectStorageService objectStorageService,
                                  ApplicationConfig applicationConfig) {
        this.jodConverter = jodConverter;
        this.objectStorageService = objectStorageService;
        this.applicationConfig = applicationConfig;
        log.info("JodConverterController created with converter: {}, storage: {}, config: {}",
                jodConverter != null ? jodConverter.getClass().getSimpleName() : "null",
                objectStorageService != null ? objectStorageService.getClass().getSimpleName() : "null",
                applicationConfig != null ? "ApplicationConfig" : "null");
        LinkedList<Object> objects = new LinkedList<>();
    }

    /**
     * 使用JODConverter将Word文档转换为PDF
     * 返回与ConversionController相同格式的结果
     *
     * @param file Word文件
     * @return 转换结果 (ConversionResult)
     */
    @PostMapping("/convert")
    public ResponseEntity<ConversionResult> convertWordToPdf(@RequestParam("file") @NotNull MultipartFile file) {
        log.info("Received JODConverter conversion request for file: {}", file.getOriginalFilename());

        Map<String, Object> validationResult = validateWordFile(file);
        if (!(Boolean) validationResult.get("valid")) {
            return ResponseEntity.badRequest()
                    .body(ConversionResult.failure("JODConverter", 0L, (String) validationResult.get("error")));
        }

        // 检查转换器可用性
        if (jodConverter == null) {
            return ResponseEntity.status(503)
                    .body(ConversionResult.failure("JODConverter", 0L, "JODConverter bean is not available"));
        }

        if (!jodConverter.isAvailable()) {
            return ResponseEntity.status(503)
                    .body(ConversionResult.failure("JODConverter", 0L, "JODConverter is not available"));
        }

        long startTime = System.currentTimeMillis();

        try {
            // 生成PDF文件名
            String originalFileName = file.getOriginalFilename();
            String pdfFileName = generatePdfFileName(originalFileName);
            
            // 将PDF保存到应用配置的临时目录
            File outputFile = new File(applicationConfig.getTempDir(), pdfFileName);
            
            // 确保父目录存在
            outputFile.getParentFile().mkdirs();

            log.info("JODConverter conversion started for file: {}", originalFileName);
            long start = System.currentTimeMillis();
            // 执行转换
            jodConverter.convert(file.getInputStream(), outputFile);

            long end = System.currentTimeMillis();
            log.info("JODConverter conversion finished for file: {},conversion time: {} ms", originalFileName, end - start);

            long conversionTime = System.currentTimeMillis() - startTime;

            // 验证输出文件
            if (!outputFile.exists() || outputFile.length() == 0) {
                return ResponseEntity.internalServerError()
                        .body(ConversionResult.failure("JODConverter", conversionTime, 
                                "Conversion failed: output file is empty or does not exist"));
            }

            // 生成下载URL
            String downloadUrl = "/api/download/" + pdfFileName;

            // 创建成功结果
            ConversionResult result = ConversionResult.success(
                    "JODConverter",
                    conversionTime,
                    file.getSize(),
                    outputFile.length(),
                    downloadUrl
            );

            log.info("JODConverter conversion successful for file: {}, time: {}ms, size: {} bytes, download URL: {}",
                    originalFileName, conversionTime, outputFile.length(), downloadUrl);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            long conversionTime = System.currentTimeMillis() - startTime;
            log.error("JODConverter conversion failed for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError()
                    .body(ConversionResult.failure("JODConverter", conversionTime, "Conversion failed: " + e.getMessage()));
        }
    }

    /**
     * 使用JODConverter转换Word文档为PDF并上传到对象存储
     *
     * @param file Word文件
     * @return 包含下载链接的转换结果
     */
    @PostMapping("/convert-to-storage")
    public ResponseEntity<Map<String, Object>> convertWordToPdfAndUpload(
            @RequestParam("file") @NotNull MultipartFile file) {

        log.info("Received JODConverter storage conversion request for file: {}", file.getOriginalFilename());

        // 验证文件
        Map<String, Object> validationResult = validateWordFile(file);
        if (!(Boolean) validationResult.get("valid")) {
            return ResponseEntity.badRequest().body(createStorageErrorResponse("File validation failed", validationResult));
        }

        // 检查转换器可用性
        if (jodConverter == null) {
            return ResponseEntity.status(503)
                    .body(createStorageErrorResponse("JODConverter bean is not available", null));
        }

        if (!jodConverter.isAvailable()) {
            return ResponseEntity.status(503)
                    .body(createStorageErrorResponse("JODConverter is not available", null));
        }

        long startTime = System.currentTimeMillis();
        Path tempDir = null;
        String objectKey = null;

        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("jodconverter_storage_");

            // 生成输出文件名
            String originalFileName = file.getOriginalFilename();
            String pdfFileName = generatePdfFileName(originalFileName);
            File outputFile = tempDir.resolve(pdfFileName).toFile();

            // 执行转换
            jodConverter.convert(file.getInputStream(), outputFile);

            long conversionTime = System.currentTimeMillis() - startTime;

            // 验证输出文件
            if (!outputFile.exists() || outputFile.length() == 0) {
                return ResponseEntity.internalServerError()
                        .body(createStorageErrorResponse("Conversion failed: output file is empty or does not exist", null));
            }

            // 生成唯一的对象键
            if (objectStorageService instanceof MockObjectStorageService) {
                MockObjectStorageService mockService = (MockObjectStorageService) objectStorageService;
                objectKey = mockService.generateUniqueObjectKey(originalFileName, "jodconverter");
            } else {
                // 默认对象键生成逻辑
                objectKey = generateDefaultObjectKey(originalFileName);
            }

            // 上传到对象存储
            boolean uploadSuccess = objectStorageService.uploadFile(outputFile, objectKey);

            if (!uploadSuccess) {
                return ResponseEntity.internalServerError()
                        .body(createStorageErrorResponse("Failed to upload file to object storage", null));
            }

            // 获取下载链接
            String downloadUrl = objectStorageService.getDownloadUrl(objectKey);
            String preSignedUrl = objectStorageService.getPreSignedDownloadUrl(objectKey, 60);

            long totalTime = System.currentTimeMillis() - startTime;

            // 创建成功响应
            Map<String, Object> response = createStorageSuccessResponse(
                    originalFileName, outputFile.length(), conversionTime, totalTime,
                    downloadUrl, preSignedUrl, objectKey);

            log.info("JODConverter storage conversion successful for file: {}, total time: {}ms, object key: {}",
                    originalFileName, totalTime, objectKey);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("JODConverter storage conversion failed for file: {}", file.getOriginalFilename(), e);

            // 清理可能已上传的文件
            if (objectKey != null) {
                try {
                    objectStorageService.deleteFile(objectKey);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to cleanup uploaded file: {}", objectKey, cleanupEx);
                }
            }

            return ResponseEntity.internalServerError()
                    .body(createStorageErrorResponse("Conversion failed: " + e.getMessage(), null));
        } finally {
            // 异步清理临时文件
            if (tempDir != null) {
                cleanupTempDirectoryAsync(tempDir);
            }
        }
    }

    /**
     * 获取JODConverter状态信息
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getJodConverterStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", jodConverter.isAvailable());
        status.put("converterName", jodConverter.getConverterName());
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return ResponseEntity.ok(status);
    }

    /**
     * 获取LibreOffice进程池详细信息
     * 用于诊断性能问题
     *
     * @return 进程池状态
     */
    @GetMapping("/process-pool-status")
    public ResponseEntity<Map<String, Object>> getProcessPoolStatus() {
        Map<String, Object> poolStatus = new HashMap<>();
        
        try {
            // 检查LibreOffice进程
            String[] portNumbers = {"2002", "2003"};
            Map<String, Object> processInfo = new HashMap<>();
            
            for (String port : portNumbers) {
                Map<String, Object> portInfo = checkLibreOfficeProcess(port);
                processInfo.put("port_" + port, portInfo);
            }
            
            poolStatus.put("success", true);
            poolStatus.put("processInfo", processInfo);
            poolStatus.put("converterAvailable", jodConverter.isAvailable());
            poolStatus.put("documentConverterClass", 
                    jodConverter != null ? jodConverter.getClass().getSimpleName() : "null");
            poolStatus.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.ok(poolStatus);
            
        } catch (Exception e) {
            poolStatus.put("success", false);
            poolStatus.put("error", e.getMessage());
            poolStatus.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.status(500).body(poolStatus);
        }
    }

    /**
     * 检查当前活跃的LibreOffice进程
     */
    private List<Map<String, Object>> checkActiveLibreOfficeProcesses() {
        List<Map<String, Object>> processes = new ArrayList<>();
        String portNumbers = getProperty("jodconverter.local.port-numbers", "2002,2003,2004");
        
        for (String port : portNumbers.split(",")) {
            Map<String, Object> processInfo = checkLibreOfficeProcess(port.trim());
            processes.add(processInfo);
        }
        
        return processes;
    }
    
    /**
     * 生成UNO连接字符串
     */
    private List<String> generateUNOConnectionStrings() {
        List<String> connectionStrings = new ArrayList<>();
        String portNumbers = getProperty("jodconverter.local.port-numbers", "2002,2003,2004");
        
        for (String port : portNumbers.split(",")) {
            String connectionString = String.format("socket,host=127.0.0.1,port=%s;urp;", port.trim());
            connectionStrings.add(connectionString);
        }
        
        return connectionStrings;
    }
    
    /**
     * 获取系统属性或Spring配置属性
     */
    private String getProperty(String key, String defaultValue) {
        // 首先尝试系统属性
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        
        // 然后尝试环境变量（将点替换为下划线并转大写）
        String envKey = key.replace(".", "_").toUpperCase();
        value = System.getenv(envKey);
        if (value != null) {
            return value;
        }
        
        // 最后返回默认值
        return defaultValue;
    }
    
    /**
     * 检查指定端口的LibreOffice进程状态
     *
     * @param port 端口号
     * @return 进程状态信息
     */
    private Map<String, Object> checkLibreOfficeProcess(String port) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // 使用 lsof 命令检查端口占用
            ProcessBuilder pb = new ProcessBuilder("lsof", "-ti:" + port);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                // 端口被占用，说明进程运行中
                info.put("status", "RUNNING");
                info.put("port", port);
                
                // 尝试获取进程信息
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String pid = reader.readLine();
                    if (pid != null && !pid.trim().isEmpty()) {
                        info.put("pid", pid.trim());
                        
                        // 获取进程的内存和CPU使用情况
                        Map<String, String> processStats = getProcessStats(pid.trim());
                        info.putAll(processStats);
                    }
                }
            } else {
                info.put("status", "NOT_RUNNING");
                info.put("port", port);
            }
            
        } catch (Exception e) {
            info.put("status", "ERROR");
            info.put("port", port);
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    /**
     * 获取进程统计信息
     *
     * @param pid 进程ID
     * @return 进程统计信息
     */
    private Map<String, String> getProcessStats(String pid) {
        Map<String, String> stats = new HashMap<>();
        
        try {
            // 使用 ps 命令获取进程信息
            ProcessBuilder pb = new ProcessBuilder("ps", "-o", "pid,pcpu,pmem,etime,state", "-p", pid);
            Process process = pb.start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    
                    reader.readLine(); // 跳过标题行
                    String statsLine = reader.readLine();
                    
                    if (statsLine != null) {
                        String[] parts = statsLine.trim().split("\\s+");
                        if (parts.length >= 5) {
                            stats.put("cpuUsage", parts[1] + "%");
                            stats.put("memoryUsage", parts[2] + "%");
                            stats.put("elapsedTime", parts[3]);
                            stats.put("processState", parts[4]);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            stats.put("statsError", e.getMessage());
        }
        
        return stats;
    }


    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();

        try {
            if (jodConverter != null && jodConverter.isAvailable()) {
                health.put("status", "UP");
                health.put("message", "JODConverter is available and ready");
                return ResponseEntity.ok(health);
            } else {
                health.put("status", "DOWN");
                health.put("message", "JODConverter is not available");
                return ResponseEntity.status(503).body(health);
            }
        } catch (Exception e) {
            health.put("status", "ERROR");
            health.put("message", "Error checking JODConverter: " + e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * 验证Word文件
     *
     * @param file 上传的文件
     * @return 验证结果
     */
    private Map<String, Object> validateWordFile(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("valid", false);
            result.put("error", "File is empty");
            return result;
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.toLowerCase().endsWith(".docx") && !fileName.toLowerCase().endsWith(".doc"))) {
            result.put("valid", false);
            result.put("error", "File must be a Word document (.doc or .docx)");
            return result;
        }

        // 检查文件大小（限制为2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            result.put("valid", false);
            result.put("error", "File size must be less than 100MB");
            return result;
        }

        result.put("valid", true);
        return result;
    }

    /**
     * 生成PDF文件名
     *
     * @param originalFileName 原始文件名
     * @return PDF文件名
     */
    private String generatePdfFileName(String originalFileName) {
        if (originalFileName == null) {
            return "converted_" + UUID.randomUUID() + ".pdf";
        }

        String baseName = originalFileName;
        int lastDotIndex = baseName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            baseName = baseName.substring(0, lastDotIndex);
        }

        return baseName + "_jodconverter.pdf";
    }

    /**
     * 创建错误响应
     *
     * @param message 错误消息
     * @return 错误响应Map
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        error.put("converter", "JODConverter");
        return error;
    }

    /**
     * 生成默认对象键
     *
     * @param originalFileName 原始文件名
     * @return 对象键
     */
    private String generateDefaultObjectKey(String originalFileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd/HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        String fileName = originalFileName != null ? originalFileName : "file.pdf";
        String extension = "";

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
            fileName = fileName.substring(0, lastDotIndex);
        }

        return String.format("jodconverter/%s/%s_%s%s", timestamp, fileName, uuid, extension);
    }

    /**
     * 创建存储成功响应
     *
     * @param originalFileName 原始文件名
     * @param fileSize         文件大小
     * @param conversionTime   转换时间
     * @param totalTime        总时间
     * @param downloadUrl      下载链接
     * @param preSignedUrl     预签名链接
     * @param objectKey        对象键
     * @return 成功响应
     */
    private Map<String, Object> createStorageSuccessResponse(String originalFileName, long fileSize,
                                                             long conversionTime, long totalTime,
                                                             String downloadUrl, String preSignedUrl,
                                                             String objectKey) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Conversion and upload completed successfully");
        response.put("converter", "JODConverter");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 文件信息
        response.put("originalFileName", originalFileName);
        response.put("fileSize", fileSize);
        response.put("fileSizeFormatted", formatFileSize(fileSize));

        // 时间信息
        response.put("conversionTime", conversionTime);
        response.put("totalTime", totalTime);
        response.put("conversionTimeFormatted", conversionTime + "ms");
        response.put("totalTimeFormatted", totalTime + "ms");

        // 存储信息
        response.put("objectKey", objectKey);
        response.put("bucketName", objectStorageService.getBucketName());

        // 下载链接
        response.put("downloadUrl", downloadUrl);
        response.put("preSignedUrl", preSignedUrl);
        response.put("preSignedUrlExpiration", "60 minutes");

        return response;
    }

    /**
     * 创建存储错误响应
     *
     * @param message 错误消息
     * @param details 错误详情
     * @return 错误响应
     */
    private Map<String, Object> createStorageErrorResponse(String message, Map<String, Object> details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("converter", "JODConverter");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (details != null) {
            response.put("details", details);
        }

        return response;
    }

    /**
     * 格式化文件大小
     *
     * @param bytes 字节数
     * @return 格式化后的文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 异步清理临时目录
     *
     * @param tempDir 临时目录
     */
    private void cleanupTempDirectoryAsync(Path tempDir) {
        // 在后台线程中清理临时文件
        Thread cleanupThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // 等待5秒确保文件下载完成
                Files.walk(tempDir)
                        .sorted((path1, path2) -> path2.compareTo(path1)) // 先删除文件，再删除目录
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
                log.debug("Cleaned up temp directory: {}", tempDir);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp directory: {}", tempDir, e);
            }
        });

        cleanupThread.setDaemon(true);
        cleanupThread.setName("JodConverter-Cleanup-" + System.currentTimeMillis());
        cleanupThread.start();
    }
} 