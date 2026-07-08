import { TeamOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';

export default function PeoplePayrollTab() {
  return (
    <div
      style={{
        background: '#ffffff',
        border: '1px solid #d8d8d8',
        borderRadius: 8,
        padding: '64px 40px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
        gap: 12,
      }}
    >
      <div
        style={{
          width: 52,
          height: 52,
          borderRadius: 14,
          background: '#f0efec',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#888888',
          fontSize: 22,
        }}
      >
        <TeamOutlined />
      </div>
      <h2
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 18,
          color: '#232323',
          margin: '2px 0 0',
        }}
      >
        People & Payroll settings
      </h2>
      <p style={{ fontSize: 14, lineHeight: 1.6, color: '#555555', maxWidth: 360, margin: 0 }}>
        Coming soon. Delivery Practice Unit list, Management/Leadership BU mapping,
        and payroll configuration will live here.
      </p>
    </div>
  );
}
