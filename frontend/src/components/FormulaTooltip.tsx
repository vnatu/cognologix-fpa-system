import { Popover, theme, Typography } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

const { Text } = Typography;

export interface FormulaTooltipProps {
  metric: string;
  formula: string;
  components?: string[];
  source?: string;
}

/**
 * Clickable formula help icon for dashboard metrics.
 */
export default function FormulaTooltip({
  metric,
  formula,
  components = [],
  source,
}: FormulaTooltipProps) {
  const { token } = theme.useToken();

  return (
    <Popover
      title={metric}
      trigger="click"
      content={
        <div style={{ maxWidth: 360 }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>
            {formula}
          </Text>
          {components.length > 0 && (
            <ul style={{ margin: '0 0 8px', paddingLeft: 18 }}>
              {components.map((c) => (
                <li key={c}>
                  <Text>{c}</Text>
                </li>
              ))}
            </ul>
          )}
          {source && (
            <Text
              italic
              type="secondary"
              style={{ display: 'block', fontSize: 12 }}
            >
              {source}
            </Text>
          )}
        </div>
      }
    >
      <QuestionCircleOutlined
        style={{
          color: token.colorTextSecondary,
          fontSize: 14,
          cursor: 'pointer',
          marginLeft: 6,
        }}
        aria-label={`Formula for ${metric}`}
      />
    </Popover>
  );
}
