package com.suny.word2pdf.service;

import com.suny.word2pdf.config.ApplicationConfig;
import com.suny.word2pdf.converter.WordToPdfConverter;
import com.suny.word2pdf.dto.ConversionResult;
import com.suny.word2pdf.dto.PerformanceStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 转换服务
 *
 * @author suny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversionService {

    private final ApplicationConfig applicationConfig;
    private final List<WordToPdfConverter> converters;
    private final Map<String, List<ConversionResult>> conversionHistory = new ConcurrentHashMap<>();

    /**
     * 使用指定转换器转换Word文档为PDF
     *
     * @param file          Word文件
     * @param converterName 转换器名称
     * @return 转换结果
     */
    public ConversionResult convertWordToPdf(MultipartFile file, String converterName) {
        return convertWordToPdf(file, converterName, true);
    }

    /**
     * 使用指定转换器转换Word文档为PDF
     *
     * @param file                    Word文件
     * @param converterName          转换器名称
     * @param includeConverterInName 是否在文件名中包含转换器名称
     * @return 转换结果
     */
    public ConversionResult convertWordToPdf(MultipartFile file, String converterName, boolean includeConverterInName) {
        log.info("Starting conversion with {} converter for file: {}", converterName, file.getOriginalFilename());

        long startTime = System.currentTimeMillis();
        WordToPdfConverter converter = findConverter(converterName);

        if (converter == null) {
            return handleConverterNotFound(converterName, startTime);
        }

        if (!converter.isAvailable()) {
            return handleConverterNotAvailable(converterName, startTime);
        }

        try {
            return performConversion(file, converter, startTime, includeConverterInName);
        } catch (Exception e) {
            return handleConversionError(converter.getConverterName(), startTime, e);
        }
    }

    /**
     * 使用所有可用转换器转换Word文档
     *
     * @param file Word文件
     * @return 性能统计结果
     */
    public PerformanceStats convertWithAllConverters(MultipartFile file) {
        log.info("Starting conversion with all converters for file: {}", file.getOriginalFilename());

        List<ConversionResult> results = new ArrayList<>();

        for (WordToPdfConverter converter : converters) {
            if (!converter.isAvailable()) {
                continue;
            }

            try {
                byte[] fileBytes = file.getBytes();
                MultipartFile fileClone = createMultipartFileClone(file, fileBytes);
                ConversionResult result = convertWordToPdf(fileClone, converter.getConverterName());
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to convert with {} converter", converter.getConverterName(), e);
                long duration = System.currentTimeMillis();
                results.add(ConversionResult.failure(converter.getConverterName(), duration, e.getMessage()));
            }
        }

        return calculatePerformanceStats(results);
    }

    /**
     * 获取可用的转换器列表
     *
     * @return 可用转换器列表
     */
    public List<String> getAvailableConverters() {
        return converters.stream()
                .filter(WordToPdfConverter::isAvailable)
                .map(WordToPdfConverter::getConverterName)
                .toList();
    }



    /**
     * 查找指定名称的转换器
     *
     * @param converterName 转换器名称
     * @return 转换器实例
     */
    private WordToPdfConverter findConverter(String converterName) {
        return converters.stream()
                .filter(converter -> converter.getConverterName().equalsIgnoreCase(converterName))
                .findFirst()
                .orElse(null);
    }


    /**
     * 执行转换
     *
     * @param file                    文件
     * @param converter              转换器
     * @param startTime              开始时间
     * @param includeConverterInName 是否在文件名中包含转换器名称
     * @return 转换结果
     * @throws Exception 异常
     */
    private ConversionResult performConversion(MultipartFile file, WordToPdfConverter converter, long startTime, boolean includeConverterInName) throws Exception {
        String outputFileName = generateOutputFileName(file.getOriginalFilename(), includeConverterInName ? converter.getConverterName() : null);
        File outputFile = Paths.get(applicationConfig.getTempDir(), outputFileName).toFile();

        log.debug("Converting file to: {}", outputFile.getAbsolutePath());

        try (InputStream inputStream = file.getInputStream()) {
            converter.convert(inputStream, outputFile);
        }

        long duration = System.currentTimeMillis() - startTime;
        ConversionResult result = ConversionResult.success(
                converter.getConverterName(),
                duration,
                file.getSize(),
                outputFile.length(),
                outputFile.getAbsolutePath()
        );

        recordConversionResult(result);

        log.info("Conversion completed successfully with {} converter in {}ms",
                converter.getConverterName(), duration);

        return result;
    }


    /**
     * 生成输出文件名
     *
     * @param originalFileName 原文件名
     * @param converterName   转换器名称（可选）
     * @return 输出文件名
     */
    private String generateOutputFileName(String originalFileName, String converterName) {
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String fileName = baseName + "_" + UUID.randomUUID();
        
        if (converterName != null && !converterName.trim().isEmpty()) {
            fileName = baseName + "_" + converterName + "_" + UUID.randomUUID();
        }
        
        return fileName + ".pdf";
    }

    /**
     * 记录转换结果
     *
     * @param result 转换结果
     */
    private void recordConversionResult(ConversionResult result) {
        conversionHistory.computeIfAbsent(result.getMethod(), k -> new ArrayList<>()).add(result);
    }

    /**
     * 处理转换器未找到的情况
     *
     * @param converterName 转换器名称
     * @param startTime     开始时间
     * @return 转换结果
     */
    private ConversionResult handleConverterNotFound(String converterName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        String errorMessage = "Converter not found: " + converterName;
        log.error(errorMessage);
        return ConversionResult.failure(converterName, duration, errorMessage);
    }

    /**
     * 处理转换器不可用的情况
     *
     * @param converterName 转换器名称
     * @param startTime     开始时间
     * @return 转换结果
     */
    private ConversionResult handleConverterNotAvailable(String converterName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        String errorMessage = "Converter not available: " + converterName;
        log.error(errorMessage);
        return ConversionResult.failure(converterName, duration, errorMessage);
    }

    /**
     * 处理转换错误
     *
     * @param converterName 转换器名称
     * @param startTime     开始时间
     * @param e             异常
     * @return 转换结果
     */
    private ConversionResult handleConversionError(String converterName, long startTime, Exception e) {
        long duration = System.currentTimeMillis() - startTime;
        log.error("Conversion failed with {} converter", converterName, e);
        return ConversionResult.failure(converterName, duration, e.getMessage());
    }

    /**
     * 创建MultipartFile副本
     *
     * @param original  原文件
     * @param fileBytes 文件字节
     * @return 副本文件
     */
    private MultipartFile createMultipartFileClone(MultipartFile original, byte[] fileBytes) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return original.getName();
            }

            @Override
            public String getOriginalFilename() {
                return original.getOriginalFilename();
            }

            @Override
            public String getContentType() {
                return original.getContentType();
            }

            @Override
            public boolean isEmpty() {
                return fileBytes.length == 0;
            }

            @Override
            public long getSize() {
                return fileBytes.length;
            }

            @Override
            public byte[] getBytes() {
                return fileBytes;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(fileBytes);
            }

            @Override
            public void transferTo(File dest) throws IllegalStateException, java.io.IOException {
                throw new UnsupportedOperationException("transferTo not supported for clone");
            }
        };
    }

    /**
     * 计算性能统计
     *
     * @param results 转换结果列表
     * @return 性能统计
     */
    private PerformanceStats calculatePerformanceStats(List<ConversionResult> results) {
        Map<String, ConversionResult> successResults = results.stream()
                .filter(ConversionResult::getSuccess)
                .collect(Collectors.toMap(ConversionResult::getMethod, Function.identity()));

        String fastestMethod = successResults.values().stream()
                .min((r1, r2) -> Long.compare(r1.getDuration(), r2.getDuration()))
                .map(ConversionResult::getMethod)
                .orElse(null);

        String slowestMethod = successResults.values().stream()
                .max((r1, r2) -> Long.compare(r1.getDuration(), r2.getDuration()))
                .map(ConversionResult::getMethod)
                .orElse(null);

        double averageDuration = successResults.values().stream()
                .mapToLong(ConversionResult::getDuration)
                .average()
                .orElse(0.0);

        long successCount = results.stream()
                .mapToLong(r -> r.getSuccess() ? 1 : 0)
                .sum();

        double successRate = results.isEmpty() ? 0.0 : (double) successCount / results.size();

        return PerformanceStats.builder()
                .results(results)
                .fastestMethod(fastestMethod)
                .slowestMethod(slowestMethod)
                .averageDuration(averageDuration)
                .successRate(successRate)
                .totalConversions(results.size())
                .build();
    }
} 