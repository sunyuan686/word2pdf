package com.suny.word2pdf.controller;

import com.suny.word2pdf.config.ApplicationConfig;
import com.suny.word2pdf.converter.impl.JodConverterWordToPdfConverter;
import com.suny.word2pdf.dto.ConversionResult;
import com.suny.word2pdf.dto.PerformanceStats;
import com.suny.word2pdf.service.ConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 转换控制器
 * 
 * 提供统一的Word转PDF转换接口，支持多种转换器
 * 
 * @author suny
 */
@Slf4j
@RestController
@RequestMapping("/convert")
@RequiredArgsConstructor
public class ConversionController {
    
    private final ConversionService conversionService;
    private final ApplicationConfig applicationConfig;
    private final Optional<JodConverterWordToPdfConverter> jodConverter;
    
    /**
     * 使用指定转换器转换Word文档为PDF
     * 
     * @param file Word文件
     * @param converter 转换器名称 (POI, Docx4j, LibreOffice, JODConverter)
     * @return 转换结果
     */
    @PostMapping("/{converter}")
    public ResponseEntity<ConversionResult> convertWordToPdf(
            @RequestParam("file") @NotNull MultipartFile file,
            @PathVariable("converter") @NotBlank String converter) {
        
        log.info("Received conversion request with {} converter for file: {}", 
                converter, file.getOriginalFilename());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ConversionResult.failure(converter, 0L, "File is empty"));
        }
        
        if (!isWordFile(file)) {
            return ResponseEntity.badRequest()
                    .body(ConversionResult.failure(converter, 0L, "File must be a Word document (.docx)"));
        }
        
        ConversionResult result = conversionService.convertWordToPdf(file, converter);
        
        if (result.getSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 使用所有可用转换器转换Word文档并比较性能
     * 
     * @param file Word文件
     * @return 性能统计结果
     */
    @PostMapping("/performance-comparison")
    public ResponseEntity<PerformanceStats> performanceComparison(
            @RequestParam("file") @NotNull MultipartFile file) {
        
        log.info("Received performance comparison request for file: {}", file.getOriginalFilename());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (!isWordFile(file)) {
            return ResponseEntity.badRequest().build();
        }
        
        PerformanceStats stats = conversionService.convertWithAllConverters(file);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取可用的转换器列表
     * 
     * @return 转换器列表
     */
    @GetMapping("/converters")
    public ResponseEntity<Map<String, Object>> getAvailableConverters() {
        List<String> converters = conversionService.getAvailableConverters();
        
        Map<String, Object> response = new HashMap<>();
        response.put("converters", converters);
        response.put("total", converters.size());
        response.put("description", "Available Word-to-PDF converters");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取JODConverter状态信息
     *
     * @return 状态信息
     */
    @GetMapping("/jodconverter/status")
    public ResponseEntity<Map<String, Object>> getJodConverterStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (jodConverter.isPresent()) {
            JodConverterWordToPdfConverter converter = jodConverter.get();
            status.put("available", converter.isAvailable());
            status.put("converterName", converter.getConverterName());
        } else {
            status.put("available", false);
            status.put("converterName", "JODConverter not configured");
        }
        
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(status);
    }

    /**
     * JODConverter健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/jodconverter/health")
    public ResponseEntity<Map<String, Object>> jodConverterHealthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        boolean isHealthy = jodConverter.isPresent() && jodConverter.get().isAvailable();
        
        health.put("status", isHealthy ? "UP" : "DOWN");
        health.put("converter", "JODConverter");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 下载转换后的PDF文件
     * 
     * @param fileName 文件名
     * @return PDF文件
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String fileName) {
        try {
            // 构建文件路径
            File file = new File(applicationConfig.getTempDir(), fileName);
            
            if (!file.exists()) {
                log.warn("File not found: {}", file.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            // 处理中文文件名编码
            String contentDisposition = createContentDisposition(fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading file: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 检查是否为Word文件
     * 
     * @param file 文件
     * @return 是否为Word文件
     */
    private boolean isWordFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }
        
        String contentType = file.getContentType();
        String fileName = originalFilename.toLowerCase();
        
        return fileName.endsWith(".docx") || 
               fileName.endsWith(".doc") ||
               "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType) ||
               "application/msword".equals(contentType);
    }
    
    /**
     * 创建支持中文的Content-Disposition头
     *
     * @param filename 文件名
     * @return Content-Disposition头值
     */
    private String createContentDisposition(String filename) {
        try {
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", filename, encodedFilename);
        } catch (Exception e) {
            log.warn("Failed to encode filename: {}", filename, e);
            return "attachment; filename=\"" + filename + "\"";
        }
    }
} 