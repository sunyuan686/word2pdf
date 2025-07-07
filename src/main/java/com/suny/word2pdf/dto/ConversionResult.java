package com.suny.word2pdf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转换结果DTO
 * 
 * @author suny
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResult {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 转换方式
     */
    private String method;
    
    /**
     * 转换耗时（毫秒）
     */
    private Long duration;
    
    /**
     * 原文件大小（字节）
     */
    private Long originalFileSize;
    
    /**
     * PDF文件大小（字节）
     */
    private Long pdfFileSize;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * PDF文件下载URL
     */
    private String downloadUrl;
    
    /**
     * 创建成功结果
     * 
     * @param method 转换方式
     * @param duration 转换耗时
     * @param originalFileSize 原文件大小
     * @param pdfFileSize PDF文件大小
     * @param downloadUrl 下载URL
     * @return 转换结果
     */
    public static ConversionResult success(String method, Long duration, 
                                         Long originalFileSize, Long pdfFileSize, String downloadUrl) {
        return ConversionResult.builder()
                .success(true)
                .method(method)
                .duration(duration)
                .originalFileSize(originalFileSize)
                .pdfFileSize(pdfFileSize)
                .downloadUrl(downloadUrl)
                .build();
    }
    
    /**
     * 创建失败结果
     * 
     * @param method 转换方式
     * @param duration 转换耗时
     * @param errorMessage 错误信息
     * @return 转换结果
     */
    public static ConversionResult failure(String method, Long duration, String errorMessage) {
        return ConversionResult.builder()
                .success(false)
                .method(method)
                .duration(duration)
                .errorMessage(errorMessage)
                .build();
    }
} 