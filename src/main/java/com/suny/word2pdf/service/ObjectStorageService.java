package com.suny.word2pdf.service;

import java.io.File;
import java.io.InputStream;

/**
 * 对象存储服务接口
 * 
 * 支持文件上传和下载链接生成
 * 
 * @author suny
 */
public interface ObjectStorageService {
    
    /**
     * 上传文件到对象存储
     * 
     * @param file 要上传的文件
     * @param objectKey 对象存储中的文件键名
     * @return 上传是否成功
     */
    boolean uploadFile(File file, String objectKey);
    
    /**
     * 上传输入流到对象存储
     * 
     * @param inputStream 文件输入流
     * @param objectKey 对象存储中的文件键名
     * @param contentLength 文件内容长度
     * @return 上传是否成功
     */
    boolean uploadFile(InputStream inputStream, String objectKey, long contentLength);
    
    /**
     * 获取文件的下载链接
     * 
     * @param objectKey 对象存储中的文件键名
     * @return 下载链接URL
     */
    String getDownloadUrl(String objectKey);
    
    /**
     * 获取带有效期的文件下载链接
     * 
     * @param objectKey 对象存储中的文件键名
     * @param expirationMinutes 链接有效期（分钟）
     * @return 预签名下载链接URL
     */
    String getPreSignedDownloadUrl(String objectKey, int expirationMinutes);
    
    /**
     * 删除文件
     * 
     * @param objectKey 对象存储中的文件键名
     * @return 删除是否成功
     */
    boolean deleteFile(String objectKey);
    

    /**
     * 获取存储桶名称
     * 
     * @return 存储桶名称
     */
    String getBucketName();
} 