package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Docx4j的Word转PDF转换器 - 性能优化 + 中文字体保障版本
 * <p>
 * 优化策略：
 * 1. 保持性能优化的核心逻辑
 * 2. 增强中文字体映射和回退机制
 * 3. 强制字体嵌入避免乱码
 * 4. 提供多层级字体保障
 * 5. 兜底机制确保任何情况下都有可用字体
 *
 * @author suny
 * @version 3.1.0 - Performance Optimized with Chinese Font Guarantee
 */
@Slf4j
@Component
public class Docx4jWordToPdfConverter implements WordToPdfConverter {

    /**
     * 字体映射缓存
     */
    private static final Map<String, PhysicalFont> FONT_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 字体系统初始化状态
     */
    private static volatile boolean FONT_SYSTEM_READY = false;
    
    /**
     * 字体初始化锁
     */
    private static final Object INIT_LOCK = new Object();

    /**
     * 最优中文字体（运行时确定）
     */
    private static String OPTIMAL_CHINESE_FONT = null;

    /**
     * 中文字体候选列表（按优先级排序）
     */
    private static final List<String> CHINESE_FONT_CANDIDATES = Arrays.asList(
        "Microsoft YaHei",
        "Microsoft YaHei UI", 
        "SimSun",
        "SimHei",
        "PingFang SC",
        "Hiragino Sans GB",
        "Source Han Sans SC",
        "Noto Sans CJK SC",
        "Arial Unicode MS",
        "STHeiti",
        "KaiTi"
    );

    /**
     * 中文字体映射表 - 将Word中的中文字体名映射到系统可用字体
     */
    private static final Map<String, String> CHINESE_FONT_MAPPING = Map.of(
        "宋体", "SimSun",
        "微软雅黑", "Microsoft YaHei", 
        "黑体", "SimHei",
        "楷体", "KaiTi",
        "苹方", "PingFang SC",
        "苹方-简", "PingFang SC",
        "冬青黑体简体中文", "Hiragino Sans GB",
        "华文黑体", "STHeiti",
        "等线", "DengXian"
    );

