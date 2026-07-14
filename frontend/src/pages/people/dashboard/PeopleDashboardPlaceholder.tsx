import { Empty } from 'antd';
import { BarChartOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';

export default function PeopleDashboardPlaceholder() {
  return (
    <div
      style={{
        padding: 64,
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
          background: 'var(--ant-color-fill-quaternary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--ant-color-text-description)',
          fontSize: 22,
        }}
      >
        <BarChartOutlined />
      </div>
      <h2
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 18,
          margin: '2px 0 0',
        }}
      >
        People Analytics Dashboard
      </h2>
      <Empty description="Coming soon" />
    </div>
  );
}
