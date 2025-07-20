package com.suny.word2pdf.controller;

import com.suny.word2pdf.config.ApplicationConfig;
import com.suny.word2pdf.dto.ConversionResult;
import com.suny.word2pdf.dto.PerformanceStats;
import com.suny.word2pdf.service.ConversionService;
import com.suny.word2pdf.service.PdfQualityValidator;
import com.suny.word2pdf.service.PdfQualityValidator.QualityValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转换控制器
 * 
 * @author suny
 */
@Slf4j
@RestController
@RequestMapping("/convert")
@RequiredArgsConstructor
public class ConversionController {
    
    private final ConversionService conversionService;
    private final PdfQualityValidator qualityValidator;
    private final ApplicationConfig applicationConfig;
    
    /**
     * 使用指定转换器转换Word文档为PDF
     * 
     * @param file Word文件
     * @param converter 转换器名称
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
    @PostMapping("/compare")
    public ResponseEntity<PerformanceStats> compareAllConverters(
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
     * @return 可用转换器列表
     */
    @GetMapping("/converters")
    public ResponseEntity<List<String>> getAvailableConverters() {
        List<String> converters = conversionService.getAvailableConverters();
        log.info("Available converters: {}", converters);
        return ResponseEntity.ok(converters);
    }
    
    /**
     * 获取转换历史统计
     * 
     * @return 转换历史统计
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, List<ConversionResult>>> getConversionHistory() {
        return ResponseEntity.ok(conversionService.getConversionHistory());
    }
    
    /**
     * 清理转换历史
     * 
     * @return 响应结果
     */
    @DeleteMapping("/history")
    public ResponseEntity<String> clearConversionHistory() {
        conversionService.clearConversionHistory();
        return ResponseEntity.ok("Conversion history cleared");
    }
    
