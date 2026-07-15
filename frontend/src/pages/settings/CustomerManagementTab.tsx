import { Alert, Tabs } from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { HEADING_FONT } from '@/theme/antdTheme';
import ConcentrationRiskSection from './customer/ConcentrationRiskSection';

/**
 * Settings → Customer Management — config only (ADR-021).
 * Operational screens live under top-level Customer Management nav.
 */
export default function CustomerManagementTab() {
  const tabs = [
    {
      key: 'concentration-risk',
      label: (
        <span>
          <WarningOutlined style={{ marginRight: 6 }} />
          Concentration Risk
        </span>
      ),
      children: (
        <div style={{ padding: '0 0 0 24px' }}>
          <ConcentrationRiskSection />
        </div>
      ),
    },
  ];

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          <>
            Customer, Rate Card, and Project Code management has moved to the{' '}
            <Link to="/customer-management/customers">Customer Management</Link>{' '}
            section in the main navigation.
          </>
        }
      />
      <Tabs
        tabPosition="left"
        items={tabs}
        style={{ minHeight: 520 }}
        tabBarStyle={{
          fontFamily: HEADING_FONT,
          fontWeight: 600,
          minWidth: 190,
        }}
      />
    </div>
  );
}
