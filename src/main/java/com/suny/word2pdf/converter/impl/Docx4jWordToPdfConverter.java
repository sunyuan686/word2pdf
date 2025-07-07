package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFonts;
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
            
            // 配置中文字体支持
            configureFontMapper(wordPackage);
            
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
     * 配置字体映射以支持中文显示
     * 
     * @param wordPackage Word文档包
     */
    private void configureFontMapper(WordprocessingMLPackage wordPackage) {
        try {
            // 发现系统字体
            PhysicalFonts.discoverPhysicalFonts();
            
            // 创建字体映射器
            Mapper fontMapper = new IdentityPlusMapper();
            wordPackage.setFontMapper(fontMapper);
            
            // 配置中文字体映射
            configureFontMapping(fontMapper);
            
            log.debug("Chinese font mapper configured successfully for Docx4j converter");
        } catch (Exception e) {
            log.warn("Failed to configure font mapper for Docx4j converter: {}", e.getMessage());
        }
    }
    
    /**
     * 配置中文字体映射
     * 
     * @param fontMapper 字体映射器
     */
    private void configureFontMapping(Mapper fontMapper) {
        try {
            // 配置常见中文字体映射
            String[] chineseFontNames = {
                "SimSun", "宋体",
                "SimHei", "黑体", 
                "Microsoft YaHei", "微软雅黑",
                "PingFang SC", "苹方",
                "Hiragino Sans GB", "冬青黑体简体中文",
                "Arial Unicode MS"
            };
            
            // 查找可用的中文字体
            String availableChineseFont = findAvailableChineseFont();
            
            if (availableChineseFont != null) {
                // 映射所有中文字体名称到可用字体
                for (String fontName : chineseFontNames) {
                    try {
                        fontMapper.put(fontName, PhysicalFonts.get(availableChineseFont));
                        log.debug("Mapped font '{}' to '{}'", fontName, availableChineseFont);
                    } catch (Exception e) {
                        log.debug("Failed to map font '{}': {}", fontName, e.getMessage());
                    }
                }
            } else {
                log.warn("No suitable Chinese font found for Docx4j converter");
            }
        } catch (Exception e) {
            log.warn("Failed to configure Chinese font mapping: {}", e.getMessage());
        }
    }
    
    /**
     * 查找可用的中文字体
     * 
     * @return 可用的中文字体名称，如果没有找到则返回null
     */
    private String findAvailableChineseFont() {
        String[] candidateFonts = {
            "Arial Unicode MS",
            "PingFang SC",
            "Hiragino Sans GB",
            "Microsoft YaHei",
            "SimSun",
            "SimHei"
        };
        
        for (String fontName : candidateFonts) {
            try {
                if (PhysicalFonts.get(fontName) != null) {
                    log.debug("Found available Chinese font: {}", fontName);
                    return fontName;
                }
            } catch (Exception e) {
                log.debug("Font '{}' not available: {}", fontName, e.getMessage());
            }
        }
        
        return null;
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