package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.converter.WordToPdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 基于POI的Word转PDF转换器
 * 
 * @author suny
 */
@Slf4j
@Component
public class PoiWordToPdfConverter implements WordToPdfConverter {
    
    @Override
    public String getConverterName() {
        return "POI";
    }
    
    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.debug("Starting POI conversion for file: {}", outputFile.getName());
        
        try (XWPFDocument document = new XWPFDocument(inputStream);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            
            PdfOptions options = createPdfOptions();
            PdfConverter.getInstance().convert(document, outputStream, options);
            
            log.debug("POI conversion completed successfully for file: {}", outputFile.getName());
        } catch (Exception e) {
            log.error("POI conversion failed for file: {}", outputFile.getName(), e);
            throw new RuntimeException("POI conversion failed: " + e.getMessage(), e);
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
            log.warn("POI converter not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建PDF转换选项
     * 
     * @return PDF选项
     */
    private PdfOptions createPdfOptions() {
        PdfOptions options = PdfOptions.create();
        // 可以在这里配置更多PDF选项
        return options;
    }
} 