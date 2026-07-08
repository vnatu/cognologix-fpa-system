import { Typography } from 'antd';
import { HEADING_FONT } from '@/theme/antdTheme';

const { Title, Text } = Typography;

export default function DashboardPage() {
  return (
    <div style={{ padding: 28 }}>
      <Title
        level={2}
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          color: '#232323',
          letterSpacing: '-0.01em',
          marginBottom: 8,
        }}
      >
        Dashboard
      </Title>
      <Text style={{ color: '#888888', fontSize: 14 }}>Coming soon.</Text>
    </div>
  );
}
