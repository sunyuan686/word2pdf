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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于LibreOffice的Word转PDF转换器
 *
 * @author sunyuan
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
        log.info("Starting enhanced LibreOffice conversion with maximum quality settings for file: {}", outputFile.getName());

        File tempInputFile = createTempInputFile(inputStream);

        try {
            executeHighQualityLibreOfficeConversion(tempInputFile, outputFile);
            log.info("Enhanced LibreOffice conversion completed successfully for file: {}", outputFile.getName());
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
     * 执行高质量LibreOffice转换
     * <p>
     * 使用LibreOffice 7.4+的专业级PDF导出选项确保样式和页面布局完全一致
     */
    private void executeHighQualityLibreOfficeConversion(File inputFile, File outputFile) throws Exception {
        Path outputDir = outputFile.getParentFile().toPath();

        ProcessBuilder processBuilder = createProfessionalGradeProcessBuilder(inputFile, outputDir);

        log.debug("Executing professional-grade LibreOffice command: {}", String.join(" ", processBuilder.command()));

        Process process = processBuilder.start();

        boolean finished = process.waitFor(applicationConfig.getConversionTimeout(), TimeUnit.MILLISECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("LibreOffice conversion timeout after " + applicationConfig.getConversionTimeout() + "ms");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorOutput = readProcessErrorOutput(process);
            throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode +
                    ". Error: " + errorOutput);
        }

        moveGeneratedPdfToTargetLocation(inputFile, outputFile, outputDir);
    }

    /**
     * 创建专业级进程构建器
     * <p>
     * 使用LibreOffice 7.4+的高级PDF导出选项确保最大质量和一致性
     */
    private ProcessBuilder createProfessionalGradeProcessBuilder(File inputFile, Path outputDir) {
        List<String> command = Arrays.asList(
                applicationConfig.getLibreOfficePath(),
                "--headless",
                "--invisible",
                "--nodefault",
                "--nolockcheck",
                "--nologo",
                "--norestore",
                "--convert-to", buildEnhancedPdfExportFilter(),
                "--outdir", outputDir.toString(),
                inputFile.getAbsolutePath()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        processBuilder.environment().put("SAL_USE_VCLPLUGIN", "svp");
        processBuilder.environment().put("SAL_DISABLE_OPENCL", "true");
        processBuilder.environment().put("SAL_DISABLE_HUNSPELL", "true");

        return processBuilder;
    }

    /**
     * 构建增强的PDF导出过滤器选项
     * <p>
     * 使用专业级PDF导出参数确保样式、字体、页面布局的最大一致性
     * 基于LibreOffice 7.4+的writer_pdf_Export高级选项
     */
    private String buildEnhancedPdfExportFilter() {
        return "pdf:writer_pdf_Export:{" +
                // 字体嵌入设置 - 确保字体一致性
                "\"EmbedStandardFonts\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"EmbedFonts\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"SubsetThreshold\":{\"type\":\"long\",\"value\":\"100\"}," +  // 完整嵌入所有字体

                // PDF版本和兼容性设置
                "\"UseTaggedPDF\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"SelectPdfVersion\":{\"type\":\"long\",\"value\":\"17\"}," +      // PDF 1.7 最佳兼容性

                // 压缩和质量设置 - 确保最高图像质量
                "\"CompressMode\":{\"type\":\"long\",\"value\":\"1\"}," +           // 无损压缩
                "\"JPEGQuality\":{\"type\":\"long\",\"value\":\"95\"}," +           // 95%高质量
                "\"ReduceImageResolution\":{\"type\":\"boolean\",\"value\":\"false\"}," +
                "\"MaxImageResolution\":{\"type\":\"long\",\"value\":\"300\"}," +   // 300 DPI

                // 导出内容控制
                "\"ExportBookmarks\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"ExportNotes\":{\"type\":\"boolean\",\"value\":\"false\"}," +
                "\"ConvertOOoTargetToPDFTarget\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"ExportLinksRelativeFsys\":{\"type\":\"boolean\",\"value\":\"false\"}," +

                // 辅助功能和结构
                "\"PDFUACompliance\":{\"type\":\"boolean\",\"value\":\"false\"}," +
                "\"ExportFormFields\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"FormsType\":{\"type\":\"long\",\"value\":\"0\"}," +
                "\"AllowDuplicateFieldNames\":{\"type\":\"boolean\",\"value\":\"false\"}," +

                // 页面布局保持设置 - 关键参数确保页面一致性
                "\"ExportBackgrounds\":{\"type\":\"boolean\",\"value\":\"true\"}," +
                "\"IsSkipEmptyPages\":{\"type\":\"boolean\",\"value\":\"false\"}," +
                "\"IsAddStream\":{\"type\":\"boolean\",\"value\":\"false\"}," +

                // 高级图形和字体渲染选项
                "\"SignPDF\":{\"type\":\"boolean\",\"value\":\"false\"}," +
                "\"RestrictPermissions\":{\"type\":\"boolean\",\"value\":\"false\"}" +
                "}";
    }

    /**
     * 读取进程错误输出
     */
    private String readProcessErrorOutput(Process process) {
        try {
            return new String(process.getErrorStream().readAllBytes());
        } catch (Exception e) {
            return "Unable to read error output";
        }
    }

    /**
     * 移动生成的PDF到目标位置
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