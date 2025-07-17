# Word2PDF API 文档

Word2PDF 转换服务提供了完整的 RESTful API，支持三种转换引擎的 Word 文档转 PDF 功能。

## 基础信息

- **基础URL**: `http://localhost:8080/api`
- **内容类型**: `application/json` 或 `multipart/form-data`
- **字符编码**: `UTF-8`
- **API版本**: `v1.0.1`

## 认证

当前版本无需认证，所有接口均为公开访问。

---

## 📋 接口列表

### 1. 获取可用转换器

获取系统中所有可用的转换器列表。

```http
GET /api/convert/converters
```

#### 响应

**成功响应 (200 OK)**
```json
["POI", "Docx4j", "LibreOffice"]
```

#### 示例

```bash
curl -X GET "http://localhost:8080/api/convert/converters"
```

---

### 2. 使用指定转换器转换文件

使用指定的转换器将 Word 文档转换为 PDF。

```http
POST /api/convert/{converter}
```

#### 路径参数

| 参数 | 类型 | 必需 | 描述 |
|------|------|------|------|
| converter | string | 是 | 转换器名称 (`POI`, `Docx4j`, `LibreOffice`) |

#### 请求体

**Content-Type**: `multipart/form-data`

| 字段 | 类型 | 必需 | 描述 |
|------|------|------|------|
| file | file | 是 | Word 文档文件 (`.docx`, `.doc`) |

#### 响应

**成功响应 (200 OK)**
```json
{
  "success": true,
  "method": "POI",
  "duration": 1250,
  "originalFileSize": 25600,
  "pdfFileSize": 18432,
  "downloadUrl": "/api/download/document_abc123.pdf",
  "errorMessage": null
}
```

**失败响应 (400 Bad Request)**
```json
{
  "success": false,
  "method": "POI",
  "duration": 0,
  "originalFileSize": 0,
  "pdfFileSize": 0,
  "downloadUrl": null,
  "errorMessage": "Unsupported file format"
}
```

#### 字段说明

| 字段 | 类型 | 描述 |
|------|------|------|
| success | boolean | 转换是否成功 |
| method | string | 使用的转换器名称 |
| duration | long | 转换耗时（毫秒） |
| originalFileSize | long | 原始文件大小（字节） |
| pdfFileSize | long | PDF文件大小（字节） |
| downloadUrl | string | PDF下载链接 |
| errorMessage | string | 错误信息（失败时） |

#### 示例

```bash
# 使用 POI 转换器
curl -X POST "http://localhost:8080/api/convert/POI" \
  -F "file=@document.docx"

# 使用 Docx4j 转换器
curl -X POST "http://localhost:8080/api/convert/Docx4j" \
  -F "file=@document.docx"

# 使用 LibreOffice 转换器
curl -X POST "http://localhost:8080/api/convert/LibreOffice" \
  -F "file=@document.docx"
```

---

### 3. 性能对比转换

使用所有可用转换器转换同一文档，并提供性能对比分析。

```http
POST /api/convert/compare
```

#### 请求体

**Content-Type**: `multipart/form-data`

| 字段 | 类型 | 必需 | 描述 |
|------|------|------|------|
| file | file | 是 | Word 文档文件 (`.docx`, `.doc`) |

#### 响应

**成功响应 (200 OK)**
```json
{
  "results": [
    {
      "success": true,
      "method": "POI",
      "duration": 1250,
      "originalFileSize": 25600,
      "pdfFileSize": 18432,
      "downloadUrl": "/api/download/document_poi_abc123.pdf",
      "errorMessage": null
    },
    {
      "success": true,
      "method": "LibreOffice", 
      "duration": 890,
      "originalFileSize": 25600,
      "pdfFileSize": 17856,
      "downloadUrl": "/api/download/document_libreoffice_def456.pdf",
      "errorMessage": null
    },
    {
      "success": false,
      "method": "Docx4j",
      "duration": 0,
      "originalFileSize": 25600,
      "pdfFileSize": 0,
      "downloadUrl": null,
      "errorMessage": "Font mapping failed"
    }
  ],
  "fastestMethod": "LibreOffice",
  "slowestMethod": "POI",
  "averageDuration": 1070.0,
  "successRate": 0.67,
  "totalConversions": 3
}
```

