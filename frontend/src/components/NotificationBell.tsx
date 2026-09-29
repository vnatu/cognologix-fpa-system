import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Dropdown, Typography, theme } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/pages/contracts/api';
import type { AppNotification } from '@/pages/contracts/types';

const { Text, Paragraph } = Typography;

export default function NotificationBell() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const { formatDateTime } = useDateFormat();
  const [items, setItems] = useState<AppNotification[]>([]);

  const load = useCallback(() => {
    fetchNotifications()
      .then(setItems)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 5 * 60 * 1000);
    const onFocus = () => load();
    window.addEventListener('focus', onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', onFocus);
    };
  }, [load]);

  const openItem = async (item: AppNotification) => {
    try {
      await markNotificationRead(item.id);
    } catch {
      /* still navigate */
    }
    setItems((current) => current.filter((row) => row.id !== item.id));
    if (item.link) navigate(item.link);
  };

  const markAll = async () => {
    await markAllNotificationsRead();
    setItems([]);
  };

  const visible = items.slice(0, 10);

  return (
    <Dropdown
      trigger={['click']}
      dropdownRender={() => (
        <div
          style={{
            width: 360,
            background: token.colorBgContainer,
            border: `1px solid ${token.colorBorder}`,
            borderRadius: token.borderRadiusLG,
            boxShadow: token.boxShadowSecondary,
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '10px 12px',
              borderBottom: `1px solid ${token.colorBorder}`,
            }}
          >
            <Text style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>Notifications</Text>
            <Button type="link" size="small" disabled={items.length === 0} onClick={markAll}>
              Mark all read
            </Button>
          </div>
          {visible.length === 0 ? (
            <div style={{ padding: 16 }}>
              <Text type="secondary">No unread notifications</Text>
            </div>
          ) : (
            visible.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => openItem(item)}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  background: 'transparent',
                  border: 'none',
                  borderBottom: `1px solid ${token.colorBorderSecondary}`,
                  padding: '10px 12px',
                  cursor: 'pointer',
                }}
              >
                <Text strong>{item.title}</Text>
                <Paragraph
                  type="secondary"
                  ellipsis={{ rows: 2 }}
                  style={{ marginBottom: 4 }}
                >
                  {item.message}
                </Paragraph>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {formatDateTime(item.createdAt)}
                </Text>
              </button>
            ))
          )}
        </div>
      )}
    >
      <Badge count={items.length} size="small" offset={[-2, 4]}>
        <Button
          type="text"
          icon={<BellOutlined />}
          aria-label="Notifications"
          style={{ color: 'rgba(255,255,255,0.85)' }}
        />
      </Badge>
    </Dropdown>
  );
}
