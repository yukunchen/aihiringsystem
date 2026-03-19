import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Descriptions, Tag, Select, Button, Form, Input, Divider, Alert,
  InputNumber, Spin, Table, Tooltip, Space, message,
} from 'antd';
import { getJob, updateJob, changeJobStatus, type JobDetail, type UpdateJobRequest } from '../../api/jobs';
import { listDepartments, type Department } from '../../api/departments';
import { match, type MatchResultItem } from '../../api/match';

const STATUS_TRANSITIONS: Record<string, string[]> = {
  DRAFT: ['PUBLISHED'],
  PUBLISHED: ['PAUSED', 'CLOSED'],
  PAUSED: ['PUBLISHED', 'CLOSED'],
  CLOSED: [],
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default', PUBLISHED: 'success', PAUSED: 'warning', CLOSED: 'error',
};

const LLM_COLOR = (score: number) => score >= 80 ? 'green' : score >= 60 ? 'orange' : 'red';

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<JobDetail | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [editing, setEditing] = useState(false);
  const [editForm] = Form.useForm();
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusDropdownOpen, setStatusDropdownOpen] = useState(false);

  const [matchLoading, setMatchLoading] = useState(false);
  const [topK, setTopK] = useState(10);
  const [matchResults, setMatchResults] = useState<MatchResultItem[] | null>(null);
  const [matchError, setMatchError] = useState<{ type: '422' | '503' | 'generic'; message: string } | null>(null);

  useEffect(() => {
    if (!id) return;
    getJob(id).then(setJob).catch(() => message.error('Failed to load job'));
    listDepartments().then(setDepartments).catch(() => {});
  }, [id]);

  const handleStatusChange = async (status: string) => {
    if (!id) return;
    setStatusError(null);
    try {
      const updated = await changeJobStatus(id, status);
      setJob(updated);
    } catch (e: unknown) {
      setStatusError(e instanceof Error ? e.message : 'Status change failed');
    }
  };

  const handleEdit = () => {
    if (!job) return;
    editForm.setFieldsValue(job);
    setEditing(true);
  };

  const handleSave = async () => {
    if (!id) return;
    try {
      const values: UpdateJobRequest = await editForm.validateFields();
      const updated = await updateJob(id, values);
      setJob(updated);
      setEditing(false);
    } catch (e: unknown) {
      if (e instanceof Error && (e as Error & { data?: Record<string, string> }).data) {
        const fieldErrors = (e as Error & { data: Record<string, string> }).data;
        editForm.setFields(
          Object.entries(fieldErrors).map(([name, errors]) => ({ name, errors: [errors] }))
        );
      }
    }
  };

  const handleMatch = async () => {
    if (!id) return;
    setMatchError(null);
    setMatchResults(null);
    setMatchLoading(true);
    try {
      const result = await match(id, topK);
      setMatchResults(result.results);
    } catch (e: unknown) {
      const status = (e as Error & { status?: number }).status;
      const msg = e instanceof Error ? e.message : 'Match failed';
      if (status === 422) {
        setMatchError({ type: '422', message: msg });
      } else if (status === 503) {
        setMatchError({ type: '503', message: msg });
      } else {
        setMatchError({ type: 'generic', message: msg });
      }
    } finally {
      setMatchLoading(false);
    }
  };

  if (!job) return <Spin />;

  const nextStatuses = STATUS_TRANSITIONS[job.status] ?? [];

  const matchColumns = [
    { title: '#', key: 'rank', render: (_: unknown, __: unknown, i: number) => i + 1, width: 50 },
    {
      title: 'Resume ID', dataIndex: 'resumeId', key: 'resumeId',
      render: (id: string) => <Tooltip title={id}>{id.slice(0, 8)}…</Tooltip>,
    },
    { title: 'Vector Score', dataIndex: 'vectorScore', key: 'vectorScore', render: (v: number) => v.toFixed(2) },
    {
      title: 'LLM Score', dataIndex: 'llmScore', key: 'llmScore',
      render: (v: number) => <span style={{ color: LLM_COLOR(v), fontWeight: 600 }}>{v}</span>,
    },
    {
      title: 'Reasoning', dataIndex: 'reasoning', key: 'reasoning',
      render: (text: string) => (
        <Tooltip title={text}>{text.length > 80 ? `${text.slice(0, 80)}…` : text}</Tooltip>
      ),
    },
    {
      title: 'Highlights', dataIndex: 'highlights', key: 'highlights',
      render: (tags: string[]) => <Space wrap>{tags.map((t) => <Tag key={t}>{t}</Tag>)}</Space>,
    },
  ];

  return (
    <div>
      {/* Section 1: JD Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>{job.title}</h2>
        {!editing && <Button onClick={handleEdit}>Edit</Button>}
      </div>

      {!editing ? (
        <>
          <div style={{ marginBottom: 12 }}>
            <Tag color={STATUS_COLORS[job.status] ?? 'default'}>{job.status}</Tag>
            {nextStatuses.length > 0 && (
              <span style={{ position: 'relative', display: 'inline-block', marginLeft: 8 }}>
                <div
                  role="combobox"
                  aria-expanded={statusDropdownOpen}
                  aria-haspopup="listbox"
                  tabIndex={0}
                  onClick={() => setStatusDropdownOpen(o => !o)}
                  style={{ cursor: 'pointer', border: '1px solid #d9d9d9', borderRadius: 4, padding: '2px 8px', display: 'inline-block' }}
                >
                  Change status
                </div>
                {statusDropdownOpen && (
                  <div role="listbox" style={{ position: 'absolute', background: 'white', border: '1px solid #d9d9d9', borderRadius: 4, zIndex: 100, minWidth: 120 }}>
                    {nextStatuses.map((s) => (
                      <div
                        key={s}
                        role="option"
                        aria-selected="false"
                        onClick={() => { handleStatusChange(s); setStatusDropdownOpen(false); }}
                        style={{ padding: '4px 12px', cursor: 'pointer' }}
                      >
                        {s}
                      </div>
                    ))}
                  </div>
                )}
              </span>
            )}
            {statusError && <span style={{ color: 'red', marginLeft: 8 }}>{statusError}</span>}
          </div>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Department">{job.departmentName}</Descriptions.Item>
            <Descriptions.Item label="Description">{job.description}</Descriptions.Item>
            {job.requirements && <Descriptions.Item label="Requirements">{job.requirements}</Descriptions.Item>}
            {job.skills && <Descriptions.Item label="Skills">{job.skills}</Descriptions.Item>}
            {job.education && <Descriptions.Item label="Education">{job.education}</Descriptions.Item>}
            {job.experience && <Descriptions.Item label="Experience">{job.experience}</Descriptions.Item>}
            {job.salaryRange && <Descriptions.Item label="Salary Range">{job.salaryRange}</Descriptions.Item>}
            {job.location && <Descriptions.Item label="Location">{job.location}</Descriptions.Item>}
          </Descriptions>
        </>
      ) : (
        <Form form={editForm} layout="vertical">
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="departmentId" label="Department" rules={[{ required: true }]}>
            <Select>
              {departments.map((d) => (
                <Select.Option key={d.id} value={d.id}>{d.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="requirements" label="Requirements"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="skills" label="Skills"><Input /></Form.Item>
          <Form.Item name="education" label="Education"><Input /></Form.Item>
          <Form.Item name="experience" label="Experience"><Input /></Form.Item>
          <Form.Item name="salaryRange" label="Salary Range"><Input /></Form.Item>
          <Form.Item name="location" label="Location"><Input /></Form.Item>
          <Form.Item>
            <Button type="primary" onClick={handleSave}>Save</Button>
            <Button style={{ marginLeft: 8 }} onClick={() => setEditing(false)}>Cancel</Button>
          </Form.Item>
        </Form>
      )}

      <Divider />

      {/* Section 2: AI Matching */}
      <h3>AI Matching</h3>
      <Space style={{ marginBottom: 16 }}>
        <InputNumber min={1} max={50} value={topK} onChange={(v) => setTopK(v ?? 10)} addonBefore="Top K" />
        <Button type="primary" onClick={handleMatch} loading={matchLoading}>
          Find Matching Resumes
        </Button>
      </Space>

      {matchError?.type === '422' && (
        <Alert
          type="warning"
          message="This JD has not been indexed yet. It will be ready shortly after creation."
          style={{ marginBottom: 16 }}
        />
      )}
      {matchError?.type === '503' && (
        <Alert
          type="error"
          message="AI matching service is currently unavailable."
          style={{ marginBottom: 16 }}
        />
      )}
      {matchError?.type === 'generic' && (
        <Alert type="error" message={matchError.message} style={{ marginBottom: 16 }} />
      )}

      {matchResults !== null && (
        matchResults.length === 0 ? (
          <Alert type="info" message="No matching resumes found." />
        ) : (
          <Table
            rowKey="resumeId"
            columns={matchColumns}
            dataSource={matchResults}
            pagination={false}
            size="small"
          />
        )
      )}
    </div>
  );
}
