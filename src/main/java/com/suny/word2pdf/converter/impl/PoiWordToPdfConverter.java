package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 使用POI库将Word文档转换为PDF
 * 基于OpenSagres的IFontProvider实现完整的中文字体支持
 */
@Component
public class PoiWordToPdfConverter implements WordToPdfConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(PoiWordToPdfConverter.class);
    
    // 字体缓存，避免重复加载
    private static final Map<String, BaseFont> fontCache = new ConcurrentHashMap<>();
    
    // 中文字体路径列表（按优先级排序）
    private static final List<String> CHINESE_FONT_PATHS = new ArrayList<>();
    
    static {
        // macOS 字体路径
        CHINESE_FONT_PATHS.add("/System/Library/Fonts/Supplemental/Arial Unicode.ttf");
        CHINESE_FONT_PATHS.add("/System/Library/Fonts/PingFang.ttc,0"); // TTC需要指定索引
        CHINESE_FONT_PATHS.add("/System/Library/Fonts/STHeiti Light.ttc,0");
        CHINESE_FONT_PATHS.add("/System/Library/Fonts/Hiragino Sans GB.ttc,0");
        
        // Windows 字体路径
        CHINESE_FONT_PATHS.add("C:/Windows/Fonts/msyh.ttc,0"); // 微软雅黑
        CHINESE_FONT_PATHS.add("C:/Windows/Fonts/simsun.ttc,0"); // 宋体
        CHINESE_FONT_PATHS.add("C:/Windows/Fonts/simhei.ttf"); // 黑体
        CHINESE_FONT_PATHS.add("C:/Windows/Fonts/arial.ttf"); // Arial作为备选
        
        // Linux 字体路径
        CHINESE_FONT_PATHS.add("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        CHINESE_FONT_PATHS.add("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf");
        CHINESE_FONT_PATHS.add("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0");
        
        logger.info("初始化中文字体路径配置完成，共{}个候选字体", CHINESE_FONT_PATHS.size());
    }

    @Override
    public String getConverterName() {
        return "POI";
    }

    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        logger.info("开始POI转换: {}", outputFile.getName());
        
        try (XWPFDocument document = new XWPFDocument(inputStream);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            
            // 创建PDF选项并设置自定义字体提供器
            PdfOptions options = PdfOptions.create();
            
            // **关键：设置自定义字体提供器**
            ChineseFontProvider fontProvider = new ChineseFontProvider();
            options.fontProvider(fontProvider);
            
            // 转换文档
            PdfConverter.getInstance().convert(document, fos, options);
            
            logger.info("POI转换成功完成: {}", outputFile.getName());
            
        } catch (Exception e) {
            logger.error("POI转换失败: {}", outputFile.getName(), e);
            throw e;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // 尝试加载POI相关类以验证可用性
            Class.forName("org.apache.poi.xwpf.usermodel.XWPFDocument");
            Class.forName("fr.opensagres.poi.xwpf.converter.pdf.PdfConverter");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("POI converter not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 自定义中文字体提供器
     * 基于OpenSagres IFontProvider接口实现
     */
    private static class ChineseFontProvider implements IFontProvider {
        
        private BaseFont defaultChineseFont;
        
        public ChineseFontProvider() {
            // 初始化时查找并加载可用的中文字体
            this.defaultChineseFont = loadChineseFont();
            if (defaultChineseFont != null) {
                logger.info("成功加载默认中文字体");
            } else {
                logger.warn("未能加载任何中文字体，将使用系统默认字体");
            }
        }
        
        @Override
        public Font getFont(String familyName, String encoding, float size, int style, Color color) {
            try {
                BaseFont baseFont = null;
                
                // 对于中文字体族或者需要Unicode支持的情况，使用中文字体
                if (needsChineseFont(familyName) && defaultChineseFont != null) {
                    baseFont = defaultChineseFont;
                    encoding = BaseFont.IDENTITY_H; // 强制使用Unicode编码
                } else {
                    // 尝试创建系统字体
                    try {
                        baseFont = BaseFont.createFont(familyName, encoding, BaseFont.NOT_EMBEDDED);
                    } catch (Exception e) {
                        // 如果系统字体创建失败，回退到中文字体
                        if (defaultChineseFont != null) {
                            baseFont = defaultChineseFont;
                            encoding = BaseFont.IDENTITY_H;
                        } else {
                            // 最终回退到Helvetica
                            baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                        }
                    }
                }
                
                Font font = new Font(baseFont, size, style, color);
                logger.debug("创建字体: family={}, encoding={}, size={}, style={}", 
                    familyName, encoding, size, style);
                return font;
                
            } catch (Exception e) {
                logger.error("创建字体失败: family={}, encoding={}", familyName, encoding, e);
                // 返回默认字体
                try {
                    BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                    return new Font(helvetica, size, style, color);
                } catch (Exception fallbackError) {
                    logger.error("创建回退字体也失败", fallbackError);
                    return null;
                }
            }
        }
        
        /**
         * 判断是否需要中文字体
         */
        private boolean needsChineseFont(String familyName) {
            if (familyName == null) return true;
            
            String family = familyName.toLowerCase();
            return family.contains("song") || family.contains("黑体") || family.contains("宋体") || 
                   family.contains("微软雅黑") || family.contains("yahei") || family.contains("simhei") ||
                   family.contains("simsun") || family.contains("pingfang") || family.contains("苹方") ||
                   family.contains("noto") || family.contains("source han") || family.contains("思源");
        }
        
        /**
         * 加载中文字体
         */
        private BaseFont loadChineseFont() {
            for (String fontPath : CHINESE_FONT_PATHS) {
                try {
                    // 检查缓存
                    BaseFont cachedFont = fontCache.get(fontPath);
                    if (cachedFont != null) {
                        logger.debug("从缓存加载字体: {}", fontPath);
                        return cachedFont;
                    }
                    
                    // 检查字体文件是否存在
                    File fontFile = new File(fontPath.contains(",") ? fontPath.split(",")[0] : fontPath);
                    if (!fontFile.exists()) {
                        logger.debug("字体文件不存在: {}", fontPath);
                        continue;
                    }
                    
                    // 尝试创建字体
                    BaseFont baseFont;
                    if (fontPath.toLowerCase().endsWith(".ttc") || fontPath.contains(",")) {
                        // 处理TTC字体集合，需要指定索引
                        baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    } else if (fontPath.toLowerCase().endsWith(".ttf")) {
                        // 处理TTF字体
                        baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    } else {
                        logger.debug("不支持的字体格式: {}", fontPath);
                        continue;
                    }
                    
                    // 验证字体是否支持中文
                    if (validateChineseSupport(baseFont)) {
                        fontCache.put(fontPath, baseFont);
                        logger.info("成功加载中文字体: {}", fontPath);
                        return baseFont;
                    } else {
                        logger.debug("字体不支持中文字符: {}", fontPath);
                    }
                    
                } catch (Exception e) {
                    logger.debug("加载字体失败: {} - {}", fontPath, e.getMessage());
                }
            }
            
            // 如果所有字体都加载失败，尝试使用内置字体
            try {
                logger.warn("所有预设字体加载失败，尝试使用Arial Unicode MS");
                BaseFont fallbackFont = BaseFont.createFont("Arial Unicode MS", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                fontCache.put("Arial Unicode MS", fallbackFont);
                return fallbackFont;
            } catch (Exception e) {
                logger.error("加载内置Unicode字体也失败", e);
                return null;
            }
        }
        
        /**
         * 验证字体是否支持中文字符
         */
        private boolean validateChineseSupport(BaseFont baseFont) {
            try {
                // 测试常用中文字符
                String[] testChars = {"中", "文", "测", "试", "你", "好"};
                for (String testChar : testChars) {
                    int[] charCodes = testChar.codePointAt(0) < 65536 ? 
                        new int[]{testChar.codePointAt(0)} : 
                        new int[]{testChar.codePointAt(0)};
                    
                    for (int charCode : charCodes) {
                        if (!baseFont.charExists(charCode)) {
                            return false;
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                logger.debug("验证中文字符支持时出错", e);
                return false;
            }
        }
    }
} 