import { Tabs } from 'antd';
import { HEADING_FONT } from '@/theme/antdTheme';
import GeneralTab from './GeneralTab';
import PeoplePayrollTab from './PeoplePayrollTab';
import CustomerManagementTab from './CustomerManagementTab';
import RevenueTab from './RevenueTab';
import ExpensesTab from './ExpensesTab';
import BankReconciliationTab from './BankReconciliationTab';
import ContractsTab from './ContractsTab';
import SecurityTab from './SecurityTab';

const TABS = [
  { key: 'general',             label: 'General',             children: <GeneralTab /> },
  { key: 'security',            label: 'Security',            children: <SecurityTab /> },
  { key: 'people-payroll',      label: 'People & Payroll',    children: <PeoplePayrollTab /> },
  { key: 'customer-management', label: 'Customer Management', children: <CustomerManagementTab /> },
  { key: 'revenue',             label: 'Revenue',             children: <RevenueTab /> },
  { key: 'expenses',            label: 'Expenses',            children: <ExpensesTab /> },
  { key: 'bank-reconciliation', label: 'Bank Reconciliation', children: <BankReconciliationTab /> },
  { key: 'contracts',           label: 'Contracts',           children: <ContractsTab /> },
];

export default function SettingsPage() {
  return (
    <div style={{ padding: 28, maxWidth: 1240 }}>
      <Tabs
        defaultActiveKey="general"
        items={TABS}
        tabBarStyle={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
      />
    </div>
  );
}
