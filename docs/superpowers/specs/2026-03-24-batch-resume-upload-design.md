# 批量简历上传功能设计

## 1. 概述

允许用户在简历列表页一次性选择并上传多份简历，每份简历独立走现有流程（存储→文本提取→事件发布→向量化），最终返回每份的上传结果。

## 2. 用户场景

用户在简历列表页点击"批量上传"按钮，弹出批量上传弹窗，可一次选择多份本地文件上传。系统分别处理每份简历，成功者入库并触发异步向量化，失败者单独展示错误信息，用户可针对失败的简历重试。

## 3. API 改动

### 3.1 后端接口

**Endpoint**: `POST /api/resumes/upload`

**变更**: 参数从 `MultipartFile file` 改为 `MultipartFile[] files`

**Request**: `multipart/form-data`，字段名 `files`（数组）

**Response** (`BatchUploadResponse`):
```json
{
  "total": 5,
  "succeeded": 4,
  "failed": 1,
  "results": [
    {
      "fileName": "resume1.pdf",
      "status": "UPLOADED",
      "resumeId": 101
    },
    {
      "fileName": "resume2.pdf",
      "status": "FAILED",
      "error": "不支持的文件类型"
    }
  ]
}
```

**状态码**:
- `200`: 所有文件处理完成（部分成功也返回200）
- `400`: 请求格式错误（如无文件）

### 3.2 前端 API

新增 `uploadResumes(files: File[]): Promise<BatchUploadResponse>`，调用同一 endpoint。

## 4. 后端实现

### 4.1 ResumeController

```java
@PostMapping("/upload")
public ResponseEntity<BatchUploadResponse> upload(
    @RequestParam("files") MultipartFile[] files,
    @RequestParam(value = "source", defaultValue = "MANUAL") ResumeSource source)
```

遍历 `files`，对每份独立调用 `resumeService.uploadSingle(file, source)`，捕获异常并记录。

### 4.2 ResumeService

新增 `uploadSingle(MultipartFile file, ResumeSource source)` 方法（提取自原有 `upload`），每份简历独立处理：
1. 校验文件（类型、大小）
2. 存储文件
3. 提取文本
4. 发布 `ResumeUploadedEvent`
5. 返回结果或捕获异常

原有 `upload()` 方法改为调用 `uploadSingle` 循环处理数组。

### 4.3 批量限制

- 单文件大小: ≤ 10MB（保持不变）
- 批量总文件数: ≤ 100份
- 批量总大小: ≤ 200MB

## 5. 前端实现

### 5.1 ResumeListPage 改动

工具栏新增"批量上传"按钮，点击打开 `BatchUploadModal`。

### 5.2 BatchUploadModal

- 拖拽选择文件区（`multiple={true}`）
- 文件列表展示（文件名、大小、状态）
- 可移除单个文件
- 确认上传按钮
- 上传进度和结果展示

### 5.3 状态映射

| 后端状态 | 前端显示 |
|---------|---------|
| UPLOADED | 上传成功 |
| TEXT_EXTRACTED | 文本已提取 |
| AI_PROCESSED | AI处理完成 |
| FAILED | 上传失败 |

## 6. 错误处理

每份简历独立处理，失败不影响其他：
- 文件类型不支持 → 标记 FAILED
- 文件过大 → 标记 FAILED
- 存储失败 → 标记 FAILED
- 向量化失败 → 简历入库，状态保留 UPLOADED（非致命）

## 7. 向后兼容

- 现有单文件上传 `POST /api/resumes/upload`（单文件）仍然正常工作
- 现有 `ResumeUploadPage` 保持不变

## 8. 测试计划

### 8.1 单元测试

- `ResumeService.uploadSingle()`: 正常上传、文件类型校验、文件大小校验
- `ResumeService.uploadBatch()`: 部分成功部分失败、全部成功、全部失败

### 8.2 集成测试

- `POST /api/resumes/upload` (多文件): 验证多文件流转、响应结构
- 文件类型校验: 部分文件类型错误时其他正常处理
- 文件大小超限: 超过10MB的文件正确拒绝

### 8.3 前端测试

- 批量选择多个文件 → 弹窗显示文件列表
- 移除单个文件 → 列表更新
- 上传 → 结果展示（成功/失败）
- 刷新列表 → 新简历出现
