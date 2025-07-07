package com.suny.word2pdf;

import com.suny.word2pdf.service.ConversionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Word2PDF应用测试类
 * 
 * @author suny
 */
@SpringBootTest
class Word2pdfApplicationTests {

    @Autowired
    private ConversionService conversionService;

    @Test
    void contextLoads() {
        // 验证Spring上下文正常加载
        assertNotNull(conversionService);
    }
    
    @Test
    void testConvertersAvailability() {
        // 测试转换器可用性
        var availableConverters = conversionService.getAvailableConverters();
        assertNotNull(availableConverters);
        // 至少应该有POI和Docx4j可用
        assertTrue(availableConverters.size() >= 2);
    }

}
