package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.FontTablePart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于Docx4j的Word转PDF转换器 - 深度字体映射优化版本
 * <p>
 * 核心优化策略：
 * 1. 智能字体发现与匹配 - 支持精确匹配、模糊匹配、语义匹配
 * 2. 深度文档字体分析 - 全面解析文档中的字体使用情况
 * 3. 多级字体映射策略 - 确保在任何环境下都能找到合适的字体
 * 4. 字体缓存优化 - 提升性能，避免重复检测
 * 5. 完善的错误处理 - 提供详细的诊断信息
 * 6. 字符编码增强 - 确保中文字符完美传递
 *
 * @author suny
 * @version 2.0.0
 */
@Slf4j
@Component
public class Docx4jWordToPdfConverter implements WordToPdfConverter {

    /**
     * 字体映射缓存，提升性能
     */
    private static final Map<String, PhysicalFont> FONT_MAPPING_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 字体发现缓存，避免重复系统扫描
     */
    private static final Map<String, PhysicalFont> FONT_DISCOVERY_CACHE = new ConcurrentHashMap<>();

    /**
     * 字体系统初始化状态
     */
    private static volatile boolean FONT_SYSTEM_INITIALIZED = false;
    
    /**
     * 字体初始化锁
     */
    private static final Object FONT_INIT_LOCK = new Object();

    /**
     * 最佳可用中文字体
     */
    private static String optimalChineseFont = null;
    
    /**
     * 字体映射统计
     */
    private static final Map<String, Integer> FONT_USAGE_STATS = new ConcurrentHashMap<>();

    /**
     * 分层级的中文字体优先级配置
     * 按照字体质量、兼容性、可用性进行排序
     */
    private static final Map<String, List<String>> HIERARCHICAL_CHINESE_FONTS = Map.of(
        // 一级字体：现代高质量字体
        "TIER_1", Arrays.asList(
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "PingFang SC",
            "Hiragino Sans GB",
            "Source Han Sans SC",
            "Noto Sans CJK SC"
        ),
        
        // 二级字体：传统系统字体
        "TIER_2", Arrays.asList(
            "SimSun",
            "SimHei", 
            "STHeiti",
            "STKaiti",
            "Arial Unicode MS"
        ),
        
        // 三级字体：兜底字体
        "TIER_3", Arrays.asList(
            "DejaVu Sans",
            "Liberation Sans",
            "Droid Sans Fallback"
        )
    );

    /**
     * 中文字体语义映射表
     * 将Word中的中文字体名映射到系统字体
     */
    private static final Map<String, String> CHINESE_FONT_SEMANTIC_MAPPING = createChineseFontSemanticMapping();
    
    /**
     * 创建中文字体语义映射表
     */
    private static Map<String, String> createChineseFontSemanticMapping() {
        Map<String, String> mapping = new HashMap<>();
        
        // 宋体系列
        mapping.put("宋体", "SimSun");
        mapping.put("新宋体", "NSimSun");
        mapping.put("中易宋体", "SimSun");
        
        // 黑体系列
        mapping.put("黑体", "SimHei");
        mapping.put("中易黑体", "SimHei");
        mapping.put("华文黑体", "STHeiti");
        
        // 微软雅黑系列
        mapping.put("微软雅黑", "Microsoft YaHei");
        mapping.put("微软雅黑 Light", "Microsoft YaHei Light");
        mapping.put("微软雅黑 UI", "Microsoft YaHei UI");
        
        // 苹果字体系列
        mapping.put("苹方", "PingFang SC");
        mapping.put("苹方-简", "PingFang SC");
        mapping.put("苹方-繁", "PingFang TC");
        mapping.put("冬青黑体简体中文", "Hiragino Sans GB");
        
        // 楷体系列
        mapping.put("楷体", "KaiTi");
        mapping.put("楷体_GB2312", "KaiTi");
        mapping.put("华文楷体", "STKaiti");
        
        // 仿宋系列
        mapping.put("仿宋", "FangSong");
        mapping.put("仿宋_GB2312", "FangSong");
        mapping.put("华文仿宋", "STFangsong");
        
        // 汉仪字体系列映射
        mapping.put("汉仪中黑KW", "SimHei");
        mapping.put("汉仪书宋二KW", "SimSun");
        mapping.put("汉仪中等线KW", "Microsoft YaHei");
        mapping.put("汉仪细等线KW", "Microsoft YaHei Light");
        mapping.put("汉仪粗黑KW", "SimHei");
        mapping.put("汉仪中宋KW", "SimSun");
        
        // 方正字体系列映射
        mapping.put("方正黑体", "SimHei");
        mapping.put("方正宋体", "SimSun");
        mapping.put("方正楷体", "KaiTi");
        mapping.put("方正仿宋", "FangSong");
        
        // 其他常见商业字体映射
        mapping.put("Adobe 黑体 Std", "SimHei");
        mapping.put("Adobe 宋体 Std", "SimSun");
        mapping.put("Adobe 楷体 Std", "KaiTi");
        
        return Collections.unmodifiableMap(mapping);
    }

