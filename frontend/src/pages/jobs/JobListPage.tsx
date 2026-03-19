import { useEffect, useState } from 'react';
import { Table, Button, Tag, Popconfirm, Space, message, Empty } from 'antd';
import { useNavigate } from 'react-router-dom';
import { listJobs, deleteJob, type JobListItem } from '../../api/jobs';
import type { Page } from '../../api/types';

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default', PUBLISHED: 'success', PAUSED: 'warning', CLOSED: 'error',
};

export default function JobListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<JobListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const data = await listJobs(p - 1, 10);
      setPage(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(1); }, []);

  const handleDelete = async (id: string) => {
    try {
      await deleteJob(id);
      message.success('Job deleted');
      load(current);
    } catch {
      message.error('Failed to delete job');
    }
  };

  const columns = [
    { title: 'Title', dataIndex: 'title', key: 'title' },
    { title: 'Department', dataIndex: 'departmentName', key: 'departmentName' },
    {
      title: 'Status', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={STATUS_COLORS[s] ?? 'default'}>{s}</Tag>,
    },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', render: (d: string) => new Date(d).toLocaleDateString() },
    {
      title: 'Actions', key: 'actions',
      render: (_: unknown, record: JobListItem) => (
        <Space>
          <Button size="small" onClick={() => navigate(`/jobs/${record.id}`)}>View</Button>
          <Popconfirm title="Delete this job?" onConfirm={() => handleDelete(record.id)}>
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
        <h2 style={{ margin: 0 }}>Jobs</h2>
        <Button type="primary" onClick={() => navigate('/jobs/new')}>Create JD</Button>
      </div>
      {isEmpty ? (
        <Empty description="No jobs yet. Start by adding your first job description." />
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
