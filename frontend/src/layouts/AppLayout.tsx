import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Space } from 'antd';
import {
  DashboardOutlined,
  SettingOutlined,
  LogoutOutlined,
  TeamOutlined,
  ShopOutlined,
  FundProjectionScreenOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/context/AuthContext';
import AppLogo from '@/components/AppLogo';
import { HEADING_FONT } from '@/theme/antdTheme';

const { Header, Sider, Content } = Layout;

const NAV_ITEMS = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/people-payroll', icon: <TeamOutlined />, label: 'People & Payroll' },
  {
    key: '/customer-management',
    icon: <ShopOutlined />,
    label: 'Customer Management',
  },
  {
    key: '/budgeting',
    icon: <FundProjectionScreenOutlined />,
    label: 'Budgeting & Forecasting',
  },
  { key: '/revenue', icon: <DollarOutlined />, label: 'Revenue' },
  { key: '/settings', icon: <SettingOutlined />, label: 'Settings' },
];

const TOPBAR_META: Record<string, { title: string; subtitle: string }> = {
  '/dashboard': { title: 'Dashboard', subtitle: 'Financial planning overview' },
  '/people-payroll': {
    title: 'People & Payroll',
    subtitle: 'Imports, periods, master data & analytics',
  },
  '/customer-management': {
    title: 'Customer Management',
    subtitle: 'Customers, rate cards & project codes',
  },
  '/budgeting': {
    title: 'Budgeting & Forecasting',
    subtitle: 'AOP plan, rolling forecast & Plan vs Actual',
  },
  '/revenue': {
    title: 'Revenue',
    subtitle: 'Zoho Books imports, invoices & revenue vs plan',
  },
  '/settings': { title: 'Settings', subtitle: 'Workspace & members' },
};

function resolveTopbarMeta(pathname: string) {
  if (TOPBAR_META[pathname]) return TOPBAR_META[pathname];
  if (pathname.startsWith('/people-payroll')) return TOPBAR_META['/people-payroll'];
  if (pathname.startsWith('/customer-management')) {
    return TOPBAR_META['/customer-management'];
  }
  if (pathname.startsWith('/budgeting')) return TOPBAR_META['/budgeting'];
  if (pathname.startsWith('/revenue')) return TOPBAR_META['/revenue'];
  return { title: '', subtitle: '' };
}

function selectedNavKey(pathname: string): string {
  if (pathname.startsWith('/people-payroll')) return '/people-payroll';
  if (pathname.startsWith('/customer-management')) return '/customer-management';
  if (pathname.startsWith('/budgeting')) return '/budgeting';
  if (pathname.startsWith('/revenue')) return '/revenue';
  return pathname;
}

export default function AppLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const meta = resolveTopbarMeta(pathname);

  return (
    <Layout style={{ height: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          position: 'sticky',
          top: 0,
          zIndex: 100,
        }}
      >
        <AppLogo variant="dark" height={28} />

        <Space>
          <div style={{ textAlign: 'right' }}>
            <div
              style={{
                fontFamily: HEADING_FONT,
                fontWeight: 700,
                fontSize: 17,
                color: '#ffffff',
                letterSpacing: '-0.01em',
                lineHeight: 1.2,
              }}
            >
              {meta.title}
            </div>
            <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.55)', marginTop: 1 }}>
              {meta.subtitle}
            </div>
          </div>
        </Space>

        <Button
          type="text"
          icon={<LogoutOutlined />}
          onClick={logout}
          style={{ color: 'rgba(255,255,255,0.75)' }}
        >
          Sign out
        </Button>
      </Header>

      <Layout>
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          width={220}
          style={{ background: '#ffffff', borderRight: '1px solid #d8d8d8' }}
        >
          {!collapsed && (
            <div
              style={{
                padding: '14px 20px 10px',
                borderBottom: '1px solid #d8d8d8',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <AppLogo variant="light" height={22} showWordmark={false} />
                <div>
                  <div
                    style={{
                      fontFamily: HEADING_FONT,
                      fontWeight: 700,
                      fontSize: 12,
                      color: '#525957',
                      letterSpacing: '-0.01em',
                    }}
                  >
                    cognologix
                  </div>
                  <div style={{ fontSize: 10, color: '#888888' }}>Financial planning</div>
                </div>
              </div>
            </div>
          )}

          <Menu
            mode="inline"
            selectedKeys={[selectedNavKey(pathname)]}
            items={NAV_ITEMS}
            onClick={({ key }) => {
              if (key === '/people-payroll') {
                navigate('/people-payroll/imports/zoho-people');
              } else if (key === '/customer-management') {
                navigate('/customer-management/customers');
              } else if (key === '/budgeting') {
                navigate('/budgeting/dashboard');
              } else if (key === '/revenue') {
                navigate('/revenue/imports/zoho-books-invoices');
              } else {
                navigate(key);
              }
            }}
            style={{ border: 'none', marginTop: 8 }}
          />

          <div
            style={{
              position: 'absolute',
              bottom: 48,
              left: 0,
              right: 0,
              padding: '10px 14px',
              borderTop: '1px solid #d8d8d8',
              display: 'flex',
              alignItems: 'center',
              gap: 10,
            }}
          >
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: '50%',
                background: 'linear-gradient(90deg,#f68c45 0%,#f05756 100%)',
                color: '#fff',
                fontFamily: HEADING_FONT,
                fontWeight: 700,
                fontSize: 12,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              AK
            </div>
            {!collapsed && (
              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    fontSize: 13,
                    fontWeight: 700,
                    color: '#2a2a2a',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  Anita Krishnan
                </div>
                <div style={{ fontSize: 11, color: '#888888' }}>Head of Finance</div>
              </div>
            )}
          </div>
        </Sider>

        <Content style={{ overflow: 'auto', background: '#f7f6f4' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