#### 字段说明

| 字段 | 类型 | 描述 |
|------|------|------|
| results | array | 各转换器的详细结果 |
| fastestMethod | string | 最快的转换器 |
| slowestMethod | string | 最慢的转换器 |
| averageDuration | double | 平均转换时间（毫秒） |
| successRate | double | 成功率（0.0-1.0） |
| totalConversions | int | 总转换次数 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/convert/compare" \
  -F "file=@document.docx"
```

---

### 4. 下载PDF文件

下载转换后的PDF文件。

```http
GET /api/download/{filename}
```

#### 路径参数

| 参数 | 类型 | 必需 | 描述 |
|------|------|------|------|
| filename | string | 是 | PDF文件名 |

#### 响应

**成功响应 (200 OK)**
- **Content-Type**: `application/pdf`
- **Content-Disposition**: `attachment; filename="filename.pdf"`
- **Body**: PDF文件二进制数据

**失败响应 (404 Not Found)**
```json
{
  "timestamp": "2024-12-20T10:30:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "message": "File not found: filename.pdf",
  "path": "/api/download/filename.pdf"
}
```

#### 示例

```bash
# 下载PDF文件
curl -X GET "http://localhost:8080/api/download/document_abc123.pdf" \
  -o converted.pdf

# 使用wget下载
wget "http://localhost:8080/api/download/document_abc123.pdf" \
  -O converted.pdf
```

---

### 5. 获取转换历史

获取所有转换操作的历史记录。

```http
GET /api/convert/history
```

#### 响应

**成功响应 (200 OK)**
```json
{
  "totalConversions": 15,
  "successfulConversions": 13,
  "failedConversions": 2,
  "averageDuration": 1180.5,
  "converterStats": {
    "POI": {
      "count": 5,
      "successRate": 0.8,
      "averageDuration": 1250.0
    },
    "Docx4j": {
      "count": 5,
      "successRate": 0.6,
      "averageDuration": 980.0
    },
    "LibreOffice": {
      "count": 5,
      "successRate": 1.0,
      "averageDuration": 890.0
    }
  },
  "recentConversions": [
    {
      "timestamp": "2024-12-20T10:30:00.000Z",
      "method": "LibreOffice",
      "success": true,
      "duration": 890,
      "filename": "document.docx"
    }
  ]
}
```

#### 示例

```bash
curl -X GET "http://localhost:8080/api/convert/history"
```

---

### 6. 清理转换历史

清除所有转换历史记录和临时文件。

```http
DELETE /api/convert/history
```

#### 响应

**成功响应 (200 OK)**
```json
{
  "message": "Conversion history cleared successfully",
  "clearedEntries": 15,
  "clearedFiles": 8
}
```

#### 示例

```bash
curl -X DELETE "http://localhost:8080/api/convert/history"
```

---

## 🔧 错误处理

### HTTP 状态码

| 状态码 | 描述 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 413 | 文件过大 |
| 415 | 不支持的文件类型 |
| 500 | 服务器内部错误 |

### 错误响应格式

```json
{
  "timestamp": "2024-12-20T10:30:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "详细错误描述",
  "path": "/api/convert/POI"
}
```

### 常见错误

#### 1. 文件格式不支持
```json
{
  "status": 415,
  "error": "Unsupported Media Type",
  "message": "File format not supported. Only .docx and .doc files are allowed."
}
```

#### 2. 文件过大
```json
{
  "status": 413,
  "error": "Payload Too Large", 
  "message": "File size exceeds maximum limit of 100MB."
}
```

#### 3. 转换器不可用
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Converter 'InvalidConverter' is not available."
}
```

#### 4. 转换失败
```json
{
  "success": false,
  "errorMessage": "Document structure is corrupted or contains unsupported elements."
}
```

