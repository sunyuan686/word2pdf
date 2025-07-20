package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;

/**
 * 基于JODConverter的Word转PDF转换器
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
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true")
public class JodConverterWordToPdfConverter implements WordToPdfConverter {

    private final DocumentConverter documentConverter;

    /**
     * 构造函数注入DocumentConverter
     *
     * @param documentConverter JODConverter文档转换器
     */
    public JodConverterWordToPdfConverter(DocumentConverter documentConverter) {
        this.documentConverter = documentConverter;
        log.info("JodConverterWordToPdfConverter initialized with converter: {}",
                documentConverter != null ? documentConverter.getClass().getSimpleName() : "null");
    }

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

        long startTime = System.currentTimeMillis();
        long actualConversionTime = 0;

        try {
            log.debug("DocumentConverter class: {}", documentConverter.getClass().getName());
            log.debug("DocumentConverter hash: {}", System.identityHashCode(documentConverter));

            long tempFileStart = System.currentTimeMillis();
            performJodConversion(inputStream, outputFile);
            long tempFileEnd = System.currentTimeMillis();
            
            actualConversionTime = tempFileEnd - tempFileStart;
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("JODConverter conversion completed successfully for file: {} in {}ms", 
                    outputFile.getName(), duration);
            log.info("Performance breakdown - Total: {}ms, Conversion: {}ms", 
                    duration, actualConversionTime);
                    
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("JODConverter conversion failed for file: {} after {}ms", 
                    outputFile.getName(), duration, e);
            throw new RuntimeException("JODConverter conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return documentConverter != null;
    }

    /**
     * 执行JODConverter转换
     * 使用类型安全的直接调用，无反射开销
     *
     * @param inputStream 输入流
     * @param outputFile  输出文件
     * @throws Exception 转换异常
     */
    private void performJodConversion(InputStream inputStream, File outputFile) throws Exception {
        


        documentConverter.convert(inputStream)
                .to(outputFile)
                .execute();

        log.debug("JODConverter conversion completed for file: {}", outputFile.getName());

    }

}