import { useState } from 'react';
import { Tabs } from 'antd';
import {
  TeamOutlined,
  FileTextOutlined,
  TagsOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';
import CustomersSection from './customer/CustomersSection';
import RateCardsSection from './customer/RateCardsSection';
import ProjectCodesSection from './customer/ProjectCodesSection';
import ConcentrationRiskSection from './customer/ConcentrationRiskSection';

export default function CustomerManagementTab() {
  // Shared selected customer — set from the Customers list, consumed by
  // Rate Cards and Project Codes sections as their default context.
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null);

  const tabs = [
    {
      key: 'customers',
      label: (
        <span>
          <TeamOutlined style={{ marginRight: 6 }} />
          Customers
        </span>
      ),
      children: (
        <div style={{ padding: '0 0 0 24px' }}>
          <CustomersSection onSelectCustomer={setSelectedCustomerId} />
        </div>
      ),
    },
    {
      key: 'rate-cards',
      label: (
        <span>
          <FileTextOutlined style={{ marginRight: 6 }} />
          Rate Cards
        </span>
      ),
      children: (
        <div style={{ padding: '0 0 0 24px' }}>
          <RateCardsSection
            selectedCustomerId={selectedCustomerId}
            onSelectCustomer={setSelectedCustomerId}
          />
        </div>
      ),
    },
    {
      key: 'project-codes',
      label: (
        <span>
          <TagsOutlined style={{ marginRight: 6 }} />
          Project Codes
        </span>
      ),
      children: (
        <div style={{ padding: '0 0 0 24px' }}>
          <ProjectCodesSection
            selectedCustomerId={selectedCustomerId}
            onSelectCustomer={setSelectedCustomerId}
          />
        </div>
      ),
    },
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
  );
}
