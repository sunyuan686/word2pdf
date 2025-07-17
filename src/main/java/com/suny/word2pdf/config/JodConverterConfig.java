package com.suny.word2pdf.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * JODConverter配置类
 * 
 * 配置JODConverter相关的Bean和设置
 * 
 * @author suny
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true")
public class JodConverterConfig {
    
    /**
     * 验证LibreOffice安装
     * 
     * @return 是否安装了LibreOffice
     */
    @Bean
    public Boolean libreOfficeInstalled() {
        String[] possiblePaths = {
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
            "/usr/bin/libreoffice",
            "/usr/local/bin/libreoffice",
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
        };
        
        for (String path : possiblePaths) {
            File libreOfficeFile = new File(path);
            if (libreOfficeFile.exists() && libreOfficeFile.canExecute()) {
                log.info("Found LibreOffice at: {}", path);
                return true;
            }
        }
        
        log.warn("LibreOffice not found in standard locations. JODConverter may not work properly.");
        return false;
    }
} 