    /**
     * 验证PDF质量
     * 
     * @param wordFile 原始Word文件
     * @param pdfFile 生成的PDF文件
     * @param converterName 转换器名称
     * @return 质量验证结果
     */
    @PostMapping("/validate-quality")
    public ResponseEntity<?> validatePdfQuality(
            @RequestParam("wordFile") @NotNull MultipartFile wordFile,
            @RequestParam("pdfFile") @NotNull MultipartFile pdfFile,
            @RequestParam("converterName") @NotBlank String converterName) {
        
        log.info("开始PDF质量验证: converter={}", converterName);
        
        try {
            File tempWordFile = saveUploadedFile(wordFile, "word_");
            File tempPdfFile = saveUploadedFile(pdfFile, "pdf_");
            
            try {
                // 执行质量验证
                QualityValidationResult result = qualityValidator.validatePdfQuality(
                    tempWordFile, tempPdfFile, converterName);
                
                return ResponseEntity.ok(result);
                
            } finally {
                tempWordFile.delete();
                tempPdfFile.delete();
            }
            
        } catch (Exception e) {
            log.error("PDF质量验证失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "质量验证失败: " + e.getMessage()));
        }
    }
    
    /**
     * 转换并验证质量（一体化接口）
     * 
     * @param file Word文件
     * @param converter 转换器名称
     * @return 转换结果和质量验证结果
     */
    @PostMapping("/convert-and-validate/{converter}")
    public ResponseEntity<?> convertAndValidate(
            @RequestParam("file") @NotNull MultipartFile file,
            @PathVariable("converter") @NotBlank String converter) {
        
        log.info("开始转换并验证: converter={}, file={}", converter, file.getOriginalFilename());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "文件为空"));
        }
        
        if (!isWordFile(file)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "文件必须是Word文档 (.docx)"));
        }
        
        try {
            // 1. 执行转换
            ConversionResult convertResult = conversionService.convertWordToPdf(file, converter);
            
            if (!convertResult.getSuccess()) {
                return ResponseEntity.internalServerError().body(convertResult);
            }
            
            // 2. 构建PDF文件路径进行质量验证
            String fileName = convertResult.getDownloadUrl().substring("/api/download/".length());
            File pdfFile = new File(applicationConfig.getTempDir(), fileName);
            File tempWordFile = saveUploadedFile(file, "word_");
            
            try {
                // 3. 执行质量验证
                QualityValidationResult validationResult = qualityValidator.validatePdfQuality(
                    tempWordFile, pdfFile, converter);
                
                // 4. 组合返回结果
                Map<String, Object> combinedResult = new HashMap<>();
                combinedResult.put("conversion", convertResult);
                combinedResult.put("validation", validationResult);
                
                return ResponseEntity.ok(combinedResult);
                
            } finally {
                tempWordFile.delete();
            }
            
        } catch (Exception e) {
            log.error("转换并验证失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "转换并验证失败: " + e.getMessage()));
        }
    }
    
    /**
     * 批量质量对比（使用所有转换器转换同一文档并对比质量）
     * 
     * @param file Word文件
     * @return 所有转换器的转换结果和质量对比
     */
    @PostMapping("/quality-comparison")
    public ResponseEntity<?> batchQualityComparison(@RequestParam("file") @NotNull MultipartFile file) {
        
        log.info("开始批量质量对比: file={}", file.getOriginalFilename());
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "文件为空"));
        }
        
        if (!isWordFile(file)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "文件必须是Word文档 (.docx)"));
        }
        
        try {
            List<String> converters = conversionService.getAvailableConverters();
            Map<String, Object> comparisonResults = new HashMap<>();
            File tempWordFile = saveUploadedFile(file, "word_");
            
            // 新增：记录生成的PDF文件信息
            Map<String, Object> generatedFiles = new HashMap<>();
            
            try {
                for (String converter : converters) {
                    try {
                        // 转换文档 - 在文件名中包含转换器名称
                        ConversionResult convertResult = conversionService.convertWordToPdf(file, converter, true);
                        
                        if (convertResult.getSuccess()) {
                            String fileName = convertResult.getDownloadUrl().substring("/api/download/".length());
                            File pdfFile = new File(applicationConfig.getTempDir(), fileName);
                            
                            // 记录生成的PDF文件信息
                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("fileName", fileName);
                            fileInfo.put("downloadUrl", convertResult.getDownloadUrl());
                            fileInfo.put("fullDownloadUrl", "http://localhost:8080" + convertResult.getDownloadUrl());
                            fileInfo.put("fileSize", pdfFile.exists() ? pdfFile.length() : 0);
                            fileInfo.put("storagePath", pdfFile.getAbsolutePath());
                            generatedFiles.put(converter, fileInfo);
                            
                            // 验证质量
                            QualityValidationResult validationResult = qualityValidator.validatePdfQuality(
                                tempWordFile, pdfFile, converter);
                            
                            Map<String, Object> converterResult = new HashMap<>();
                            converterResult.put("conversion", convertResult);
                            converterResult.put("validation", validationResult);
                            converterResult.put("fileInfo", fileInfo);  // 添加文件信息
                            
                            comparisonResults.put(converter, converterResult);
                            
                        } else {
                            comparisonResults.put(converter, Map.of("error", "转换失败: " + convertResult.getErrorMessage()));
                        }
                        
                    } catch (Exception e) {
                        log.error("转换器 {} 处理失败", converter, e);
                        comparisonResults.put(converter, Map.of("error", e.getMessage()));
                    }
                }
                
                // 添加质量排名
                List<Map<String, Object>> qualityRanking = comparisonResults.entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof Map && 
                            ((Map<?, ?>) entry.getValue()).containsKey("validation"))
                    .map(entry -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("converter", entry.getKey());
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) entry.getValue();
                        QualityValidationResult validation = (QualityValidationResult) data.get("validation");
                        result.put("score", validation.getOverallScore());
                        result.put("level", validation.getQualityLevel());
                        result.put("pageCountAccurate", validation.isPageCountAccurate());
                        result.put("textSimilarity", validation.getTextSimilarity());
                        result.put("chineseAccuracy", validation.getChineseCharacterAccuracy());
                        
                        // 添加下载信息到排名中
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fileInfo = (Map<String, Object>) data.get("fileInfo");
                        if (fileInfo != null) {
                            result.put("downloadUrl", fileInfo.get("downloadUrl"));
                            result.put("fileSize", fileInfo.get("fileSize"));
                        }
                        
                        return result;
                    })
                    .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                    .collect(Collectors.toList());
                
                Map<String, Object> finalResult = new HashMap<>();
                finalResult.put("results", comparisonResults);
                finalResult.put("ranking", qualityRanking);
                finalResult.put("summary", createQualitySummary(qualityRanking));
                finalResult.put("generatedFiles", generatedFiles);  // 新增：生成的文件清单
                finalResult.put("storageInfo", createStorageInfo());  // 新增：存储信息
                
                return ResponseEntity.ok(finalResult);
                
            } finally {
                tempWordFile.delete();
            }
            
        } catch (Exception e) {
            log.error("批量质量对比失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "批量质量对比失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建质量总结
     */
    private Map<String, Object> createQualitySummary(List<Map<String, Object>> ranking) {
        Map<String, Object> summary = new HashMap<>();
        
        if (!ranking.isEmpty()) {
            Map<String, Object> best = ranking.get(0);
            summary.put("recommendedConverter", best.get("converter"));
            summary.put("bestScore", best.get("score"));
            summary.put("bestLevel", best.get("level"));
            
            double avgScore = ranking.stream()
                    .mapToDouble(r -> (Double) r.get("score"))
                    .average()
                    .orElse(0.0);
            summary.put("averageScore", avgScore);
            
            long excellentCount = ranking.stream()
                    .filter(r -> "优秀".equals(r.get("level")))
                    .count();
            summary.put("excellentCount", excellentCount);
            summary.put("totalCount", ranking.size());
        }
        
        return summary;
    }
    
    /**
     * 创建存储信息
     */
    private Map<String, Object> createStorageInfo() {
        Map<String, Object> storageInfo = new HashMap<>();
        storageInfo.put("tempDirectory", applicationConfig.getTempDir());
        storageInfo.put("absolutePath", new File(applicationConfig.getTempDir()).getAbsolutePath());
        storageInfo.put("downloadBaseUrl", "http://localhost:8080/api/download/");
        storageInfo.put("note", "PDF文件存储在临时目录中，可通过downloadUrl直接下载");
        return storageInfo;
    }
    
    /**
     * 保存上传的文件到临时目录
     */
    private File saveUploadedFile(MultipartFile file, String prefix) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        
        Path tempFile = Files.createTempFile(prefix, suffix);
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        
        return tempFile.toFile();
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
} 