package com.suny.word2pdf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 性能统计DTO
 * 
 * @author suny
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceStats {
    
    /**
     * 所有转换结果
     */
    private List<ConversionResult> results;
    
    /**
     * 最快的转换方式
     */
    private String fastestMethod;
    
    /**
     * 最慢的转换方式
     */
    private String slowestMethod;
    
    /**
     * 平均转换时间
     */
    private Double averageDuration;
    
    /**
     * 成功率
     */
    private Double successRate;
    
    /**
     * 总转换次数
     */
    private Integer totalConversions;
} 