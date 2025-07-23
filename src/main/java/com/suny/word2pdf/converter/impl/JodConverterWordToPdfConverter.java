package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import com.suny.word2pdf.util.JodConverterPerformanceMonitor;
import com.suny.word2pdf.util.JodConverterPerformanceMonitor.ConversionStep;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 基于JODConverter的Word转PDF转换器 - 增强性能监控版本
 * <p>
 * JODConverter是一个强大的文档转换库，提供了以下优势：
 * 1. 高性能：通过LibreOffice的UNO API进行文档转换
 * 2. 连接池管理：自动管理LibreOffice进程池，提高并发性能
 * 3. 格式支持广泛：支持多种文档格式之间的转换
 * 4. 稳定性强：经过大量生产环境验证
 * 5. Spring Boot集成：提供了完整的Spring Boot自动配置
 * <p>
 *
 * @author suny
 * @version 2.0 - Enhanced Performance Monitoring
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true")
public class JodConverterWordToPdfConverter implements WordToPdfConverter {

    private final DocumentConverter documentConverter;
    private final OfficeManager officeManager;
    private final JodConverterPerformanceMonitor performanceMonitor;


    /**
     * 构造函数注入DocumentConverter和OfficeManager
     *
     * @param documentConverter JODConverter文档转换器
     * @param officeManager     LibreOffice进程管理器
     */
    @Autowired
    public JodConverterWordToPdfConverter(DocumentConverter documentConverter,
                                          @Autowired(required = false) OfficeManager officeManager,
                                          JodConverterPerformanceMonitor performanceMonitor) {
        this.documentConverter = documentConverter;
        this.officeManager = officeManager;
        this.performanceMonitor = performanceMonitor;
        log.info("JodConverterWordToPdfConverter initialized with enhanced performance monitoring");
        log.info("DocumentConverter class: {}",
                documentConverter != null ? documentConverter.getClass().getSimpleName() : "null");
        log.info("OfficeManager available: {}", officeManager != null);
    }

    @Override
    public String getConverterName() {
        return "JODConverter";
    }

    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        String operationId = performanceMonitor.startOperation(outputFile.getName());

        log.info("🚀 Starting enhanced JODConverter conversion [{}] for file: {}",
                operationId, outputFile.getName());

        if (documentConverter == null) {
            throw new RuntimeException("JODConverter DocumentConverter is not available");
        }

        try {

            performDetailedJodConversion(inputStream, outputFile, operationId);

        } catch (Exception e) {
            log.error("❌ JODConverter conversion failed [{}] for file: {}",
                    operationId, outputFile.getName(), e);
            throw new RuntimeException("JODConverter conversion failed: " + e.getMessage(), e);
        } finally {

            performanceMonitor.generateFinalReport(operationId);
        }
    }


    @Override
    public boolean isAvailable() {
        boolean available = documentConverter != null;
        if (available && officeManager != null) {
            try {
                log.debug("OfficeManager status: {}", officeManager.toString());
            } catch (Exception e) {
                log.debug("Cannot get OfficeManager status", e);
            }
        }
        return available;
    }



    /**
     * 执行详细监控的JODConverter转换
     */
    private void performDetailedJodConversion(InputStream inputStream, File outputFile, String operationId) throws Exception {

        performanceMonitor.stepStart(operationId, ConversionStep.CONVERSION_EXECUTION);
        try {
            documentConverter.convert(inputStream).to(outputFile).execute();

        } catch (Exception e) {
            log.error("[{}] ❌ 转换执行失败", operationId, e);
            throw e;
        }
        performanceMonitor.stepEnd(operationId, ConversionStep.CONVERSION_EXECUTION);

        log.info("[{}] ✅ JODConverter转换完成", operationId);
        log.info("[{}] 📄 输出PDF文件大小: {} bytes", operationId, outputFile.length());
    }

}