package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 基于Docx4j的Word转PDF转换器
 * 
 * @author suny
 */
@Slf4j
@Component
public class Docx4jWordToPdfConverter implements WordToPdfConverter {
    
    @Override
    public String getConverterName() {
        return "Docx4j";
    }
    
    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.debug("Starting Docx4j conversion for file: {}", outputFile.getName());
        
        try {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);
            
            FOSettings foSettings = createFoSettings();
            
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                Docx4J.toPDF(wordPackage, outputStream);
            }
            
            log.debug("Docx4j conversion completed successfully for file: {}", outputFile.getName());
        } catch (Exception e) {
            log.error("Docx4j conversion failed for file: {}", outputFile.getName(), e);
            throw new RuntimeException("Docx4j conversion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // 尝试加载Docx4j相关类以验证可用性
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            Class.forName("org.docx4j.Docx4J");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("Docx4j converter not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建FO设置
     * 
     * @return FO设置
     */
    private FOSettings createFoSettings() {
        FOSettings foSettings = Docx4J.createFOSettings();
        foSettings.setWmlPackage(null);
        // 可以在这里配置更多FO选项
        return foSettings;
    }
} 