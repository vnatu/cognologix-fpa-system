import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  BarChartOutlined,
  CalendarOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  {
    key: 'imports',
    icon: <CloudUploadOutlined />,
    label: 'Imports',
    children: [
      { key: '/people-payroll/imports/zoho-people', label: 'Zoho People' },
      { key: '/people-payroll/imports/zoho-payroll', label: 'Zoho Payroll' },
      {
        key: '/people-payroll/imports/zoho-payroll-fnf',
        label: 'Zoho Payroll — F&F',
      },
      {
        key: '/people-payroll/imports/zoho-people-exited',
        label: 'Zoho People Exited',
      },
    ],
  },
  {
    key: '/people-payroll/periods',
    icon: <CalendarOutlined />,
    label: 'Period Management',
  },
  {
    key: '/people-payroll/master',
    icon: <DatabaseOutlined />,
    label: 'Master Data',
  },
  {
    key: '/people-payroll/dashboard',
    icon: <BarChartOutlined />,
    label: 'Dashboard',
  },
];

function selectedKeys(pathname: string): string[] {
  if (pathname.startsWith('/people-payroll/imports')) {
    return [pathname];
  }
  return [pathname];
}

function openKeys(pathname: string): string[] {
  if (pathname.startsWith('/people-payroll/imports')) {
    return ['imports'];
  }
  return [];
}

export default function PeoplePayrollLayout() {
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
