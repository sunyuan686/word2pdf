package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 基于Docx4j的Word转PDF转换器
 * 
 * 简化实现策略：
 * 1. 使用基础的字体映射确保兼容性
 * 2. 专注于稳定的转换质量
 * 3. 减少复杂的API调用避免兼容性问题
 * 4. 保持良好的错误处理
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
        log.info("Starting Docx4j conversion for file: {}", outputFile.getName());
        
        try {
            // 加载Word文档
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);
            
            // 设置字体映射
            setupFontMapping(wordPackage);
            
            // 创建FO设置
            FOSettings foSettings = createFOSettings(wordPackage);
            
            // 执行转换
            performConversion(wordPackage, foSettings, outputFile);
            
            log.info("Docx4j conversion completed successfully for file: {}", outputFile.getName());
            
        } catch (Exception e) {
            log.error("Docx4j conversion failed for file: {}", outputFile.getName(), e);
            throw new RuntimeException("Docx4j conversion failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            Class.forName("org.docx4j.Docx4J");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("Docx4j converter not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 设置字体映射
     */
    private void setupFontMapping(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Setting up font mapping for better Chinese support");
        
        try {
            // 发现系统字体
            PhysicalFonts.discoverPhysicalFonts();
            
            // 使用默认的字体映射器
            IdentityPlusMapper fontMapper = new IdentityPlusMapper();
            wordPackage.setFontMapper(fontMapper);
            
            log.debug("Font mapping configured successfully");
        } catch (Exception e) {
            log.warn("Could not configure font mapping: {}", e.getMessage());
            // 继续处理，使用默认设置
        }
    }
    
    /**
     * 创建FO设置
     */
    private FOSettings createFOSettings(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Creating FO settings for high-quality output");
        
        FOSettings foSettings = Docx4J.createFOSettings();
        foSettings.setWmlPackage(wordPackage);
        
        // 设置基本的输出参数
        configureFOSettings(foSettings);
        
        return foSettings;
    }
    
    /**
     * 配置FO设置参数
     */
    private void configureFOSettings(FOSettings foSettings) {
        try {
            // 设置基础参数
            foSettings.setApacheFopMime("application/pdf");
            
            log.debug("FO settings configured for PDF output");
        } catch (Exception e) {
            log.warn("Could not configure all FO settings: {}", e.getMessage());
        }
    }
    
    /**
     * 执行转换
     */
    private void performConversion(WordprocessingMLPackage wordPackage, 
                                   FOSettings foSettings, 
                                   File outputFile) throws Exception {
        log.debug("Starting PDF conversion");
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            // 直接转换为PDF
            Docx4J.toPDF(wordPackage, fos);
            
            log.debug("PDF conversion completed successfully");
        } catch (Exception e) {
            log.error("Failed to convert to PDF: {}", e.getMessage());
            throw new RuntimeException("PDF conversion failed", e);
        }
    }
} 