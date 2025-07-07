package com.suny.word2pdf.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用配置类
 * 
 * @author suny
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app")
public class ApplicationConfig {
    
    /**
     * LibreOffice可执行文件路径
     */
    private String libreOfficePath = "/Applications/LibreOffice.app/Contents/MacOS/soffice";
    
    /**
     * 临时文件目录
     */
    private String tempDir = "/tmp/word2pdf";
    
    /**
     * 转换超时时间（毫秒）
     */
    private Long conversionTimeout = 30000L;
    
    /**
     * 初始化配置
     */
    @PostConstruct
    public void init() {
        log.info("Initializing application configuration");
        validateLibreOfficeInstallation();
        createTempDirectory();
        log.info("Application configuration initialized successfully");
    }
    
    /**
     * 验证LibreOffice安装
     */
    private void validateLibreOfficeInstallation() {
        File libreOfficeFile = new File(libreOfficePath);
        if (!libreOfficeFile.exists() || !libreOfficeFile.canExecute()) {
            log.warn("LibreOffice not found at path: {}", libreOfficePath);
            log.warn("LibreOffice conversion will not be available");
        } else {
            log.info("LibreOffice found at path: {}", libreOfficePath);
        }
    }
    
    /**
     * 创建临时目录
     */
    private void createTempDirectory() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
                log.info("Created temp directory: {}", tempDir);
            } else {
                log.info("Temp directory already exists: {}", tempDir);
            }
        } catch (Exception e) {
            log.error("Failed to create temp directory: {}", tempDir, e);
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }
} 