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
 * 配置文档转换相关的参数和初始化设置
 * 
 * @author suny
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
@Slf4j
public class ApplicationConfig {
    
    /**
     * LibreOffice可执行文件路径
     */
    private String libreOfficePath;
    
    /**
     * 临时文件目录
     */
    private String tempDir;
    
    /**
     * 转换超时时间（毫秒）
     */
    private Long conversionTimeout = 30000L;
    
    /**
     * 应用初始化
     * 设置字符编码确保中文支持
     */
    @PostConstruct
    public void initializeApplication() {
        // 确保字符编码正确设置
        configureCharacterEncoding();
        
        // 创建临时目录
        createTempDirectory();
        
        log.info("Application configuration initialized successfully");
        log.info("Temp directory: {}", tempDir);
        log.info("LibreOffice path: {}", libreOfficePath);
    }
    
    /**
     * 配置字符编码以支持中文
     */
    private void configureCharacterEncoding() {
        try {
            // 设置系统字符编码属性
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("sun.jnu.encoding", "UTF-8");
            System.setProperty("user.language", "zh");
            System.setProperty("user.country", "CN");
            
            log.debug("Character encoding configured for Chinese support");
        } catch (Exception e) {
            log.warn("Failed to configure character encoding: {}", e.getMessage());
        }
    }
    
    /**
     * 创建临时目录
     */
    private void createTempDirectory() {
        try {
            File tempDirectory = new File(tempDir);
            if (!tempDirectory.exists()) {
                boolean created = tempDirectory.mkdirs();
                if (created) {
                    log.info("Created temp directory: {}", tempDir);
                } else {
                    log.warn("Failed to create temp directory: {}", tempDir);
                }
            }
        } catch (Exception e) {
            log.error("Error creating temp directory: {}", e.getMessage());
        }
    }
} 