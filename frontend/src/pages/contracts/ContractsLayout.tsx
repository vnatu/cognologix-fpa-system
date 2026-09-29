import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import { DashboardOutlined, FileTextOutlined, UnorderedListOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  { key: '/contracts/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/contracts/list', icon: <UnorderedListOutlined />, label: 'All Contracts' },
  { key: '/contracts/templates', icon: <FileTextOutlined />, label: 'Templates' },
];

function selectedKey(pathname: string): string {
  if (pathname.startsWith('/contracts/list')) return '/contracts/list';
  if (pathname.startsWith('/contracts/templates')) return '/contracts/templates';
  if (pathname.startsWith('/contracts/dashboard')) return '/contracts/dashboard';
  if (/^\/contracts\/[^/]+$/.test(pathname)) return '/contracts/list';
  return '/contracts/dashboard';
}

export default function ContractsLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { confirmIfDirty } = useUnsavedChanges();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Sider
        width={220}
        style={{
          background: 'var(--ant-color-bg-container)',
          borderRight: '1px solid var(--ant-color-border)',
        }}
      >
        <Menu
          mode="inline"
          selectedKeys={[selectedKey(pathname)]}
          items={MENU_ITEMS}
          onClick={({ key }) => {
            if (!key.startsWith('/')) return;
            if (pathname === key) return;
            confirmIfDirty(() => navigate(key));
          }}
          style={{ border: 'none', paddingTop: 8 }}
        />
      </Sider>
      <Content>
        <Outlet />
      </Content>
    </Layout>
  );
}
