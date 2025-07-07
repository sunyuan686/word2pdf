# Word2PDF 转换服务

一个基于Spring Boot的Word转PDF服务，支持三种转换技术栈的性能对比。

## 功能特性

- **多技术栈支持**: 支持POI、Docx4j、LibreOffice三种转换方式
- **性能对比**: 提供详细的转换性能统计和对比
- **RESTful API**: 提供完整的REST API接口
- **详细日志**: 完整的转换过程日志记录
- **文件管理**: 自动的临时文件管理和清理
- **错误处理**: 完善的异常处理机制

## 技术栈

- **Java 21**: 使用最新的Java特性和语法
- **Spring Boot 3.5.3**: 现代化的Spring框架
- **Apache POI**: Microsoft Office文档处理
- **Docx4j**: OpenXML文档处理
- **LibreOffice**: 开源办公套件转换
- **Maven**: 项目构建和依赖管理

## 系统要求

- JDK 21+
- Maven 3.6+
- LibreOffice (用于LibreOffice转换器)

### MacOS LibreOffice 安装

```bash
# 使用Homebrew安装
brew install --cask libreoffice

# 或者从官网下载安装包
# https://www.libreoffice.org/download/download/
```

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd word2pdf
```

### 2. 构建项目

```bash
./mvnw clean compile
```

### 3. 运行应用

```bash
./mvnw spring-boot:run
```

应用将在 http://localhost:8080 启动。

## API 接口

### 基础URL
```
http://localhost:8080/api
```

### 1. 获取可用转换器

```bash
GET /convert/converters
```

**响应示例:**
```json
["POI", "Docx4j", "LibreOffice"]
```

### 2. 使用指定转换器转换文件

```bash
POST /convert/{converter}
Content-Type: multipart/form-data
```

**参数:**
- `converter`: 转换器名称 (POI/Docx4j/LibreOffice)
- `file`: Word文档文件 (.docx/.doc)

**响应示例:**
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

### 3. 性能对比转换

```bash
POST /convert/compare
Content-Type: multipart/form-data
```

**参数:**
- `file`: Word文档文件

**响应示例:**
```json
{
  "results": [
    {
      "success": true,
      "method": "POI",
      "duration": 1250,
      "originalFileSize": 25600,
      "pdfFileSize": 18432,
      "downloadUrl": "/api/download/document_abc123.pdf"
    },
    {
      "success": true,
      "method": "LibreOffice",
      "duration": 890,
      "originalFileSize": 25600,
      "pdfFileSize": 17856,
      "downloadUrl": "/api/download/document_def456.pdf"
    }
  ],
  "fastestMethod": "LibreOffice",
  "slowestMethod": "POI",
  "averageDuration": 1070.0,
  "successRate": 1.0,
  "totalConversions": 2
}
```

### 4. 下载PDF文件

```bash
GET /download/{filename}
```

### 5. 获取转换历史

```bash
GET /convert/history
```

### 6. 清理转换历史

```bash
DELETE /convert/history
```

## 使用示例

### curl 命令示例

```bash
# 1. 检查可用转换器
curl -X GET http://localhost:8080/api/convert/converters

# 2. 使用POI转换器转换文件
curl -X POST http://localhost:8080/api/convert/POI \
  -F "file=@/path/to/your/document.docx"

# 3. 性能对比转换
curl -X POST http://localhost:8080/api/convert/compare \
  -F "file=@/path/to/your/document.docx"

# 4. 下载转换后的PDF
curl -X GET http://localhost:8080/api/download/document_abc123.pdf \
  -o converted.pdf
```

### Java 客户端示例

```java
// 使用Spring的RestTemplate或WebClient
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource("/path/to/document.docx"));

ResponseEntity<ConversionResult> response = restTemplate.postForEntity(
    "http://localhost:8080/api/convert/POI", 
    body, 
    ConversionResult.class
);
```

## 转换器对比

| 转换器 | 优势 | 劣势 | 适用场景 | 中文支持 |
|--------|------|------|----------|----------|
| **POI** | - 纯Java实现<br>- 无外部依赖<br>- 格式支持好 | - 转换速度较慢<br>- 内存占用较高 | 简单文档转换 | ✅ 已优化字体配置 |
| **Docx4j** | - 功能强大<br>- 格式保真度高<br>- 可定制性强 | - 学习曲线陡峭<br>- 复杂文档可能出错 | 复杂格式文档 | ✅ 已配置中文字体映射 |
| **LibreOffice** | - 转换质量最高<br>- 格式支持最全<br>- 速度较快 | - 需要安装LibreOffice<br>- 系统依赖性强 | 生产环境推荐 | ✅ 原生支持中文 |

## 性能调优

### 1. JVM 参数调优

```bash
# 启动参数示例
java -Xmx2g -Xms1g -XX:+UseG1GC -jar word2pdf-0.0.1-SNAPSHOT.jar
```

### 2. 应用配置调优

```properties
# application.properties
# 调整转换超时时间
app.conversion.timeout=60000

