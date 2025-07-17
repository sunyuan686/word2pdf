package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 基于JODConverter的Word转PDF转换器
 * 
 * JODConverter是一个强大的文档转换库，提供了以下优势：
 * 1. 高性能：通过LibreOffice的UNO API进行文档转换
 * 2. 连接池管理：自动管理LibreOffice进程池，提高并发性能
 * 3. 格式支持广泛：支持多种文档格式之间的转换
 * 4. 稳定性强：经过大量生产环境验证
 * 5. Spring Boot集成：提供了完整的Spring Boot自动配置
 * 
 * @author suny
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true")
public class JodConverterWordToPdfConverter implements WordToPdfConverter {
    
    @Autowired(required = false)
    @Qualifier("localDocumentConverter")
    private Object documentConverter;
    
    @Override
    public String getConverterName() {
        return "JODConverter";
    }
    
    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.info("Starting JODConverter conversion for file: {}", outputFile.getName());
        
        if (documentConverter == null) {
            throw new RuntimeException("JODConverter DocumentConverter is not available");
        }
        
        try {
            performJodConversion(inputStream, outputFile);
            log.info("JODConverter conversion completed successfully for file: {}", outputFile.getName());
        } catch (Exception e) {
            log.error("JODConverter conversion failed for file: {}", outputFile.getName(), e);
            throw new RuntimeException("JODConverter conversion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return documentConverter != null;
    }
    
    /**
     * 执行JODConverter转换
     * 
     * @param inputStream 输入流
     * @param outputFile 输出文件
     * @throws Exception 转换异常
     */
    private void performJodConversion(InputStream inputStream, File outputFile) throws Exception {
        // 创建临时输入文件
        Path tempInputFile = Files.createTempFile("jod_input_" + UUID.randomUUID(), ".docx");
        
        try {
            // 将InputStream写入临时文件
            try (FileOutputStream fos = new FileOutputStream(tempInputFile.toFile())) {
                inputStream.transferTo(fos);
            }
            
            // 使用反射调用JODConverter，避免编译时依赖
            Object converter = this.documentConverter;
            
            // 调用 converter.convert(inputFile).to(outputFile).execute();
            Object convertOperation = converter.getClass()
                    .getMethod("convert", File.class)
                    .invoke(converter, tempInputFile.toFile());
            
            Object toOperation = convertOperation.getClass()
                    .getMethod("to", File.class)
                    .invoke(convertOperation, outputFile);
            
            toOperation.getClass()
                    .getMethod("execute")
                    .invoke(toOperation);
            
            log.debug("JODConverter conversion completed for file: {}", outputFile.getName());
            
        } finally {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempInputFile);
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", tempInputFile, e);
            }
        }
    }
} 