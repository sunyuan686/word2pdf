package com.suny.word2pdf.controller;

import com.suny.word2pdf.dto.ConversionResult;
import com.suny.word2pdf.dto.PerformanceStats;
import com.suny.word2pdf.service.ConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

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