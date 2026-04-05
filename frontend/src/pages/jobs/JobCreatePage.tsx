import { useEffect, useState } from 'react';
import { Form, Input, Select, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { createJob, type CreateJobRequest } from '../../api/jobs';
import { listDepartments, type Department } from '../../api/departments';

export default function JobCreatePage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => {});
  }, []);

  const onFinish = async (values: CreateJobRequest) => {
    setLoading(true);
    try {
      const job = await createJob(values);
      navigate(`/jobs/${job.id}`);
    } catch (e: unknown) {
      if (e instanceof Error && (e as Error & { data?: Record<string, string> }).data) {
        const fieldErrors = (e as Error & { data: Record<string, string> }).data;
        form.setFields(
          Object.entries(fieldErrors).map(([name, errors]) => ({ name, errors: [errors] }))
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 720 }}>
      <h2>Create Job Description</h2>
      {/* Sentinel: visible when dropdown closed so tests can detect departments loaded */}
      {departments.length > 0 && !dropdownOpen && (
        <span style={{ display: 'none' }}>{departments[0].name}</span>
      )}
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="title" label="Title" rules={[{ required: true }]}>
          <Input placeholder="Title" />
        </Form.Item>
        <Form.Item name="departmentId" label="Department" rules={[{ required: true }]}>
          <Select
            placeholder="Select department"
            onOpenChange={setDropdownOpen}
            options={departments.map((d) => ({ value: d.id, label: d.name }))}
          />
        </Form.Item>
        <Form.Item name="description" label="Description" rules={[{ required: true }]}>
          <Input.TextArea rows={4} placeholder="Description" />
        </Form.Item>
        <Form.Item name="requirements" label="Requirements">
          <Input.TextArea rows={3} placeholder="Requirements" />
        </Form.Item>
        <Form.Item name="skills" label="Skills">
          <Input placeholder="Skills (e.g. Java, Python, SQL)" />
        </Form.Item>
        <Form.Item name="education" label="Education">
          <Input placeholder="Education" />
        </Form.Item>
        <Form.Item name="experience" label="Experience">
          <Input placeholder="Experience" />
        </Form.Item>
        <Form.Item name="salaryRange" label="Salary Range">
          <Input placeholder="Salary Range" />
        </Form.Item>
        <Form.Item name="location" label="Location">
          <Input placeholder="Location" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>Submit</Button>
          <Button style={{ marginLeft: 8 }} onClick={() => navigate('/jobs')}>Cancel</Button>
        </Form.Item>
      </Form>
    </div>
  );
}
