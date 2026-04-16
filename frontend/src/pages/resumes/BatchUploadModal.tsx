import { useState } from 'react';
import { Modal, Upload, List, Button, message } from 'antd';
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { uploadResumes, type BatchUploadResponse } from '../../api/resumes';

interface BatchUploadModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface FileItem {
  uid: string;
  file: File;
  status: 'pending' | 'uploading' | 'done' | 'error';
  error?: string;
  result?: BatchUploadResponse['results'][number];
}

export function BatchUploadModal({ open, onClose, onSuccess }: BatchUploadModalProps) {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [uploading, setUploading] = useState(false);

  const handleFileChange: UploadProps['onChange'] = (info) => {
    const newFiles: FileItem[] = info.fileList
      .filter((fl) => fl.originFileObj instanceof File)
      .map((fl) => ({
        uid: fl.uid,
        file: fl.originFileObj as File,
        status: 'pending' as const,
      }));
    setFiles((prev) => {
      const existingIds = new Set(prev.map((f) => f.uid));
      return [...prev, ...newFiles.filter((nf) => !existingIds.has(nf.uid))];
    });
  };

  const handleRemove = (uid: string) => {
    setFiles((prev) => prev.filter((f) => f.uid !== uid));
  };

  const handleUpload = async () => {
    if (files.length === 0) return;
    setUploading(true);
    setFiles((prev) => prev.map((f) => ({ ...f, status: 'uploading' as const })));

    try {
      const actualFiles = files.map((f) => f.file);
      const result = await uploadResumes(actualFiles);
      const resultMap = new Map(result.results.map((r) => [r.fileName, r]));
      setFiles((prev) =>
        prev.map((f) => {
          const r = resultMap.get(f.file.name);
          if (!r) return { ...f, status: 'error' as const, error: 'No result returned' };
          const succeeded = r.status === 'UPLOADED' || r.status === 'TEXT_EXTRACTED';
          return {
            ...f,
            status: succeeded ? ('done' as const) : ('error' as const),
            error: r.error ?? undefined,
            result: r,
          };
        })
      );
      message.success(`Uploaded ${result.succeeded}/${result.total} resumes`);
      if (result.succeeded > 0) onSuccess();
    } catch (err) {
      message.error('Upload failed: ' + (err as Error).message);
      setFiles((prev) => prev.map((f) => ({ ...f, status: 'error' as const, error: String(err) })));
    } finally {
      setUploading(false);
    }
  };

  const handleClose = () => {
    if (!uploading) {
      setFiles([]);
      onClose();
    }
  };

  return (
    <Modal
      title="Batch Upload Resumes"
      open={open}
      onCancel={handleClose}
      footer={[
        <Button key="cancel" onClick={handleClose} disabled={uploading}>Cancel</Button>,
        <Button
          key="upload"
          type="primary"
          loading={uploading}
          disabled={files.length === 0}
          onClick={handleUpload}
        >
          Upload {files.length > 0 ? `(${files.length})` : ''}
        </Button>,
      ]}
    >
      <Upload.Dragger
        multiple
        showUploadList={false}
        accept=".pdf,.doc,.docx,.txt"
        onChange={handleFileChange}
        beforeUpload={() => false}
        data-testid="batch-file-input"
      >
        <p><UploadOutlined /></p>
        <p>Click or drag files to upload</p>
        <p style={{ fontSize: 12, color: '#999' }}>PDF, DOC, DOCX, TXT • Max 10MB per file</p>
      </Upload.Dragger>

      {files.length > 0 && (
        <List
          style={{ marginTop: 16, maxHeight: 300, overflow: 'auto' }}
          dataSource={files}
          renderItem={(item) => (
            <List.Item
              key={item.uid}
              actions={[
                <DeleteOutlined key="remove" onClick={() => handleRemove(item.uid)} />,
              ]}
            >
              <List.Item.Meta
                title={item.file.name}
                description={
                  item.status === 'error' ? (
                    <span style={{ color: 'red' }}>{item.error}</span>
                  ) : item.status === 'done' ? (
                    <span style={{ color: 'green' }}>Uploaded</span>
                  ) : item.status === 'uploading' ? (
                    <span style={{ color: 'blue' }}>Uploading...</span>
                  ) : (
                    <span style={{ color: '#999' }}>Ready</span>
                  )
                }
              />
            </List.Item>
          )}
        />
      )}
    </Modal>
  );
}
