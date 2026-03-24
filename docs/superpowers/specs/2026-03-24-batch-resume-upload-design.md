# 批量简历上传功能设计

## 1. 概述

允许用户在简历列表页一次性选择并上传多份简历，每份简历独立走现有流程（存储→文本提取→事件发布→向量化），最终返回每份的上传结果。

## 2. 用户场景

用户在简历列表页点击"批量上传"按钮，弹出批量上传弹窗，可一次选择多份本地文件上传。系统分别处理每份简历，成功者入库并触发异步向量化，失败者单独展示错误信息，用户可针对失败的简历重试。

## 3. API 改动

### 3.1 后端接口

**Endpoint**: `POST /api/resumes/upload`

**Request**: `multipart/form-data`

**参数**（二选一）:
- `files` (MultipartFile[]): 批量上传，数组
- `file` (MultipartFile): 兼容现有单文件上传

同时传入 `source` (ResumeSource, 默认 MANUAL) 和 `uploadedByUserId` (UUID, 必填)。

**Response** (`BatchUploadResponse`):
```json
{
  "total": 5,
  "succeeded": 4,
  "failed": 1,
  "results": [
    {
      "originalIndex": 0,
      "fileName": "resume1.pdf",
      "status": "UPLOADED",
      "resumeId": 101
    },
    {
      "originalIndex": 1,
      "fileName": "resume2.pdf",
      "status": "FAILED",
      "error": "不支持的文件类型"
    }
  ]
}
```

其中 `BatchUploadResult`:
- `originalIndex`: 原始文件顺序（从0开始），用于关联
- `fileName`: 原始文件名
- `status`: `UPLOADED` | `FAILED`
- `resumeId`: 成功时返回的简历ID
- `error`: 失败时返回的错误信息

**状态码**:
- `200`: 所有文件处理完成（部分成功也返回200）
- `400`: 无文件（`files` 为空或 null）或批量超限

### 3.2 前端 API

新增 `uploadResumes(files: File[]): Promise<BatchUploadResponse>`，调用同一 endpoint。

## 4. 后端实现

### 4.1 ResumeController

```java
@PostMapping("/upload")
public ResponseEntity<ApiResponse<BatchUploadResponse>> upload(
    @RequestParam(value = "files", required = false) MultipartFile[] files,
    @RequestParam(value = "file", required = false) MultipartFile singleFile,
    @RequestParam(value = "source", defaultValue = "MANUAL") ResumeSource source,
    @RequestParam UUID uploadedByUserId)
```

逻辑：
1. 空数组或 null → 返回 400
2. `files != null` → 批量处理（校验100份和200MB限制）
3. `files == null && singleFile != null` → 兼容单文件（调用 uploadSingle）
4. `files == null && singleFile == null` → 返回 400

### 4.2 ResumeService

新增 `uploadSingle(MultipartFile file, ResumeSource source, UUID uploadedByUserId)` 方法（提取自原有 `upload`），每份简历独立处理：
1. 校验文件（类型、**10MB大小**）
2. 存储文件
3. 提取文本
4. 发布 `ResumeUploadedEvent`
5. 返回结果或捕获异常

**事务边界**: `uploadSingle` 为独立事务，部分成功部分失败时已成功的不回滚。

### 4.3 批量限制

- 单文件大小: ≤ 10MB（`validateFile()` 内校验）
- 批量总文件数: ≤ 100份（Controller 校验，返回400）
- 批量总大小: ≤ 200MB（Controller 校验，返回400）

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

- **单文件**: 现有调用方使用 `file` 参数上传单文件，Controller 兼容处理
- 现有 `ResumeUploadPage` 保持不变（仍使用 `file` 参数）
- **批量**: 新版前端调用使用 `files` 参数（数组）

## 8. DTO 定义

新建 `BatchUploadResponse` 和 `BatchUploadResult`：
- `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResponse.java`
- `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResult.java`

## 9. 测试计划

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
