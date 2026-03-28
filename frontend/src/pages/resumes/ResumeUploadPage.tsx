import { useState } from 'react';
import { Upload, Button, Alert, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { uploadResume } from '../../api/resumes';

const ACCEPTED = '.pdf,.docx,.txt';

export default function ResumeUploadPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleUpload = async () => {
    if (!file) return;
    setError(null);
    setLoading(true);
    try {
      await uploadResume(file);
      message.success('Resume uploaded successfully');
      navigate('/resumes');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <h2>Upload Resume</h2>
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Upload.Dragger
        accept={ACCEPTED}
        multiple={false}
        beforeUpload={(f) => { setFile(f); return false; }}
        onRemove={() => setFile(null)}
        maxCount={1}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">Click or drag a resume file to this area to upload</p>
        <p className="ant-upload-hint">Supports PDF, DOCX, TXT</p>
      </Upload.Dragger>
      <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
        <Button type="primary" disabled={!file} loading={loading} onClick={handleUpload} data-testid="upload-btn">
          Upload
        </Button>
        <Button onClick={() => navigate('/resumes')}>Cancel</Button>
      </div>
    </div>
  );
}