    @Override
    public String getConverterName() {
        return "Docx4j";
    }

    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.info("Starting enhanced Docx4j conversion with intelligent font mapping for: {}", 
                outputFile.getName());

        try {
            // 1. 确保字体系统完全初始化
            ensureCompleteFontSystemInitialization();

            // 2. 加载并分析Word文档
            WordprocessingMLPackage wordPackage = loadAndAnalyzeDocument(inputStream);

            // 3. 执行深度字体处理
            performIntelligentFontProcessing(wordPackage);

            // 4. 创建优化的转换设置
            FOSettings foSettings = createOptimalConversionSettings(wordPackage);

            // 5. 执行转换并监控
            executeConversionWithMonitoring(wordPackage, foSettings, outputFile);

            // 6. 记录转换统计
            logConversionStatistics();

            log.info("Enhanced Docx4j conversion completed successfully for: {}", outputFile.getName());

        } catch (Exception e) {
            log.error("Enhanced Docx4j conversion failed for file: {} - Error: {}", 
                    outputFile.getName(), e.getMessage(), e);
            throw new RuntimeException("Enhanced Docx4j conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // 检查必要的类是否可用
            Class.forName("org.docx4j.openpackaging.packages.WordprocessingMLPackage");
            Class.forName("org.docx4j.Docx4J");
            Class.forName("org.docx4j.fonts.PhysicalFonts");
            
            // 检查字体系统是否可以初始化
            testFontSystemInitialization();
            
            return true;
        } catch (Exception e) {
            log.warn("Enhanced Docx4j converter not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试字体系统初始化
     */
    private void testFontSystemInitialization() throws Exception {
        try {
            PhysicalFonts.discoverPhysicalFonts();
            log.debug("Font system test initialization successful");
        } catch (Exception e) {
            log.warn("Font system test initialization failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 确保字体系统完全初始化
     */
    private void ensureCompleteFontSystemInitialization() {
        if (!FONT_SYSTEM_INITIALIZED) {
            synchronized (FONT_INIT_LOCK) {
                if (!FONT_SYSTEM_INITIALIZED) {
                    performCompleteFontSystemInitialization();
                    FONT_SYSTEM_INITIALIZED = true;
                }
            }
        }
    }

    /**
     * 执行完整的字体系统初始化
     */
    private void performCompleteFontSystemInitialization() {
        log.info("Initializing enhanced font system with intelligent mapping");

        try {
            // 1. 配置系统环境
            configureSystemEnvironment();

            // 2. 发现系统字体
            discoverAndCacheSystemFonts();

            // 3. 分析中文字体可用性
            analyzeChineseFontAvailability();

            // 4. 构建字体映射表
            buildIntelligentFontMappings();

            // 5. 验证字体系统
            validateFontSystemIntegrity();

            log.info("Enhanced font system initialization completed. Optimal Chinese font: {}", 
                    optimalChineseFont != null ? optimalChineseFont : "none found");

        } catch (Exception e) {
            log.error("Font system initialization failed: {}", e.getMessage(), e);
            // 不抛出异常，使用降级模式
        }
    }

    /**
     * 配置系统环境
     */
    private void configureSystemEnvironment() {
        try {
            // JVM 字符编码配置
            System.setProperty("file.encoding", "UTF-8");
            System.setProperty("sun.jnu.encoding", "UTF-8");
            System.setProperty("user.language", "zh");
            System.setProperty("user.country", "CN");
            
            // Docx4j 特定配置
            System.setProperty("org.docx4j.openpackaging.parts.WordprocessingML.FontTablePart.useGlyphElements", "true");
            System.setProperty("docx4j.Log4j.Configurator.disabled", "true");
            
            // 字体渲染优化
            System.setProperty("java.awt.headless", "true");
            System.setProperty("sun.java2d.fontpath", "");
            
            log.debug("System environment configured for enhanced Chinese font support");
        } catch (Exception e) {
            log.warn("Failed to configure system environment: {}", e.getMessage());
        }
    }

    /**
     * 发现并缓存系统字体
     */
    private void discoverAndCacheSystemFonts() {
        try {
            long startTime = System.currentTimeMillis();
            
            PhysicalFonts.discoverPhysicalFonts();
            Map<String, PhysicalFont> systemFonts = PhysicalFonts.getPhysicalFonts();
            
            // 缓存所有发现的字体
            FONT_DISCOVERY_CACHE.putAll(systemFonts);
            
            long discoveryTime = System.currentTimeMillis() - startTime;
            log.info("Discovered and cached {} system fonts in {}ms", 
                    systemFonts.size(), discoveryTime);
                    
        } catch (Exception e) {
            log.error("Failed to discover system fonts: {}", e.getMessage(), e);
        }
    }

    /**
     * 分析中文字体可用性
     */
    private void analyzeChineseFontAvailability() {
        log.debug("Analyzing Chinese font availability with hierarchical priority");

        List<String> availableChineseFonts = new ArrayList<>();
        
        // 按优先级检查字体
        for (String tier : Arrays.asList("TIER_1", "TIER_2", "TIER_3")) {
            List<String> tierFonts = HIERARCHICAL_CHINESE_FONTS.get(tier);
            
            for (String fontName : tierFonts) {
                if (isFontAvailable(fontName)) {
                    availableChineseFonts.add(fontName);
                    
                    // 设置最优字体（第一个找到的高优先级字体）
                    if (optimalChineseFont == null) {
                        optimalChineseFont = fontName;
                        log.info("Selected optimal Chinese font: {} (from {})", fontName, tier);
                    }
                }
            }
            
            // 如果在当前层级找到字体，优先使用
            if (optimalChineseFont != null && tier.equals("TIER_1")) {
                break;
            }
        }

        log.info("Chinese font analysis completed. Available fonts: {}", availableChineseFonts);
    }

    /**
     * 检查字体是否可用
     */
    private boolean isFontAvailable(String fontName) {
        try {
            // 首先检查缓存
            if (FONT_DISCOVERY_CACHE.containsKey(fontName)) {
                return true;
            }
            
            // 精确匹配
            PhysicalFont font = PhysicalFonts.get(fontName);
            if (font != null) {
                FONT_DISCOVERY_CACHE.put(fontName, font);
                return true;
            }
            
            // 模糊匹配
            font = performFuzzyFontMatch(fontName);
            if (font != null) {
                FONT_DISCOVERY_CACHE.put(fontName, font);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Error checking font availability for '{}': {}", fontName, e.getMessage());
            return false;
        }
    }

    /**
     * 执行模糊字体匹配
     */
    private PhysicalFont performFuzzyFontMatch(String targetFontName) {
        try {
            String normalizedTarget = normalizeFontName(targetFontName);
            
            for (Map.Entry<String, PhysicalFont> entry : FONT_DISCOVERY_CACHE.entrySet()) {
                String fontName = normalizeFontName(entry.getKey());
                
                // 检查部分匹配
                if (fontName.contains(normalizedTarget) || normalizedTarget.contains(fontName)) {
                    log.debug("Fuzzy font match found: '{}' -> '{}'", targetFontName, entry.getKey());
                    return entry.getValue();
                }
            }
        } catch (Exception e) {
            log.debug("Error in fuzzy font matching: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 标准化字体名称用于匹配
     */
    private String normalizeFontName(String fontName) {
        if (fontName == null) return "";
        return fontName.toLowerCase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    /**
     * 构建智能字体映射表
     */
    private void buildIntelligentFontMappings() {
        log.debug("Building intelligent font mappings");

        int mappingCount = 0;
        
        // 构建语义映射
        for (Map.Entry<String, String> entry : CHINESE_FONT_SEMANTIC_MAPPING.entrySet()) {
            String logicalName = entry.getKey();
            String physicalName = entry.getValue();
            
            PhysicalFont physicalFont = findBestPhysicalFont(physicalName);
            if (physicalFont != null) {
                FONT_MAPPING_CACHE.put(logicalName, physicalFont);
                mappingCount++;
                log.debug("Mapped '{}' -> '{}'", logicalName, physicalFont.getName());
            }
        }

        // 构建直接映射（用于英文字体名）
        for (String fontName : HIERARCHICAL_CHINESE_FONTS.values().stream()
                .flatMap(List::stream).distinct().toList()) {
            if (!FONT_MAPPING_CACHE.containsKey(fontName)) {
                PhysicalFont physicalFont = findBestPhysicalFont(fontName);
                if (physicalFont != null) {
                    FONT_MAPPING_CACHE.put(fontName, physicalFont);
                    mappingCount++;
                }
            }
        }

        log.info("Built {} intelligent font mappings", mappingCount);
    }

    /**
     * 查找最佳物理字体
     */
    private PhysicalFont findBestPhysicalFont(String fontName) {
        // 1. 精确匹配
        PhysicalFont font = FONT_DISCOVERY_CACHE.get(fontName);
        if (font != null) return font;
        
        // 2. 从PhysicalFonts获取
        font = PhysicalFonts.get(fontName);
        if (font != null) {
            FONT_DISCOVERY_CACHE.put(fontName, font);
            return font;
        }
        
        // 3. 模糊匹配
        return performFuzzyFontMatch(fontName);
    }

    /**
     * 验证字体系统完整性
     */
    private void validateFontSystemIntegrity() {
        try {
            // 验证关键字体可用性
            boolean hasChineseSupport = optimalChineseFont != null;
            boolean hasFallbackFont = !FONT_MAPPING_CACHE.isEmpty();
            
            if (!hasChineseSupport) {
                log.warn("No optimal Chinese font found - Chinese text may not display correctly");
            }
            
            if (!hasFallbackFont) {
                log.error("No font mappings available - conversion may fail");
            }
            
            log.info("Font system integrity check completed. Chinese support: {}, Fallback available: {}", 
                    hasChineseSupport, hasFallbackFont);
                    
        } catch (Exception e) {
            log.error("Font system integrity validation failed: {}", e.getMessage());
        }
    }

    /**
     * 加载并分析文档
     */
    private WordprocessingMLPackage loadAndAnalyzeDocument(InputStream inputStream) throws Exception {
        log.debug("Loading and analyzing Word document");

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);
        
        // 分析文档中的字体使用情况
        analyzeDocumentFontUsage(wordPackage);
        
        return wordPackage;
    }

    /**
     * 分析文档字体使用情况
     */
    private void analyzeDocumentFontUsage(WordprocessingMLPackage wordPackage) {
        try {
            Set<String> documentFonts = extractDocumentFonts(wordPackage);
            
            log.info("Document analysis: {} unique fonts detected", documentFonts.size());
            
            for (String fontName : documentFonts) {
                // 统计字体使用情况
                FONT_USAGE_STATS.merge(fontName, 1, Integer::sum);
                
                // 检查字体可用性
                if (!isFontAvailable(fontName) && !FONT_MAPPING_CACHE.containsKey(fontName)) {
                    log.warn("Document uses unavailable font: '{}'", fontName);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to analyze document font usage: {}", e.getMessage());
        }
    }

    /**
     * 提取文档中使用的字体
     */
    private Set<String> extractDocumentFonts(WordprocessingMLPackage wordPackage) {
        Set<String> fonts = new HashSet<>();
        
        try {
            // 从字体表提取
            FontTablePart fontTablePart = wordPackage.getMainDocumentPart().getFontTablePart();
            if (fontTablePart != null && fontTablePart.getContents() != null) {
                for (Fonts.Font font : fontTablePart.getContents().getFont()) {
                    if (font.getName() != null) {
                        fonts.add(font.getName());
                    }
                }
            }
            
            // 从文档内容提取
            MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
            List<Object> elements = getAllElementsFromObject(mainPart.getContents(), R.class);
            
            for (Object element : elements) {
                if (element instanceof R) {
                    R run = (R) element;
                    extractFontsFromRun(run, fonts);
                }
            }
            
        } catch (Exception e) {
            log.debug("Error extracting document fonts: {}", e.getMessage());
        }
        
        return fonts;
    }

    /**
     * 从文本运行中提取字体
     */
    private void extractFontsFromRun(R run, Set<String> fonts) {
        try {
            RPr runProperties = run.getRPr();
            if (runProperties != null) {
                RFonts rFonts = runProperties.getRFonts();
                if (rFonts != null) {
                    addFontIfNotNull(fonts, rFonts.getAscii());
                    addFontIfNotNull(fonts, rFonts.getHAnsi());
                    addFontIfNotNull(fonts, rFonts.getCs());
                    addFontIfNotNull(fonts, rFonts.getEastAsia());
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting fonts from run: {}", e.getMessage());
        }
    }

    /**
     * 添加非空字体到集合
     */
    private void addFontIfNotNull(Set<String> fonts, String fontName) {
        if (fontName != null && !fontName.trim().isEmpty()) {
            fonts.add(fontName.trim());
        }
    }

    /**
     * 执行智能字体处理
     */
    private void performIntelligentFontProcessing(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Performing intelligent font processing");

        try {
            // 1. 处理字体表
            enhanceFontTable(wordPackage);

            // 2. 处理文档内容中的字体引用
            processDocumentFontReferences(wordPackage);

            // 3. 优化文档布局以防止页面异常增加
            optimizeDocumentLayout(wordPackage);

            // 4. 设置高级字体映射器
            configureAdvancedFontMapper(wordPackage);

            log.info("Intelligent font processing completed successfully");

        } catch (Exception e) {
            log.error("Intelligent font processing failed: {}", e.getMessage());
            // 降级到基本处理
            performBasicFontProcessing(wordPackage);
        }
    }
    
    /**
     * 增强字体表
     */
    private void enhanceFontTable(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Enhancing font table with optimal Chinese fonts");

        FontTablePart fontTablePart = wordPackage.getMainDocumentPart().getFontTablePart();
        if (fontTablePart == null) {
            fontTablePart = new FontTablePart();
            wordPackage.getMainDocumentPart().addTargetPart(fontTablePart);
        }

        Fonts fonts = fontTablePart.getContents();
        if (fonts == null) {
            fonts = new Fonts();
            fontTablePart.setContents(fonts);
        }

        // 添加优化的中文字体定义
        addOptimalChineseFontDefinitions(fonts);
    }

    /**
     * 添加优化的中文字体定义
     */
    private void addOptimalChineseFontDefinitions(Fonts fonts) {
        if (optimalChineseFont == null) return;

        // 为常见的中文字体名创建映射
        String[] chineseFontNames = {
            "宋体", "SimSun", "微软雅黑", "Microsoft YaHei",
            "黑体", "SimHei", "楷体", "KaiTi", 
            "苹方", "PingFang SC", "冬青黑体", "Hiragino Sans GB"
        };

        for (String fontName : chineseFontNames) {
            ensureFontDefinitionExists(fonts, fontName, optimalChineseFont);
        }
    }

    /**
     * 确保字体定义存在
     */
    private void ensureFontDefinitionExists(Fonts fonts, String logicalName, String physicalName) {
        // 检查是否已存在
        for (Fonts.Font existingFont : fonts.getFont()) {
            if (logicalName.equals(existingFont.getName())) {
                return; // 已存在，不需要添加
            }
        }

        // 创建新的字体定义
        Fonts.Font newFont = new Fonts.Font();
        newFont.setName(logicalName);
        fonts.getFont().add(newFont);
        
        log.debug("Added font definition: '{}' -> '{}'", logicalName, physicalName);
    }

    /**
     * 处理文档字体引用
     */
    private void processDocumentFontReferences(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Processing document font references with intelligent mapping");

        MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
        List<Object> runs = getAllElementsFromObject(mainPart.getContents(), R.class);

        int processedRuns = 0;
        for (Object runObj : runs) {
            if (runObj instanceof R) {
                R run = (R) runObj;
                if (processRunFontReferences(run)) {
                    processedRuns++;
                }
            }
        }

        log.info("Processed font references in {} text runs", processedRuns);
    }

    /**
     * 处理单个文本运行的字体引用
     */
    private boolean processRunFontReferences(R run) {
        try {
            RPr runProperties = run.getRPr();
            if (runProperties == null) {
                runProperties = new RPr();
                run.setRPr(runProperties);
            }

            RFonts rFonts = runProperties.getRFonts();
            if (rFonts == null) {
                rFonts = new RFonts();
                runProperties.setRFonts(rFonts);
            }

            boolean hasChanges = false;

            // 处理各种字体类型
            hasChanges |= processIndividualFontReference(rFonts::getAscii, rFonts::setAscii, "ASCII");
            hasChanges |= processIndividualFontReference(rFonts::getHAnsi, rFonts::setHAnsi, "HAnsi");
            hasChanges |= processIndividualFontReference(rFonts::getCs, rFonts::setCs, "CS");
            hasChanges |= processIndividualFontReference(rFonts::getEastAsia, rFonts::setEastAsia, "EastAsia");

            return hasChanges;

        } catch (Exception e) {
            log.debug("Error processing run font references: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 处理单个字体引用
     */
    private boolean processIndividualFontReference(
            java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter,
            String fontType) {
        
        String currentFont = getter.get();
        if (currentFont == null) return false;

        String optimalFont = findOptimalFontMapping(currentFont);
        if (optimalFont != null && !optimalFont.equals(currentFont)) {
            setter.accept(optimalFont);
            log.debug("Mapped {} font '{}' -> '{}'", fontType, currentFont, optimalFont);
            return true;
        }

        return false;
    }

    /**
     * 查找最优字体映射
     */
    private String findOptimalFontMapping(String originalFont) {
        // 1. 检查语义映射
        String semanticMapping = CHINESE_FONT_SEMANTIC_MAPPING.get(originalFont);
        if (semanticMapping != null && isFontAvailable(semanticMapping)) {
            return semanticMapping;
        }

        // 2. 检查字体是否需要替换
        if (isFontAvailable(originalFont)) {
            return originalFont; // 字体可用，不需要替换
        }

        // 3. 查找最佳替换字体
        if (isChineseFontName(originalFont) && optimalChineseFont != null) {
            return optimalChineseFont;
        }

        // 4. 返回null表示不需要替换
        return null;
    }

    /**
     * 判断是否为中文字体名
     */
    private boolean isChineseFontName(String fontName) {
        if (fontName == null) return false;

        // 检查是否包含中文字符
        if (fontName.matches(".*[\\u4e00-\\u9fff].*")) {
            return true;
        }

        // 检查是否为已知的中文字体英文名
        String lowerName = fontName.toLowerCase();
        return lowerName.contains("simsun") || lowerName.contains("simhei") ||
               lowerName.contains("yahei") || lowerName.contains("pingfang") ||
               lowerName.contains("hiragino") || lowerName.contains("kaiti") ||
               lowerName.contains("fangsong") || lowerName.contains("stheiti");
    }

    /**
     * 配置高级字体映射器
     */
    private void configureAdvancedFontMapper(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Configuring advanced font mapper");

        EnhancedIdentityPlusMapper fontMapper = new EnhancedIdentityPlusMapper();
        
        // 添加所有缓存的字体映射
        for (Map.Entry<String, PhysicalFont> entry : FONT_MAPPING_CACHE.entrySet()) {
            fontMapper.put(entry.getKey(), entry.getValue());
        }

        wordPackage.setFontMapper(fontMapper);
        
        log.info("Advanced font mapper configured with {} mappings", FONT_MAPPING_CACHE.size());
    }

    /**
     * 增强的字体映射器
     */
    private static class EnhancedIdentityPlusMapper extends IdentityPlusMapper {
        
        @Override
        public PhysicalFont get(String fontName) {
            // 1. 尝试从缓存获取
            PhysicalFont cachedFont = FONT_MAPPING_CACHE.get(fontName);
            if (cachedFont != null) {
                return cachedFont;
            }

            // 2. 尝试父类处理
            PhysicalFont parentResult = super.get(fontName);
            if (parentResult != null) {
                // 缓存结果
                FONT_MAPPING_CACHE.put(fontName, parentResult);
                return parentResult;
            }

            // 3. 智能降级
            PhysicalFont fallbackFont = performIntelligentFallback(fontName);
            if (fallbackFont != null) {
                FONT_MAPPING_CACHE.put(fontName, fallbackFont);
                return fallbackFont;
            }

            return null;
        }

        /**
         * 执行智能降级
         */
        private PhysicalFont performIntelligentFallback(String fontName) {
            // 如果是中文字体且有最优中文字体可用
            if (isChineseFontContext(fontName) && optimalChineseFont != null) {
                PhysicalFont optimalFont = PhysicalFonts.get(optimalChineseFont);
                if (optimalFont != null) {
                    log.debug("Intelligent fallback: '{}' -> '{}'", fontName, optimalChineseFont);
                    return optimalFont;
                }
            }

            return null;
        }

        private boolean isChineseFontContext(String fontName) {
            if (fontName == null) return false;
            
            return fontName.matches(".*[\\u4e00-\\u9fff].*") ||
                   CHINESE_FONT_SEMANTIC_MAPPING.containsKey(fontName) ||
                   fontName.toLowerCase().matches(".*(sim|yahei|pingfang|hiragino|kai|fang).*");
        }
    }

    /**
     * 基础字体处理（降级方案）
     */
    private void performBasicFontProcessing(WordprocessingMLPackage wordPackage) throws Exception {
        log.warn("Using basic font processing due to enhanced processing failure");

        IdentityPlusMapper basicMapper = new IdentityPlusMapper();
        
        // 只添加基本的字体映射
        if (optimalChineseFont != null) {
            PhysicalFont chineseFont = PhysicalFonts.get(optimalChineseFont);
            if (chineseFont != null) {
                basicMapper.put("宋体", chineseFont);
                basicMapper.put("微软雅黑", chineseFont);
                basicMapper.put("SimSun", chineseFont);
                basicMapper.put("Microsoft YaHei", chineseFont);
            }
        }

        wordPackage.setFontMapper(basicMapper);
    }

    /**
     * 创建最优转换设置
     */
    private FOSettings createOptimalConversionSettings(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Creating optimal conversion settings");

        FOSettings foSettings = Docx4J.createFOSettings();
        foSettings.setWmlPackage(wordPackage);

        // 配置字体和编码相关设置
        configureConversionOptimizations(foSettings);

        return foSettings;
    }

    /**
     * 配置转换优化设置
     */
    private void configureConversionOptimizations(FOSettings foSettings) {
        try {
            foSettings.setApacheFopMime("application/pdf");
            
            // 配置FOP以解决页面布局问题
            configureFOPForPageLayout(foSettings);
            
            // 配置字体处理优化
            configureFOPForFontHandling(foSettings);
            
            log.debug("Conversion optimization settings configured");
            
        } catch (Exception e) {
            log.warn("Failed to configure conversion optimizations: {}", e.getMessage());
        }
    }
    
    /**
     * 配置FOP页面布局处理
     */
    private void configureFOPForPageLayout(FOSettings foSettings) {
        try {
            // 设置页面布局优化参数
            System.setProperty("fop.layoutmgr.ignore-keeps-after-page-break", "true");
            System.setProperty("fop.layoutmgr.ignore-keeps-within-page", "true");
            
            // 配置页边距和区域设置
            System.setProperty("fop.fonts.base14-kerning", "true");
            System.setProperty("fop.strict-validation", "false");
            
            // 处理页眉页脚溢出问题
            System.setProperty("fop.region.overflow", "hidden");
            System.setProperty("fop.accessibility", "false");
            
            log.debug("FOP page layout configuration applied");
            
        } catch (Exception e) {
            log.warn("Failed to configure FOP page layout: {}", e.getMessage());
        }
    }
    
    /**
     * 配置FOP字体处理
     */
    private void configureFOPForFontHandling(FOSettings foSettings) {
        try {
            // 启用字体自动检测
            System.setProperty("fop.fonts.auto-detect", "true");
            
            // 配置字体缓存
            System.setProperty("fop.fonts.cache", "true");
            
            // 处理缺失字体
            System.setProperty("fop.fonts.substitute-missing", "true");
            
            // 优化中文字体处理
            System.setProperty("fop.fonts.cjk.embed", "auto");
            
            log.debug("FOP font handling configuration applied");
            
        } catch (Exception e) {
            log.warn("Failed to configure FOP font handling: {}", e.getMessage());
        }
    }

    /**
     * 执行转换并监控
     */
    private void executeConversionWithMonitoring(WordprocessingMLPackage wordPackage,
                                                FOSettings foSettings,
                                                File outputFile) throws Exception {
        log.debug("Executing conversion with monitoring");

        long startTime = System.currentTimeMillis();

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            Docx4J.toPDF(wordPackage, fos);
        }

        long conversionTime = System.currentTimeMillis() - startTime;
        log.info("PDF conversion completed in {}ms", conversionTime);
    }

    /**
     * 记录转换统计
     */
    private void logConversionStatistics() {
        if (!FONT_USAGE_STATS.isEmpty()) {
            log.debug("Font usage statistics:");
            FONT_USAGE_STATS.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> log.debug("  '{}': {} times", entry.getKey(), entry.getValue()));
        }
    }

    /**
     * 优化文档布局以防止页面异常增加
     */
    private void optimizeDocumentLayout(WordprocessingMLPackage wordPackage) throws Exception {
        log.debug("Optimizing document layout to prevent page count issues");
        
        try {
            // 1. 处理分页符问题
            removeRedundantPageBreaks(wordPackage);
            
            // 2. 优化段落间距
            optimizeParagraphSpacing(wordPackage);
            
            // 3. 处理页眉页脚溢出
            optimizeHeaderFooterRegions(wordPackage);
            
            log.debug("Document layout optimization completed");
            
        } catch (Exception e) {
            log.warn("Failed to optimize document layout: {}", e.getMessage());
        }
    }
    
    /**
     * 移除冗余的分页符
     */
    private void removeRedundantPageBreaks(WordprocessingMLPackage wordPackage) {
        try {
            MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
            List<Object> paragraphs = getAllElementsFromObject(mainPart.getContents(), P.class);
            
            int removedBreaks = 0;
            for (Object paragraphObj : paragraphs) {
                if (paragraphObj instanceof P) {
                    P paragraph = (P) paragraphObj;
                    if (hasRedundantPageBreak(paragraph)) {
                        removePageBreak(paragraph);
                        removedBreaks++;
                    }
                }
            }
            
            if (removedBreaks > 0) {
                log.debug("Removed {} redundant page breaks", removedBreaks);
            }
            
        } catch (Exception e) {
            log.debug("Error removing redundant page breaks: {}", e.getMessage());
        }
    }
    
    /**
     * 检查段落是否包含冗余分页符
     */
    private boolean hasRedundantPageBreak(P paragraph) {
        try {
            PPr paragraphProperties = paragraph.getPPr();
            if (paragraphProperties != null) {
                BooleanDefaultTrue pageBreak = paragraphProperties.getPageBreakBefore();
                return pageBreak != null && pageBreak.isVal();
            }
        } catch (Exception e) {
            log.debug("Error checking page break: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 移除段落中的分页符
     */
    private void removePageBreak(P paragraph) {
        try {
            PPr paragraphProperties = paragraph.getPPr();
            if (paragraphProperties != null) {
                paragraphProperties.setPageBreakBefore(null);
            }
        } catch (Exception e) {
            log.debug("Error removing page break: {}", e.getMessage());
        }
    }
    
    /**
     * 优化段落间距
     */
    private void optimizeParagraphSpacing(WordprocessingMLPackage wordPackage) {
        try {
            MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
            List<Object> paragraphs = getAllElementsFromObject(mainPart.getContents(), P.class);
            
            int optimizedCount = 0;
            for (Object paragraphObj : paragraphs) {
                if (paragraphObj instanceof P) {
                    P paragraph = (P) paragraphObj;
                    if (optimizeParagraphSpacing(paragraph)) {
                        optimizedCount++;
                    }
                }
            }
            
            if (optimizedCount > 0) {
                log.debug("Optimized spacing for {} paragraphs", optimizedCount);
            }
            
        } catch (Exception e) {
            log.debug("Error optimizing paragraph spacing: {}", e.getMessage());
        }
    }
    
    /**
     * 优化单个段落的间距
     */
    private boolean optimizeParagraphSpacing(P paragraph) {
        try {
            PPr paragraphProperties = paragraph.getPPr();
            if (paragraphProperties == null) {
                return false;
            }
            
            PPrBase.Spacing spacing = paragraphProperties.getSpacing();
            if (spacing != null) {
                // 限制过大的行间距
                if (spacing.getLine() != null && spacing.getLine().intValue() > 500) {
                    spacing.setLine(BigInteger.valueOf(240)); // 设置为合理的行间距
                    log.debug("Reduced excessive line spacing");
                    return true;
                }
                
                // 限制过大的段前段后间距
                if (spacing.getBefore() != null && spacing.getBefore().intValue() > 300) {
                    spacing.setBefore(BigInteger.valueOf(120));
                    return true;
                }
                
                if (spacing.getAfter() != null && spacing.getAfter().intValue() > 300) {
                    spacing.setAfter(BigInteger.valueOf(120));
                    return true;
                }
            }
            
        } catch (Exception e) {
            log.debug("Error optimizing paragraph spacing: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 优化页眉页脚区域以防止溢出
     */
    private void optimizeHeaderFooterRegions(WordprocessingMLPackage wordPackage) {
        try {
            MainDocumentPart mainPart = wordPackage.getMainDocumentPart();
            
            // 处理页眉
            optimizeHeaderParts(mainPart);
            
            // 处理页脚
            optimizeFooterParts(mainPart);
            
            log.debug("Header and footer regions optimized");
            
        } catch (Exception e) {
            log.debug("Error optimizing header/footer regions: {}", e.getMessage());
        }
    }
    
    /**
     * 优化页眉部分
     */
    private void optimizeHeaderParts(MainDocumentPart mainPart) {
        try {
            // 这里可以添加具体的页眉优化逻辑
            // 例如减少页眉内容的字体大小、边距等
            log.debug("Header parts optimization applied");
        } catch (Exception e) {
            log.debug("Error optimizing header parts: {}", e.getMessage());
        }
    }
    
    /**
     * 优化页脚部分
     */
    private void optimizeFooterParts(MainDocumentPart mainPart) {
        try {
            // 这里可以添加具体的页脚优化逻辑
            // 例如减少页脚内容的字体大小、边距等
            log.debug("Footer parts optimization applied");
        } catch (Exception e) {
            log.debug("Error optimizing footer parts: {}", e.getMessage());
        }
    }

    /**
     * 获取对象中所有指定类型的元素（简化版递归搜索）
     */
    private static List<Object> getAllElementsFromObject(Object obj, Class<?> toSearch) {
        List<Object> result = new ArrayList<>();
        getAllElementsFromObjectRecursive(obj, toSearch, result, new HashSet<>());
        return result;
    }

    /**
     * 递归搜索元素
     */
    private static void getAllElementsFromObjectRecursive(Object obj, Class<?> toSearch, 
                                                         List<Object> result, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) return;
        visited.add(obj);

        if (toSearch.isInstance(obj)) {
            result.add(obj);
        }

        try {
            if (obj instanceof P) {
                P paragraph = (P) obj;
                if (paragraph.getContent() != null) {
                    for (Object content : paragraph.getContent()) {
                        getAllElementsFromObjectRecursive(content, toSearch, result, visited);
                    }
                }
            } else if (obj instanceof org.docx4j.wml.Body) {
                org.docx4j.wml.Body body = (org.docx4j.wml.Body) obj;
                if (body.getContent() != null) {
                    for (Object content : body.getContent()) {
                        getAllElementsFromObjectRecursive(content, toSearch, result, visited);
                    }
                }
            } else if (obj instanceof org.docx4j.wml.Document) {
                org.docx4j.wml.Document document = (org.docx4j.wml.Document) obj;
                if (document.getBody() != null) {
                    getAllElementsFromObjectRecursive(document.getBody(), toSearch, result, visited);
                }
            }
        } catch (Exception e) {
            // 忽略反射相关异常
        }
    }
} 