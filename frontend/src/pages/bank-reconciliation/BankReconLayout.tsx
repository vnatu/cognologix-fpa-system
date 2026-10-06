import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import {
  CloudServerOutlined,
  HistoryOutlined,
  PlusOutlined,
  BookOutlined,
  BankOutlined,
  BulbOutlined,
  FilterOutlined,
  DatabaseOutlined,
  TableOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  { key: '/bank-reconciliation/new-run', icon: <PlusOutlined />, label: 'New Run' },
  { key: '/bank-reconciliation/run-history', icon: <HistoryOutlined />, label: 'Run History' },
  { key: '/bank-reconciliation/config/ledgers', icon: <BookOutlined />, label: 'Ledger Master' },
  { key: '/bank-reconciliation/config/column-mapping', icon: <TableOutlined />, label: 'Column Mapping' },
  { key: '/bank-reconciliation/config/account-mapping', icon: <BankOutlined />, label: 'Account Mapping' },
  { key: '/bank-reconciliation/config/mappings', icon: <DatabaseOutlined />, label: 'Learned Mappings' },
  { key: '/bank-reconciliation/config/hints', icon: <BulbOutlined />, label: 'LLM Hints' },
  { key: '/bank-reconciliation/config/contra-rules', icon: <FilterOutlined />, label: 'Contra Rules' },
  { key: '/bank-reconciliation/config/ollama', icon: <CloudServerOutlined />, label: 'LLM Settings' },
];

function selectedKey(pathname: string): string {
  if (pathname.startsWith('/bank-reconciliation/runs/')) return '/bank-reconciliation/run-history';
  const keys = [
    '/bank-reconciliation/new-run',
    '/bank-reconciliation/run-history',
    '/bank-reconciliation/config/ledgers',
    '/bank-reconciliation/config/column-mapping',
    '/bank-reconciliation/config/account-mapping',
    '/bank-reconciliation/config/mappings',
    '/bank-reconciliation/config/hints',
    '/bank-reconciliation/config/contra-rules',
    '/bank-reconciliation/config/ollama',
  ];
  return keys.find((key) => pathname.startsWith(key)) ?? '/bank-reconciliation/new-run';
}

export default function BankReconLayout() {
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