---

## 📝 使用限制

### 文件限制
- **支持格式**: `.docx`, `.doc`
- **最大文件大小**: 100MB
- **文件编码**: 推荐 UTF-8

### 转换限制
- **超时时间**: 30秒
- **并发转换**: 系统资源限制
- **临时文件**: 自动清理

### 中文支持
- ✅ **POI**: 完全支持，自动字体配置
- ✅ **Docx4j**: 完全支持，智能字体映射
- ✅ **LibreOffice**: 原生支持，无需配置

---

## 🧪 测试示例

### JavaScript/Node.js

```javascript
const FormData = require('form-data');
const fs = require('fs');
const axios = require('axios');

async function convertDocument() {
  const form = new FormData();
  form.append('file', fs.createReadStream('document.docx'));
  
  try {
    const response = await axios.post(
      'http://localhost:8080/api/convert/POI',
      form,
      { headers: form.getHeaders() }
    );
    
    console.log('转换成功:', response.data);
    
    // 下载PDF
    if (response.data.success) {
      const pdfResponse = await axios.get(
        `http://localhost:8080${response.data.downloadUrl}`,
        { responseType: 'stream' }
      );
      
      pdfResponse.data.pipe(fs.createWriteStream('converted.pdf'));
    }
  } catch (error) {
    console.error('转换失败:', error.response.data);
  }
}
```

### Python

```python
import requests

def convert_document():
    url = 'http://localhost:8080/api/convert/POI'
    
    with open('document.docx', 'rb') as file:
        files = {'file': file}
        response = requests.post(url, files=files)
    
    if response.status_code == 200:
        result = response.json()
        print(f"转换成功: {result}")
        
        # 下载PDF
        if result['success']:
            pdf_response = requests.get(
                f"http://localhost:8080{result['downloadUrl']}"
            )
            
            with open('converted.pdf', 'wb') as pdf_file:
                pdf_file.write(pdf_response.content)
    else:
        print(f"转换失败: {response.text}")
```

### Java

```java
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class Word2PdfClient {
    public void convertDocument() {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource("document.docx"));
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = 
            new HttpEntity<>(body, headers);
        
        ResponseEntity<ConversionResult> response = restTemplate.postForEntity(
            "http://localhost:8080/api/convert/POI",
            requestEntity,
            ConversionResult.class
        );
        
        System.out.println("转换结果: " + response.getBody());
    }
}
```

---

## 📊 性能指标

### 转换器性能对比

| 转换器 | 平均速度 | 内存使用 | 中文支持 | 格式兼容性 | 推荐场景 |
|--------|----------|----------|----------|------------|----------|
| **LibreOffice** | 🚀🚀🚀 | 💾💾 | ✅ 原生 | ⭐⭐⭐⭐⭐ | 生产环境 |
| **Docx4j** | 🚀🚀 | 💾 | ✅ 优化 | ⭐⭐⭐⭐ | 复杂格式 |
| **POI** | 🚀 | 💾💾💾 | ✅ 支持 | ⭐⭐⭐ | 简单文档 |

### 基准测试结果

基于 1MB 标准测试文档的平均性能：

```json
{
  "documentSize": "1MB",
  "testRuns": 100,
  "results": {
    "LibreOffice": {
      "averageTime": "890ms",
      "successRate": "98%",
      "pdfSize": "850KB"
    },
    "Docx4j": {
      "averageTime": "1200ms", 
      "successRate": "95%",
      "pdfSize": "920KB"
    },
    "POI": {
      "averageTime": "1800ms",
      "successRate": "92%",
      "pdfSize": "980KB"
    }
  }
}
```

---

## 🔗 相关链接

- [项目源码](https://github.com/sunyuan686/word2pdf)
- [使用指南](USAGE_GUIDE.md)
- [更新日志](CHANGELOG.md)
- [问题反馈](https://github.com/sunyuan686/word2pdf/issues)
- [功能建议](https://github.com/sunyuan686/word2pdf/discussions) 