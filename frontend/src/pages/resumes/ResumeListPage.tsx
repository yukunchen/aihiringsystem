import { useEffect, useState } from 'react';
import { Table, Button, Tag, Popconfirm, Space, message, Empty } from 'antd';
import { useNavigate } from 'react-router-dom';
import { listResumes, deleteResume, downloadResume, type ResumeListItem, type Page } from '../../api/resumes';

const STATUS_COLORS: Record<string, string> = {
  UPLOADED: 'default', PARSING: 'processing', PARSED: 'success',
  PARSE_FAILED: 'error', VECTORIZING: 'processing', VECTORIZED: 'success',
};

export default function ResumeListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<ResumeListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const data = await listResumes(p - 1, 10);
      setPage(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(1); }, []);

  const handleDelete = async (id: string) => {
    try {
      await deleteResume(id);
      message.success('Resume deleted');
      load(current);
    } catch {
      message.error('Failed to delete resume');
    }
  };

  const handleDownload = async (record: ResumeListItem) => {
    try {
      const blob = await downloadResume(record.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = record.fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      message.error('Download failed');
    }
  };

  const columns = [
    { title: 'Filename', dataIndex: 'fileName', key: 'fileName' },
    { title: 'Source', dataIndex: 'source', key: 'source' },
    {
      title: 'Status', dataIndex: 'status', key: 'status',
      render: (status: string) => <Tag color={STATUS_COLORS[status] ?? 'default'}>{status}</Tag>,
    },
    { title: 'Uploaded', dataIndex: 'uploadedAt', key: 'uploadedAt', render: (d: string) => new Date(d).toLocaleDateString() },
    {
      title: 'Actions', key: 'actions',
      render: (_: unknown, record: ResumeListItem) => (
        <Space>
          <Button size="small" onClick={() => handleDownload(record)}>Download</Button>
          <Popconfirm title="Delete this resume?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const isEmpty = page && page.totalElements === 0;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Resumes</h2>
        <Button type="primary" onClick={() => navigate('/resumes/upload')}>Upload Resume</Button>
      </div>
      {isEmpty ? (
        <Empty description="No resumes yet. Upload your first resume to get started." />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={page?.content ?? []}
          loading={loading}
          pagination={{
            current, total: page?.totalElements ?? 0, pageSize: 10,
            onChange: (p) => { setCurrent(p); load(p); },
          }}
        />
      )}
    </div>
  );
}
