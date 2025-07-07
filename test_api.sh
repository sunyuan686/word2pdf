#!/bin/bash

# Word2PDF API 测试脚本

BASE_URL="http://localhost:8080/api"

echo "=== Word2PDF 转换服务 API 测试 ==="
echo

# 1. 检查可用转换器
echo "1. 获取可用转换器列表:"
curl -s "${BASE_URL}/convert/converters" | jq '.'
echo
echo

# 2. 检查转换历史
echo "2. 获取转换历史:"
curl -s "${BASE_URL}/convert/history" | jq '.'
echo
echo

# 3. 测试文件上传 (需要真实的docx文件)
if [ -f "sample.docx" ]; then
    echo "3. 使用POI转换器转换文档:"
    curl -s -X POST "${BASE_URL}/convert/POI" \
         -F "file=@sample.docx" | jq '.'
    echo
    echo
    
    echo "4. 使用Docx4j转换器转换文档:"
    curl -s -X POST "${BASE_URL}/convert/Docx4j" \
         -F "file=@sample.docx" | jq '.'
    echo
    echo
    
    echo "5. 使用LibreOffice转换器转换文档:"
    curl -s -X POST "${BASE_URL}/convert/LibreOffice" \
         -F "file=@sample.docx" | jq '.'
    echo
    echo
    
    echo "6. 性能对比测试:"
    curl -s -X POST "${BASE_URL}/convert/compare" \
         -F "file=@sample.docx" | jq '.'
    echo
    echo
else
    echo "3. 跳过文件转换测试 (没有找到 sample.docx 文件)"
    echo "   请创建一个sample.docx文件来测试转换功能"
    echo
fi

# 4. 再次检查转换历史
echo "7. 转换后的历史记录:"
curl -s "${BASE_URL}/convert/history" | jq '.'
echo
echo

echo "=== API 测试完成 ===" 