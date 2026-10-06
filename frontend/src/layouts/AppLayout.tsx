import { useEffect, useState } from 'react';
import { Outlet, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Button, Space, Tooltip, theme } from 'antd';
import {
  DashboardOutlined,
  SettingOutlined,
  LogoutOutlined,
  TeamOutlined,
  ShopOutlined,
  FundProjectionScreenOutlined,
  DollarOutlined,
  AccountBookOutlined,
  FileExcelOutlined,
  BankOutlined,
  FileProtectOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/context/AuthContext';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';
import { fetchMe } from '@/api/users';
import AppLogo from '@/components/AppLogo';
import NotificationBell from '@/components/NotificationBell';
import { SidebarCollapseIcon, SidebarExpandIcon } from '@/components/icons/SidebarCollapseIcon';
import { HEADING_FONT } from '@/theme/antdTheme';

const { Header, Sider, Content } = Layout;

const SIDEBAR_COLLAPSED_KEY = 'sidebar_collapsed';
const SIDEBAR_EXPANDED_WIDTH = 220;
const SIDEBAR_COLLAPSED_WIDTH = 64;

function readSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}

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
  { key: '/expenses', icon: <AccountBookOutlined />, label: 'Expenses' },
  { key: '/bank-reconciliation', icon: <BankOutlined />, label: 'Bank Reconciliation' },
  { key: '/contracts', icon: <FileProtectOutlined />, label: 'Contracts' },
  { key: '/reports', icon: <FileExcelOutlined />, label: 'Reports' },
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
  '/reports': {
    title: 'Reports',
    subtitle: 'Standard Excel downloads for Finance review',
  },
  '/revenue': {
    title: 'Revenue',
    subtitle: 'Zoho Books imports, invoices & revenue vs plan',
  },
  '/expenses': {
    title: 'Expenses',
    subtitle: 'Monthly overhead actuals & category setup',
  },
  '/bank-reconciliation': {
    title: 'Bank Reconciliation',
    subtitle: 'HDFC statements, TallyPrime mapping & FinSync export',
  },
  '/contracts': {
    title: 'Contracts',
    subtitle: 'Repository, versions, expiry alerts & templates',
  },
  '/settings': { title: 'Settings', subtitle: 'Workspace & members' },
  '/account': { title: 'Account', subtitle: 'Profile & password' },
};

function resolveTopbarMeta(pathname: string) {
  if (TOPBAR_META[pathname]) return TOPBAR_META[pathname];
  if (pathname.startsWith('/people-payroll')) return TOPBAR_META['/people-payroll'];
  if (pathname.startsWith('/customer-management')) {
    return TOPBAR_META['/customer-management'];
  }
  if (pathname.startsWith('/budgeting')) return TOPBAR_META['/budgeting'];
  if (pathname.startsWith('/reports')) return TOPBAR_META['/reports'];
  if (pathname.startsWith('/revenue')) return TOPBAR_META['/revenue'];
  if (pathname.startsWith('/expenses')) return TOPBAR_META['/expenses'];
  if (pathname.startsWith('/bank-reconciliation')) return TOPBAR_META['/bank-reconciliation'];
  if (pathname.startsWith('/contracts')) return TOPBAR_META['/contracts'];
  return { title: '', subtitle: '' };
}

