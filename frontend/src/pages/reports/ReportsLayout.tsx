import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import { FileExcelOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';

const { Sider, Content } = Layout;

const MENU_ITEMS: MenuProps['items'] = [
  {
    key: '/reports/standard',
    icon: <FileExcelOutlined />,
    label: 'Standard Reports',
  },
];

export default function ReportsLayout() {
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
          selectedKeys={[pathname.startsWith('/reports') ? '/reports/standard' : pathname]}
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
