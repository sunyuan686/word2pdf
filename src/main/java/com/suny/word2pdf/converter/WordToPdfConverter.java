package com.suny.word2pdf.converter;

import java.io.File;
import java.io.InputStream;

/**
 * Word转PDF转换器接口
 * 
 * @author suny
 */
public interface WordToPdfConverter {
    
    /**
     * 获取转换器名称
     * 
     * @return 转换器名称
     */
    String getConverterName();
    
    /**
     * 转换Word文件为PDF
     * 
     * @param inputStream Word文件输入流
     * @param outputFile 输出PDF文件
     * @throws Exception 转换异常
     */
    void convert(InputStream inputStream, File outputFile) throws Exception;
    
    /**
     * 检查是否支持该转换器
     * 
     * @return 是否可用
     */
    boolean isAvailable();
} 