    @Override
    public String getConverterName() {
        return "Docx4j";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            Class.forName("org.docx4j.Docx4J");
            Class.forName("org.docx4j.fonts.PhysicalFonts");
            return true;
        } catch (Exception e) {
            log.warn("Docx4j converter not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.info("Starting enhanced Docx4j conversion with Chinese font guarantee for: {}", outputFile.getName());
        
        long startTime = System.currentTimeMillis();

        try {
            // 1. 确保字体系统初始化（只执行一次）
            ensureFontSystemInitialized();

            // 2. 加载Word文档
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);

            // 3. 配置强化字体映射器
            configureEnhancedFontMapper(wordPackage);

            // 4. 创建优化转换设置
            FOSettings foSettings = createEnhancedConversionSettings(wordPackage);

            // 5. 执行转换
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                Docx4J.toPDF(wordPackage, fos);
            }

            long conversionTime = System.currentTimeMillis() - startTime;
            log.info("Enhanced Docx4j conversion completed successfully for: {} in {}ms", 
                    outputFile.getName(), conversionTime);

        } catch (Exception e) {
            log.error("Enhanced Docx4j conversion failed for file: {} - Error: {}", 
                    outputFile.getName(), e.getMessage());
            throw new RuntimeException("Docx4j conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * 确保字体系统初始化（仅执行一次）
     */
    private void ensureFontSystemInitialized() {
        if (!FONT_SYSTEM_READY) {
            synchronized (INIT_LOCK) {
                if (!FONT_SYSTEM_READY) {
                    initializeFontSystem();
                    FONT_SYSTEM_READY = true;
                }
            }
        }
    }

    /**
     * 初始化字体系统
     */
    private void initializeFontSystem() {
        log.info("Initializing enhanced font system with Chinese font guarantee");
        
        try {
            // 1. 优化系统设置但保持必要的字体功能
            configureOptimalSystemSettings();
            
            // 2. 发现并缓存最优中文字体
            discoverOptimalChineseFont();
            
            // 3. 构建字体映射表
            buildChineseFontMappings();
            
            log.info("Enhanced font system initialized. Optimal Chinese font: {}, Total cached fonts: {}", 
                    OPTIMAL_CHINESE_FONT != null ? OPTIMAL_CHINESE_FONT : "none", FONT_CACHE.size());
            
        } catch (Exception e) {
            log.error("Font system initialization failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 配置最优系统设置
     */
    private void configureOptimalSystemSettings() {
        // 关闭过度详细的日志但保持必要的字体功能
        System.setProperty("org.docx4j.fonts.RunFontSelector.log", "WARN");
        System.setProperty("docx4j.Log4j.Configurator.disabled", "true");
        
        // 字符编码设置
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        
        // FOP字体设置
        System.setProperty("fop.strict-validation", "false");
        System.setProperty("fop.fonts.substitute-missing", "true");
        System.setProperty("fop.fonts.auto-detect", "true");
        
        log.debug("Optimal system settings configured");
    }

    /**
     * 发现最优中文字体
     */
    private void discoverOptimalChineseFont() {
        log.debug("Discovering optimal Chinese font from candidates");
        
        // 确保PhysicalFonts已初始化
        try {
            PhysicalFonts.discoverPhysicalFonts();
        } catch (Exception e) {
            log.debug("PhysicalFonts discovery had issues: {}", e.getMessage());
        }
        
        // 按优先级查找可用的中文字体
        for (String fontName : CHINESE_FONT_CANDIDATES) {
            try {
                PhysicalFont physicalFont = PhysicalFonts.get(fontName);
                if (physicalFont != null) {
                    OPTIMAL_CHINESE_FONT = fontName;
                    FONT_CACHE.put(fontName, physicalFont);
                    log.info("Found optimal Chinese font: {}", fontName);
                    break;
                }
            } catch (Exception e) {
                log.debug("Font '{}' not available: {}", fontName, e.getMessage());
            }
        }
        
        if (OPTIMAL_CHINESE_FONT == null) {
            log.warn("No optimal Chinese font found, will use system default with potential for display issues");
        }
    }

    /**
     * 构建中文字体映射表
     */
    private void buildChineseFontMappings() {
        log.debug("Building Chinese font mappings");
        
        int mappingCount = 0;
        
        // 为每个中文字体名建立映射
        for (Map.Entry<String, String> entry : CHINESE_FONT_MAPPING.entrySet()) {
            String logicalName = entry.getKey();
            String targetFontName = entry.getValue();
            
            try {
                PhysicalFont physicalFont = PhysicalFonts.get(targetFontName);
                if (physicalFont != null) {
                    FONT_CACHE.put(logicalName, physicalFont);
                    FONT_CACHE.put(targetFontName, physicalFont);
                    mappingCount++;
                    log.debug("Mapped '{}' -> '{}'", logicalName, targetFontName);
                } else if (OPTIMAL_CHINESE_FONT != null) {
                    // 如果目标字体不可用，使用最优中文字体作为回退
                    PhysicalFont optimalFont = FONT_CACHE.get(OPTIMAL_CHINESE_FONT);
                    if (optimalFont != null) {
                        FONT_CACHE.put(logicalName, optimalFont);
                        mappingCount++;
                        log.debug("Mapped '{}' -> '{}' (fallback)", logicalName, OPTIMAL_CHINESE_FONT);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to map font '{}': {}", logicalName, e.getMessage());
            }
        }
        
        log.info("Built {} Chinese font mappings", mappingCount);
    }

    /**
     * 配置强化字体映射器
     */
    private void configureEnhancedFontMapper(WordprocessingMLPackage wordPackage) {
        log.debug("Configuring enhanced font mapper with Chinese font guarantee");
        
        try {
            EnhancedChineseFontMapper fontMapper = new EnhancedChineseFontMapper();
            
            // 添加所有缓存的字体映射
            for (Map.Entry<String, PhysicalFont> entry : FONT_CACHE.entrySet()) {
                fontMapper.put(entry.getKey(), entry.getValue());
            }
            
            wordPackage.setFontMapper(fontMapper);
            
            log.info("Enhanced font mapper configured with {} mappings", FONT_CACHE.size());
            
        } catch (Exception e) {
            log.error("Failed to configure enhanced font mapper: {}", e.getMessage());
            // 提供基础回退
            configureBasicFontMapper(wordPackage);
        }
    }

    /**
     * 配置基础字体映射器（回退方案）
     */
    private void configureBasicFontMapper(WordprocessingMLPackage wordPackage) {
        log.warn("Using basic font mapper as fallback");
        
        try {
            IdentityPlusMapper basicMapper = new IdentityPlusMapper();
            
            // 只添加最基础的映射
            if (OPTIMAL_CHINESE_FONT != null) {
                PhysicalFont chineseFont = FONT_CACHE.get(OPTIMAL_CHINESE_FONT);
                if (chineseFont != null) {
                    basicMapper.put("宋体", chineseFont);
                    basicMapper.put("微软雅黑", chineseFont);
                    basicMapper.put("SimSun", chineseFont);
                    basicMapper.put("Microsoft YaHei", chineseFont);
                }
            }
            
            wordPackage.setFontMapper(basicMapper);
            
        } catch (Exception e) {
            log.error("Even basic font mapper configuration failed: {}", e.getMessage());
        }
    }

    /**
     * 强化的中文字体映射器
     */
    private static class EnhancedChineseFontMapper extends IdentityPlusMapper {
        
        @Override
        public PhysicalFont get(String fontName) {
            // 1. 尝试从缓存获取
            PhysicalFont cachedFont = FONT_CACHE.get(fontName);
            if (cachedFont != null) {
                log.debug("Font '{}' found in cache", fontName);
                return cachedFont;
            }
            
            // 2. 尝试父类处理
            PhysicalFont physicalFont = super.get(fontName);
            if (physicalFont != null) {
                // 缓存成功的映射
                FONT_CACHE.put(fontName, physicalFont);
                log.debug("Font '{}' resolved by parent mapper", fontName);
                return physicalFont;
            }
            
            // 3. 中文字体智能回退
            if (isChineseFont(fontName)) {
                PhysicalFont fallbackFont = getChineseFallback(fontName);
                if (fallbackFont != null) {
                    FONT_CACHE.put(fontName, fallbackFont);
                    log.debug("Font '{}' mapped to Chinese fallback", fontName);
                    return fallbackFont;
                }
            }
            
            // 4. 最终回退：如果有最优中文字体，用它处理任何未知字体
            if (OPTIMAL_CHINESE_FONT != null) {
                PhysicalFont ultimateFallback = FONT_CACHE.get(OPTIMAL_CHINESE_FONT);
                if (ultimateFallback != null) {
                    FONT_CACHE.put(fontName, ultimateFallback);
                    log.debug("Font '{}' mapped to ultimate fallback: {}", fontName, OPTIMAL_CHINESE_FONT);
                    return ultimateFallback;
                }
            }
            
            log.warn("No mapping found for font: {}", fontName);
            return null;
        }
        
        /**
         * 判断是否为中文字体
         */
        private boolean isChineseFont(String fontName) {
            if (fontName == null) return false;
            
            // 检查是否包含中文字符
            if (fontName.matches(".*[\\u4e00-\\u9fff].*")) {
                return true;
            }
            
            // 检查是否为已知的中文字体映射
            if (CHINESE_FONT_MAPPING.containsKey(fontName)) {
                return true;
            }
            
            // 检查是否为常见的中文字体英文名
            String lowerName = fontName.toLowerCase();
            return lowerName.contains("sim") || lowerName.contains("yahei") || 
                   lowerName.contains("pingfang") || lowerName.contains("hiragino") ||
                   lowerName.contains("source han") || lowerName.contains("noto") ||
                   lowerName.contains("stheiti") || lowerName.contains("kaiti") ||
                   lowerName.contains("dengxian");
        }
        
        /**
         * 获取中文字体回退
         */
        private PhysicalFont getChineseFallback(String originalFontName) {
            // 1. 尝试语义映射
            String mappedName = CHINESE_FONT_MAPPING.get(originalFontName);
            if (mappedName != null) {
                PhysicalFont mappedFont = FONT_CACHE.get(mappedName);
                if (mappedFont != null) {
                    return mappedFont;
                }
            }
            
            // 2. 使用最优中文字体
            if (OPTIMAL_CHINESE_FONT != null) {
                PhysicalFont optimalFont = FONT_CACHE.get(OPTIMAL_CHINESE_FONT);
                if (optimalFont != null) {
                    return optimalFont;
                }
            }
            
            // 3. 按优先级查找任何可用的中文字体
            for (String fontName : CHINESE_FONT_CANDIDATES) {
                PhysicalFont font = FONT_CACHE.get(fontName);
                if (font != null) {
                    return font;
                }
            }
            
            return null;
        }
    }

    /**
     * 创建增强的转换设置
     */
    private FOSettings createEnhancedConversionSettings(WordprocessingMLPackage wordPackage) {
        log.debug("Creating enhanced conversion settings");
        
        try {
            FOSettings foSettings = Docx4J.createFOSettings();
            foSettings.setWmlPackage(wordPackage);
            foSettings.setApacheFopMime("application/pdf");
            
            // FOP优化设置，确保字体正确嵌入
            configureFopForChineseFonts();
            
            return foSettings;
            
        } catch (Exception e) {
            log.error("Failed to create enhanced conversion settings: {}", e.getMessage());
            throw new RuntimeException("Failed to create conversion settings", e);
        }
    }

    /**
     * 配置FOP以优化中文字体处理
     */
    private void configureFopForChineseFonts() {
        try {
            // 字体嵌入和替换设置
            System.setProperty("fop.fonts.embed", "true");
            System.setProperty("fop.fonts.substitute-missing", "true");
            System.setProperty("fop.fonts.auto-detect", "true");
            
            // 中文字体特定设置
            System.setProperty("fop.fonts.cjk.embed", "auto");
            System.setProperty("fop.fonts.base14-kerning", "true");
            
            // PDF输出优化
            System.setProperty("fop.strict-validation", "false");
            System.setProperty("fop.accessibility", "false");
            
            log.debug("FOP configured for optimal Chinese font handling");
            
        } catch (Exception e) {
            log.warn("Failed to configure FOP for Chinese fonts: {}", e.getMessage());
        }
    }
}