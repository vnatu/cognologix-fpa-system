import { Typography } from 'antd';
import { HEADING_FONT } from '@/theme/antdTheme';
import ColumnMappingTemplatesSection from '../settings/ColumnMappingTemplatesSection';

const { Title } = Typography;

export default function ColumnMappingPage() {
  return (
    <div style={{ padding: 28, maxWidth: 960 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT, marginBottom: 16 }}>
        Column mapping
      </Title>
      <ColumnMappingTemplatesSection />
    </div>
  );
}
