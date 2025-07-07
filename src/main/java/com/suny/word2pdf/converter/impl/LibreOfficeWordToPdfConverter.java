package com.suny.word2pdf.converter.impl;

import com.suny.word2pdf.config.ApplicationConfig;
import com.suny.word2pdf.converter.WordToPdfConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于LibreOffice的Word转PDF转换器
 * 
 * @author suny
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LibreOfficeWordToPdfConverter implements WordToPdfConverter {
    
    private final ApplicationConfig applicationConfig;
    
    @Override
    public String getConverterName() {
        return "LibreOffice";
    }
    
    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        log.debug("Starting LibreOffice conversion for file: {}", outputFile.getName());
        
        // 创建临时输入文件
        File tempInputFile = createTempInputFile(inputStream);
        
        try {
            executeLibreOfficeConversion(tempInputFile, outputFile);
            log.debug("LibreOffice conversion completed successfully for file: {}", outputFile.getName());
        } finally {
            cleanupTempFile(tempInputFile);
        }
    }
    
    @Override
    public boolean isAvailable() {
        File libreOfficeFile = new File(applicationConfig.getLibreOfficePath());
        boolean available = libreOfficeFile.exists() && libreOfficeFile.canExecute();
        
        if (!available) {
            log.warn("LibreOffice not available at path: {}", applicationConfig.getLibreOfficePath());
        }
        
        return available;
    }
    
    /**
     * 创建临时输入文件
     * 
     * @param inputStream 输入流
     * @return 临时文件
     * @throws Exception 异常
     */
    private File createTempInputFile(InputStream inputStream) throws Exception {
        String fileName = "temp_" + UUID.randomUUID() + ".docx";
        Path tempFilePath = Paths.get(applicationConfig.getTempDir(), fileName);
        File tempFile = tempFilePath.toFile();
        
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            IOUtils.copy(inputStream, outputStream);
            log.debug("Created temp input file: {}", tempFile.getAbsolutePath());
        }
        
        return tempFile;
    }
    
    /**
     * 执行LibreOffice转换
     * 
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     * @throws Exception 异常
     */
    private void executeLibreOfficeConversion(File inputFile, File outputFile) throws Exception {
        Path outputDir = outputFile.getParentFile().toPath();
        
        ProcessBuilder processBuilder = createProcessBuilder(inputFile, outputDir);
        
        log.debug("Executing LibreOffice command: {}", String.join(" ", processBuilder.command()));
        
        Process process = processBuilder.start();
        
        boolean finished = process.waitFor(applicationConfig.getConversionTimeout(), TimeUnit.MILLISECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("LibreOffice conversion timeout");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode);
        }
        
        moveGeneratedPdfToTargetLocation(inputFile, outputFile, outputDir);
    }
    
    /**
     * 创建进程构建器
     * 
     * @param inputFile 输入文件
     * @param outputDir 输出目录
     * @return 进程构建器
     */
    private ProcessBuilder createProcessBuilder(File inputFile, Path outputDir) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                applicationConfig.getLibreOfficePath(),
                "--headless",
                "--convert-to", "pdf",
                "--outdir", outputDir.toString(),
                inputFile.getAbsolutePath()
        );
        
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }
    
    /**
     * 移动生成的PDF到目标位置
     * 
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     * @param outputDir 输出目录
     * @throws Exception 异常
     */
    private void moveGeneratedPdfToTargetLocation(File inputFile, File outputFile, Path outputDir) throws Exception {
        String inputFileName = inputFile.getName();
        String baseFileName = inputFileName.substring(0, inputFileName.lastIndexOf('.'));
        Path generatedPdfPath = outputDir.resolve(baseFileName + ".pdf");
        
        if (!Files.exists(generatedPdfPath)) {
            throw new RuntimeException("Generated PDF file not found: " + generatedPdfPath);
        }
        
        Files.move(generatedPdfPath, outputFile.toPath());
        log.debug("Moved generated PDF from {} to {}", generatedPdfPath, outputFile.getAbsolutePath());
    }
    
    /**
     * 清理临时文件
     * 
     * @param tempFile 临时文件
     */
    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            boolean deleted = tempFile.delete();
            if (deleted) {
                log.debug("Cleaned up temp file: {}", tempFile.getAbsolutePath());
            } else {
                log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }
} 