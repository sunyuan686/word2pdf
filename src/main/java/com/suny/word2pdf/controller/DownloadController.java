package com.suny.word2pdf.controller;

import com.suny.word2pdf.config.ApplicationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Paths;

/**
 * 下载控制器
 * 
 * @author suny
 */
@Slf4j
@RestController
@RequestMapping("/download")
@RequiredArgsConstructor
public class DownloadController {
    
    private final ApplicationConfig applicationConfig;
    
    /**
     * 下载PDF文件
     * 
     * @param filename 文件名
     * @return PDF文件资源
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> downloadPdf(@PathVariable String filename) {
        
        log.info("Download request for file: {}", filename);
        
        if (!filename.endsWith(".pdf")) {
            log.warn("Invalid file extension for download: {}", filename);
            return ResponseEntity.badRequest().build();
        }
        
        File file = Paths.get(applicationConfig.getTempDir(), filename).toFile();
        
        if (!file.exists()) {
            log.warn("File not found for download: {}", file.getAbsolutePath());
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(resource);
    }
} 