# 调整文件上传限制
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=200MB
```

### 3. LibreOffice 优化

```properties
# LibreOffice 路径配置
app.libreoffice.path=/opt/libreoffice/program/soffice
```

## 监控和日志

### 日志配置

应用使用SLF4J + Logback进行日志记录，可通过以下方式查看详细日志：

```bash
# 查看实时日志
tail -f logs/application.log

# 查看错误日志
grep "ERROR" logs/application.log
```

### 关键日志信息

- 转换开始和结束时间
- 文件大小信息
- 转换器可用性检查
- 错误详情和堆栈跟踪
- 性能统计信息

## 中文支持

本项目已全面优化了中文文档的转换支持，解决了各转换器的中文显示问题：

### POI转换器中文支持

**问题**: 默认情况下POI转换器无法正确显示中文字符
**解决方案**: 
- 自动配置系统字体路径
- 注册常用中文字体(Arial Unicode MS, PingFang SC, Hiragino Sans GB等)
- 优化字体映射配置

### Docx4j转换器中文支持

**问题**: 中文字符显示为"#"符号
**解决方案**:
- 实现智能字体映射器(IdentityPlusMapper)
- 自动发现系统可用中文字体
- 配置多种中文字体映射:
  - 宋体 (SimSun)
  - 黑体 (SimHei) 
  - 微软雅黑 (Microsoft YaHei)
  - 苹方 (PingFang SC)
  - 冬青黑体简体中文 (Hiragino Sans GB)
  - Arial Unicode MS

### LibreOffice转换器

**优势**: 原生支持中文，无需额外配置

### 中文字体检测日志

应用启动时会自动检测可用的中文字体并记录在日志中：

```
DEBUG c.s.w.c.impl.PoiWordToPdfConverter     : Found Chinese font: /System/Library/Fonts/Arial Unicode MS.ttf
DEBUG c.s.w.c.impl.Docx4jWordToPdfConverter  : Found available Chinese font: Arial Unicode MS
DEBUG c.s.w.c.impl.Docx4jWordToPdfConverter  : Mapped font '宋体' to 'Arial Unicode MS'
```

## 故障排除

### 常见问题

1. **LibreOffice 转换失败**
   ```
   解决方案: 检查LibreOffice安装路径，确保可执行权限
   配置项: app.libreoffice.path
   ```

2. **文件上传失败**
   ```
   解决方案: 检查文件大小限制配置
   配置项: spring.servlet.multipart.max-file-size
   ```

3. **内存不足**
   ```
   解决方案: 增加JVM堆内存大小
   参数: -Xmx4g
   ```

4. **转换超时**
   ```
   解决方案: 增加转换超时时间
   配置项: app.conversion.timeout
   ```

5. **中文字符显示异常**
   ```
   POI转换器: 检查系统字体安装，确保有Arial Unicode MS或其他中文字体
   Docx4j转换器: 查看日志确认字体映射是否成功配置
   解决方案: 在macOS上安装额外的中文字体包
   ```

6. **字体未找到警告**
   ```
   日志信息: "No suitable Chinese font found"
   解决方案: 
   - macOS: brew install --cask font-microsoft-office
   - 手动下载安装Arial Unicode MS字体
   - 使用LibreOffice转换器(原生支持中文)
   ```

### 调试模式

启用DEBUG日志级别获取详细信息：

```properties
logging.level.com.suny.word2pdf=DEBUG
```

## 开发指南

### 项目结构

```
src/main/java/com/suny/word2pdf/
├── config/              # 配置类
├── controller/          # REST控制器
├── converter/           # 转换器接口和实现
│   └── impl/           # 具体转换器实现
├── dto/                # 数据传输对象
├── exception/          # 异常处理
└── service/            # 业务服务层
```

### 添加新转换器

1. 实现 `WordToPdfConverter` 接口
2. 添加 `@Component` 注解
3. 实现转换逻辑和可用性检查

```java
@Component
public class NewConverter implements WordToPdfConverter {
    @Override
    public String getConverterName() {
        return "NewConverter";
    }
    
    @Override
    public void convert(InputStream inputStream, File outputFile) throws Exception {
        // 转换实现
    }
    
    @Override
    public boolean isAvailable() {
        // 可用性检查
        return true;
    }
}
```

## 许可证

本项目采用 MIT 许可证。详情请查看 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请通过以下方式联系：

- Email: your-email@example.com
- GitHub: [your-github-username](https://github.com/your-github-username) 