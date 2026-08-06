import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import { FormOutlined, HistoryOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  {
    key: '/expenses/entry',
    icon: <FormOutlined />,
    label: 'Expense Entry',
  },
  {
    key: '/expenses/history',
    icon: <HistoryOutlined />,
    label: 'Expense History',
  },
];

export default function ExpensesLayout() {
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
          selectedKeys={[pathname.startsWith('/expenses/history') ? '/expenses/history' : '/expenses/entry']}
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
