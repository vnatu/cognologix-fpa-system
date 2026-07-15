import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  BarChartOutlined,
  CloudUploadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  {
    key: 'imports',
    icon: <CloudUploadOutlined />,
    label: 'Imports',
    children: [
      {
        key: '/revenue/imports/zoho-books-invoices',
        label: 'Zoho Books Invoices',
      },
      {
        key: '/revenue/imports/zoho-books-credit-notes',
        label: 'Zoho Books Credit Notes',
      },
    ],
  },
  {
    key: '/revenue/invoices',
    icon: <FileTextOutlined />,
    label: 'Invoices',
  },
  {
    key: '/revenue/dashboard',
    icon: <BarChartOutlined />,
    label: 'Dashboard',
  },
];

function selectedKeys(pathname: string): string[] {
  if (pathname.startsWith('/revenue/imports')) {
    return [pathname];
  }
  if (pathname.startsWith('/revenue/invoices')) {
    return ['/revenue/invoices'];
  }
  if (pathname.startsWith('/revenue/dashboard')) {
    return ['/revenue/dashboard'];
  }
  return [pathname];
}

function openKeys(pathname: string): string[] {
  if (pathname.startsWith('/revenue/imports')) {
    return ['imports'];
  }
  return [];
}

export default function RevenueLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

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
          selectedKeys={selectedKeys(pathname)}
          defaultOpenKeys={openKeys(pathname)}
          items={MENU_ITEMS}
          onClick={({ key }) => {
            if (key.startsWith('/')) navigate(key);
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
