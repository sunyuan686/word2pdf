package com.suny.word2pdf.util;

import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JODConverter性能监控工具
 * 
 * 用于深入分析JODConverter转换过程中各个步骤的耗时，包括：
 * - 进程池状态监控
 * - UNO连接时间追踪
 * - 任务队列等待时间
 * - LibreOffice进程性能分析
 * 
 * @author suny
 */
@Slf4j
@Component
public class JodConverterPerformanceMonitor {
    
    private static final Map<String, Long> operationTimestamps = new ConcurrentHashMap<>();
    private static final AtomicLong operationCounter = new AtomicLong(0);
    
    /**
     * 详细的操作步骤枚举
     */
    public enum ConversionStep {
        TEMP_FILE_CREATION("临时文件创建"),
        OFFICE_MANAGER_ACCESS("进程管理器访问"),
        CONNECTION_ACQUISITION("连接获取"),
        DOCUMENT_LOADING("文档加载"),
        FORMAT_DETECTION("格式检测"),
        CONVERSION_EXECUTION("转换执行"),
        RESULT_RETRIEVAL("结果获取"),
        FILE_CLEANUP("文件清理");
        
        private final String description;
        
        ConversionStep(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 开始监控操作
     */
    public String startOperation(String fileName) {
        String operationId = "JOD-" + operationCounter.incrementAndGet() + "-" + 
                           System.currentTimeMillis() % 10000;
        operationTimestamps.put(operationId + "_START", System.currentTimeMillis());
        
        return operationId;
    }
    
    /**
     * 记录步骤开始
     */
    public void stepStart(String operationId, ConversionStep step) {
        String key = operationId + "_" + step.name() + "_START";
        operationTimestamps.put(key, System.currentTimeMillis());
        log.debug("⏱️  [{}] {} 开始...", operationId, step.getDescription());
    }
    
    /**
     * 记录步骤完成
     */
    public long stepEnd(String operationId, ConversionStep step) {
        String startKey = operationId + "_" + step.name() + "_START";
        String endKey = operationId + "_" + step.name() + "_END";
        
        long endTime = System.currentTimeMillis();
        operationTimestamps.put(endKey, endTime);
        
        Long startTime = operationTimestamps.get(startKey);
        if (startTime != null) {
            long duration = endTime - startTime;
            log.info("✅ [{}] {} 完成 - 耗时: {}ms", operationId, step.getDescription(), duration);
            return duration;
        } else {
            log.warn("⚠️ [{}] {} 未找到开始时间", operationId, step.getDescription());
            return 0;
        }
    }
    
    /**
     * 生成最终性能报告
     */
    public void generateFinalReport(String operationId) {

        Long startTime = operationTimestamps.get(operationId + "_START");
        if (startTime == null) {
            log.warn("[{}] 未找到操作开始时间", operationId);
            return;
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        long totalStepsTime = 0;
        for (ConversionStep step : ConversionStep.values()) {
            long stepTime = calculateStepTime(operationId, step);
            if (stepTime > 0) {
                log.info("📊 [{}] {}: {}ms", operationId, step.getDescription(), stepTime);
                totalStepsTime += stepTime;
            }
        }
        

        log.info("📊 [{}] 总耗时: {}ms", operationId, totalTime);

        cleanupOperationData(operationId);
    }
    
    /**
     * 计算单个步骤耗时
     */
    private long calculateStepTime(String operationId, ConversionStep step) {
        String startKey = operationId + "_" + step.name() + "_START";
        String endKey = operationId + "_" + step.name() + "_END";
        
        Long startTime = operationTimestamps.get(startKey);
        Long endTime = operationTimestamps.get(endKey);
        
        if (startTime != null && endTime != null) {
            return endTime - startTime;
        }
        return 0;
    }
    

    /**
     * 清理操作数据
     */
    private void cleanupOperationData(String operationId) {
        operationTimestamps.entrySet().removeIf(entry -> entry.getKey().startsWith(operationId));
    }
    

}