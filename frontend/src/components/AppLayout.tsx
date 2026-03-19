import { Layout, Menu, Button, Typography, Space } from 'antd';
import { FileTextOutlined, SolutionOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Sider, Header, Content } = Layout;

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = location.pathname.startsWith('/resumes') ? 'resumes' : 'jobs';

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '16px', fontWeight: 700, fontSize: 16 }}>AI Hiring</div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={[
            { key: 'resumes', icon: <FileTextOutlined />, label: 'Resumes', onClick: () => navigate('/resumes') },
            { key: 'jobs', icon: <SolutionOutlined />, label: 'Jobs', onClick: () => navigate('/jobs') },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography.Text strong>AI Hiring Platform</Typography.Text>
          <Space>
            <Typography.Text type="secondary">{user?.username}</Typography.Text>
            <Button onClick={handleLogout}>Logout</Button>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
