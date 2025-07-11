package com.suny.word2pdf.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDF质量验证服务
 * 
 * 功能：
 * 1. 验证页面数量一致性
 * 2. 检查文本内容保持度
 * 3. 验证中文字符正确性
 * 4. 检查文档结构完整性
 * 5. 评估转换质量分数
 * 
 * @author suny
 */
@Slf4j
@Service
public class PdfQualityValidator {
    
    // 中文字符正则模式
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4e00-\u9fff]+");
    
    // 质量阈值
    private static final double MIN_TEXT_SIMILARITY = 0.85; // 85%文本相似度
    private static final double MIN_CHINESE_ACCURACY = 0.95; // 95%中文准确度
    
    /**
     * 验证PDF质量
     * 
     * @param originalWordFile 原始Word文件
     * @param generatedPdfFile 生成的PDF文件
     * @param converterName 转换器名称
     * @return 质量验证结果
     */
    public QualityValidationResult validatePdfQuality(File originalWordFile, 
                                                     File generatedPdfFile, 
                                                     String converterName) {
        log.info("开始验证PDF质量: converter={}, pdf={}", converterName, generatedPdfFile.getName());
        
        QualityValidationResult result = new QualityValidationResult(converterName);
        
        try {
            // 1. 提取原始Word文档信息
            WordDocumentInfo wordInfo = extractWordDocumentInfo(originalWordFile);
            
            // 2. 提取PDF文档信息
            PdfDocumentInfo pdfInfo = extractPdfDocumentInfo(generatedPdfFile);
            
            // 3. 执行各项质量检查
            validatePageCount(wordInfo, pdfInfo, result);
            validateTextContent(wordInfo, pdfInfo, result);
            validateChineseCharacters(wordInfo, pdfInfo, result);
            validateDocumentStructure(wordInfo, pdfInfo, result);
            
            // 4. 计算总体质量分数
            calculateOverallQualityScore(result);
            
            log.info("PDF质量验证完成: converter={}, score={}", converterName, result.getOverallScore());
            
        } catch (Exception e) {
            log.error("PDF质量验证失败: converter={}", converterName, e);
            result.setValidationError("质量验证过程中发生错误: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 提取Word文档信息
     */
    private WordDocumentInfo extractWordDocumentInfo(File wordFile) throws IOException {
        log.debug("提取Word文档信息: {}", wordFile.getName());
        
        WordDocumentInfo info = new WordDocumentInfo();
        
        try (FileInputStream fis = new FileInputStream(wordFile);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            // 提取文本内容
            StringBuilder textContent = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    textContent.append(text).append("\n");
                }
            }
            
            info.setTextContent(textContent.toString());
            info.setEstimatedPageCount(estimateWordPageCount(textContent.toString()));
            info.setChineseCharacterCount(countChineseCharacters(textContent.toString()));
            info.setTotalCharacterCount(textContent.length());
            
            log.debug("Word文档信息: pages={}, chars={}, chinese={}", 
                    info.getEstimatedPageCount(), 
                    info.getTotalCharacterCount(), 
                    info.getChineseCharacterCount());
        }
        
        return info;
    }
    
    /**
     * 提取PDF文档信息
     */
    private PdfDocumentInfo extractPdfDocumentInfo(File pdfFile) throws IOException {
        log.debug("提取PDF文档信息: {}", pdfFile.getName());
        
        PdfDocumentInfo info = new PdfDocumentInfo();
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            // 获取页面数量
            PDPageTree pages = document.getPages();
            info.setPageCount(pages.getCount());
            
            // 提取文本内容
            PDFTextStripper textStripper = new PDFTextStripper();
            String textContent = textStripper.getText(document);
            
            info.setTextContent(textContent);
            info.setChineseCharacterCount(countChineseCharacters(textContent));
            info.setTotalCharacterCount(textContent.length());
            
            log.debug("PDF文档信息: pages={}, chars={}, chinese={}", 
                    info.getPageCount(), 
                    info.getTotalCharacterCount(), 
                    info.getChineseCharacterCount());
        }
        
        return info;
    }
    
    /**
     * 验证页面数量
     */
    private void validatePageCount(WordDocumentInfo wordInfo, 
                                  PdfDocumentInfo pdfInfo, 
                                  QualityValidationResult result) {
        log.debug("验证页面数量: Word估计={}, PDF实际={}", 
                wordInfo.getEstimatedPageCount(), pdfInfo.getPageCount());
        
        int estimatedPages = wordInfo.getEstimatedPageCount();
        int actualPages = pdfInfo.getPageCount();
        
        // 允许±1页的误差
        boolean pageCountMatch = Math.abs(estimatedPages - actualPages) <= 1;
        
        result.setExpectedPageCount(estimatedPages);
        result.setActualPageCount(actualPages);
        result.setPageCountAccurate(pageCountMatch);
        
        if (!pageCountMatch) {
            result.addIssue("页面数量不匹配: 预期" + estimatedPages + "页, 实际" + actualPages + "页");
        }
    }
    
    /**
     * 验证文本内容
     */
    private void validateTextContent(WordDocumentInfo wordInfo, 
                                   PdfDocumentInfo pdfInfo, 
                                   QualityValidationResult result) {
        log.debug("验证文本内容一致性");
        
        String originalText = normalizeText(wordInfo.getTextContent());
        String pdfText = normalizeText(pdfInfo.getTextContent());
        
        double similarity = calculateTextSimilarity(originalText, pdfText);
        
        result.setTextSimilarity(similarity);
        result.setTextContentAccurate(similarity >= MIN_TEXT_SIMILARITY);
        
        if (similarity < MIN_TEXT_SIMILARITY) {
            result.addIssue(String.format("文本内容相似度过低: %.2f%% (最低要求: %.2f%%)", 
                    similarity * 100, MIN_TEXT_SIMILARITY * 100));
        }
    }
    
    /**
     * 验证中文字符
     */
    private void validateChineseCharacters(WordDocumentInfo wordInfo, 
                                         PdfDocumentInfo pdfInfo, 
                                         QualityValidationResult result) {
        log.debug("验证中文字符准确性");
        
        int originalChineseCount = wordInfo.getChineseCharacterCount();
        int pdfChineseCount = pdfInfo.getChineseCharacterCount();
        
        double chineseAccuracy = originalChineseCount > 0 ? 
                (double) pdfChineseCount / originalChineseCount : 1.0;
        
        result.setOriginalChineseCount(originalChineseCount);
        result.setPdfChineseCount(pdfChineseCount);
        result.setChineseCharacterAccuracy(chineseAccuracy);
        result.setChineseCharactersAccurate(chineseAccuracy >= MIN_CHINESE_ACCURACY);
        
        if (chineseAccuracy < MIN_CHINESE_ACCURACY) {
            result.addIssue(String.format("中文字符准确度过低: %.2f%% (最低要求: %.2f%%)", 
                    chineseAccuracy * 100, MIN_CHINESE_ACCURACY * 100));
        }
    }
    
    /**
     * 验证文档结构
     */
    private void validateDocumentStructure(WordDocumentInfo wordInfo, 
                                         PdfDocumentInfo pdfInfo, 
                                         QualityValidationResult result) {
        log.debug("验证文档结构完整性");
        
        // 检查是否有内容丢失
        boolean hasContent = !pdfInfo.getTextContent().trim().isEmpty();
        
        result.setStructureIntact(hasContent);
        
        if (!hasContent) {
            result.addIssue("PDF文档内容为空或几乎为空");
        }
    }
    
    /**
     * 计算总体质量分数
     */
    private void calculateOverallQualityScore(QualityValidationResult result) {
        log.debug("计算总体质量分数");
        
        double score = 0.0;
        
        // 页面数量权重: 20%
        if (result.isPageCountAccurate()) {
            score += 0.2;
        }
        
        // 文本内容权重: 40%
        score += 0.4 * result.getTextSimilarity();
        
        // 中文字符权重: 30%
        score += 0.3 * result.getChineseCharacterAccuracy();
        
        // 文档结构权重: 10%
        if (result.isStructureIntact()) {
            score += 0.1;
        }
        
        result.setOverallScore(score);
        
        // 设置质量等级
        if (score >= 0.9) {
            result.setQualityLevel("优秀");
        } else if (score >= 0.8) {
            result.setQualityLevel("良好");
        } else if (score >= 0.7) {
            result.setQualityLevel("中等");
        } else {
            result.setQualityLevel("较差");
        }
    }
    
    /**
     * 估算Word文档页数
     */
    private int estimateWordPageCount(String textContent) {
        // 基于字符数估算页数（约500字符/页）
        int charCount = textContent.length();
        return Math.max(1, (charCount + 499) / 500);
    }
    
    /**
     * 统计中文字符数量
     */
    private int countChineseCharacters(String text) {
        if (text == null) return 0;
        
        int count = 0;
        for (char c : text.toCharArray()) {
            if (CHINESE_PATTERN.matcher(String.valueOf(c)).matches()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 标准化文本（去除多余空白和特殊字符）
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        
        return text.replaceAll("\\s+", " ")
                  .replaceAll("[\\r\\n\\t]", " ")
                  .trim()
                  .toLowerCase();
    }
    
    /**
     * 计算文本相似度（简单的基于字符匹配的算法）
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        if (text1.equals(text2)) return 1.0;
        
        int maxLen = Math.max(text1.length(), text2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(text1, text2);
        return 1.0 - (double) distance / maxLen;
    }
    
    /**
     * 计算Levenshtein距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        
        return dp[len1][len2];
    }
    
    /**
     * Word文档信息
     */
    public static class WordDocumentInfo {
        private String textContent;
        private int estimatedPageCount;
        private int chineseCharacterCount;
        private int totalCharacterCount;
        
        // Getters and Setters
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        
        public int getEstimatedPageCount() { return estimatedPageCount; }
        public void setEstimatedPageCount(int estimatedPageCount) { this.estimatedPageCount = estimatedPageCount; }
        
        public int getChineseCharacterCount() { return chineseCharacterCount; }
        public void setChineseCharacterCount(int chineseCharacterCount) { this.chineseCharacterCount = chineseCharacterCount; }
        
        public int getTotalCharacterCount() { return totalCharacterCount; }
        public void setTotalCharacterCount(int totalCharacterCount) { this.totalCharacterCount = totalCharacterCount; }
    }
    
    /**
     * PDF文档信息
     */
    public static class PdfDocumentInfo {
        private String textContent;
        private int pageCount;
        private int chineseCharacterCount;
        private int totalCharacterCount;
        
        // Getters and Setters
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        
        public int getPageCount() { return pageCount; }
        public void setPageCount(int pageCount) { this.pageCount = pageCount; }
        
        public int getChineseCharacterCount() { return chineseCharacterCount; }
        public void setChineseCharacterCount(int chineseCharacterCount) { this.chineseCharacterCount = chineseCharacterCount; }
        
        public int getTotalCharacterCount() { return totalCharacterCount; }
        public void setTotalCharacterCount(int totalCharacterCount) { this.totalCharacterCount = totalCharacterCount; }
    }
    
    /**
     * 质量验证结果
     */
    public static class QualityValidationResult {
        private String converterName;
        private double overallScore;
        private String qualityLevel;
        
        // 页面数量
        private int expectedPageCount;
        private int actualPageCount;
        private boolean pageCountAccurate;
        
        // 文本内容
        private double textSimilarity;
        private boolean textContentAccurate;
        
        // 中文字符
        private int originalChineseCount;
        private int pdfChineseCount;
        private double chineseCharacterAccuracy;
        private boolean chineseCharactersAccurate;
        
        // 文档结构
        private boolean structureIntact;
        
        // 问题列表
        private List<String> issues;
        private String validationError;
        
        public QualityValidationResult(String converterName) {
            this.converterName = converterName;
            this.issues = new ArrayList<>();
        }
        
        public void addIssue(String issue) {
            this.issues.add(issue);
        }
        
        // Getters and Setters
        public String getConverterName() { return converterName; }
        public void setConverterName(String converterName) { this.converterName = converterName; }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public String getQualityLevel() { return qualityLevel; }
        public void setQualityLevel(String qualityLevel) { this.qualityLevel = qualityLevel; }
        
        public int getExpectedPageCount() { return expectedPageCount; }
        public void setExpectedPageCount(int expectedPageCount) { this.expectedPageCount = expectedPageCount; }
        
        public int getActualPageCount() { return actualPageCount; }
        public void setActualPageCount(int actualPageCount) { this.actualPageCount = actualPageCount; }
        
        public boolean isPageCountAccurate() { return pageCountAccurate; }
        public void setPageCountAccurate(boolean pageCountAccurate) { this.pageCountAccurate = pageCountAccurate; }
        
        public double getTextSimilarity() { return textSimilarity; }
        public void setTextSimilarity(double textSimilarity) { this.textSimilarity = textSimilarity; }
        
        public boolean isTextContentAccurate() { return textContentAccurate; }
        public void setTextContentAccurate(boolean textContentAccurate) { this.textContentAccurate = textContentAccurate; }
        
        public int getOriginalChineseCount() { return originalChineseCount; }
        public void setOriginalChineseCount(int originalChineseCount) { this.originalChineseCount = originalChineseCount; }
        
        public int getPdfChineseCount() { return pdfChineseCount; }
        public void setPdfChineseCount(int pdfChineseCount) { this.pdfChineseCount = pdfChineseCount; }
        
        public double getChineseCharacterAccuracy() { return chineseCharacterAccuracy; }
        public void setChineseCharacterAccuracy(double chineseCharacterAccuracy) { this.chineseCharacterAccuracy = chineseCharacterAccuracy; }
        
        public boolean isChineseCharactersAccurate() { return chineseCharactersAccurate; }
        public void setChineseCharactersAccurate(boolean chineseCharactersAccurate) { this.chineseCharactersAccurate = chineseCharactersAccurate; }
        
        public boolean isStructureIntact() { return structureIntact; }
        public void setStructureIntact(boolean structureIntact) { this.structureIntact = structureIntact; }
        
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        
        public String getValidationError() { return validationError; }
        public void setValidationError(String validationError) { this.validationError = validationError; }
    }
} 