function selectedNavKey(pathname: string): string {
  if (pathname.startsWith('/people-payroll')) return '/people-payroll';
  if (pathname.startsWith('/customer-management')) return '/customer-management';
  if (pathname.startsWith('/budgeting')) return '/budgeting';
  if (pathname.startsWith('/reports')) return '/reports';
  if (pathname.startsWith('/revenue')) return '/revenue';
  if (pathname.startsWith('/expenses')) return '/expenses';
  if (pathname.startsWith('/bank-reconciliation')) return '/bank-reconciliation';
  if (pathname.startsWith('/contracts')) return '/contracts';
  if (pathname.startsWith('/account')) return '';
  return pathname;
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function resolveNavTarget(key: string): string {
  if (key === '/people-payroll') return '/people-payroll/imports/zoho-people';
  if (key === '/customer-management') return '/customer-management/customers';
  if (key === '/budgeting') return '/budgeting/dashboard';
  if (key === '/reports') return '/reports/standard';
  if (key === '/revenue') return '/revenue/imports/zoho-books-invoices';
  if (key === '/expenses') return '/expenses/entry';
  if (key === '/bank-reconciliation') return '/bank-reconciliation/new-run';
  if (key === '/contracts') return '/contracts/dashboard';
  return key;
}

export default function AppLayout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { logout, mustChangePassword, role, email } = useAuth();
  const { confirmIfDirty } = useUnsavedChanges();
  const { token } = theme.useToken();
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed);
  const [displayName, setDisplayName] = useState(email ?? 'User');

  const go = (to: string) => {
    if (pathname === to) return;
    confirmIfDirty(() => navigate(to));
  };

  const toggleSidebar = () => {
    setCollapsed((current) => {
      const next = !current;
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(next));
      } catch {
        /* preference is optional */
      }
      return next;
    });
  };

  useEffect(() => {
    fetchMe()
      .then((me) => setDisplayName(me.fullName))
      .catch(() => {
        /* keep email fallback */
      });
  }, [email]);

  if (mustChangePassword && pathname !== '/account') {
    return <Navigate to="/account" replace />;
  }

  const meta = resolveTopbarMeta(pathname);
  const roleLabel = role === 'ADMIN' ? 'Admin' : role === 'VIEWER' ? 'Viewer' : '';

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
          // Ant Design Header defaults line-height to header height (64px),
          // which clips multi-line title/subtitle blocks.
          lineHeight: 'normal',
        }}
      >
        <AppLogo variant="dark" height={28} />

        <Space align="center">
          <div style={{ textAlign: 'right', lineHeight: 'normal' }}>
            <div
              style={{
                fontFamily: HEADING_FONT,
                fontWeight: 700,
                fontSize: 17,
                color: '#ffffff',
                letterSpacing: '-0.01em',
                lineHeight: 1.25,
              }}
            >
              {meta.title}
            </div>
            <div
              style={{
                fontSize: 12,
                color: 'rgba(255,255,255,0.55)',
                marginTop: 2,
                lineHeight: 1.3,
              }}
            >
              {meta.subtitle}
            </div>
          </div>
        </Space>

        <Space align="center">
          <NotificationBell />
          <Button
            type="text"
            icon={<LogoutOutlined />}
            onClick={() => confirmIfDirty(() => logout('logged_out'))}
            style={{ color: 'rgba(255,255,255,0.75)' }}
          >
            Sign out
          </Button>
        </Space>
      </Header>

      <Layout>
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          trigger={null}
          width={SIDEBAR_EXPANDED_WIDTH}
          collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
          style={{
            background: '#ffffff',
            borderRight: '1px solid #d8d8d8',
            transition: 'all 0.2s',
          }}
        >
          <div
            style={{
              padding: collapsed ? '12px 0 10px' : '14px 8px 10px 20px',
              borderBottom: '1px solid #d8d8d8',
              display: 'flex',
              flexDirection: collapsed ? 'column' : 'row',
              alignItems: 'center',
              justifyContent: collapsed ? 'center' : 'space-between',
              gap: collapsed ? 8 : 0,
            }}
          >
            {collapsed ? (
              <AppLogo variant="light" height={22} showWordmark={false} />
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                <AppLogo variant="light" height={22} showWordmark={false} />
                <div style={{ minWidth: 0 }}>
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
            )}
            <Button
              type="text"
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              icon={
                collapsed ? (
                  <SidebarExpandIcon color={token.colorTextSecondary} />
                ) : (
                  <SidebarCollapseIcon color={token.colorTextSecondary} />
                )
              }
              onClick={toggleSidebar}
              style={{
                width: 32,
                height: 32,
                padding: 0,
                flexShrink: 0,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            />
          </div>

          <Menu
            mode="inline"
            inlineCollapsed={collapsed}
            selectedKeys={[selectedNavKey(pathname)]}
            items={NAV_ITEMS.map((item) => {
              if (!mustChangePassword) return item;
              return {
                ...item,
                disabled: true,
                label: collapsed ? (
                  item.label
                ) : (
                  <Tooltip title="Change your password to continue">
                    <span>{item.label}</span>
                  </Tooltip>
                ),
              };
            })}
            onClick={({ key }) => {
              if (mustChangePassword) return;
              go(resolveNavTarget(key));
            }}
            style={{
              border: 'none',
              marginTop: 8,
              paddingBottom: 72,
              width: collapsed ? SIDEBAR_COLLAPSED_WIDTH : '100%',
            }}
          />

          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              background: '#ffffff',
            }}
          >
          <Tooltip title={collapsed ? displayName : undefined} placement="right">
          <div
            role="button"
            tabIndex={0}
            onClick={() => go('/account')}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') go('/account');
            }}
            style={{
              padding: collapsed ? '10px 0' : '10px 14px',
              borderTop: '1px solid #d8d8d8',
              display: 'flex',
              alignItems: 'center',
              justifyContent: collapsed ? 'center' : 'flex-start',
              gap: 10,
              cursor: 'pointer',
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
              {initials(displayName)}
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
                  {displayName}
                </div>
                <div style={{ fontSize: 11, color: '#888888' }}>{roleLabel}</div>
              </div>
            )}
          </div>
          </Tooltip>
          </div>
        </Sider>

        <Content style={{ overflow: 'auto', background: '#f7f6f4' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
