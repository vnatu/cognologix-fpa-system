import { useEffect, useState } from 'react';
import {
  Button,
  Form,
  Modal,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { updateDateFormat, fetchDateFormat } from '@/api/general';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  DATE_FORMAT_OPTIONS,
  formatDate,
  type DateFormatOption,
} from '@/utils/formatDate';

const { Text } = Typography;

interface Member {
  key: string;
  name: string;
  email: string;
  role: 'Admin';
}

const INITIAL_MEMBERS: Member[] = [
  { key: 'u1', name: 'Rohit Menon',    email: 'rohit@cognologix.com',  role: 'Admin' },
  { key: 'u2', name: 'Priya Nair',     email: 'priya@cognologix.com',  role: 'Admin' },
  { key: 'u3', name: 'Karan Shah',     email: 'karan@cognologix.com',  role: 'Admin' },
  { key: 'u4', name: 'Vikram Rao',     email: 'vikram@cognologix.com', role: 'Admin' },
  { key: 'u5', name: 'Anita Krishnan', email: 'anita@cognologix.com',  role: 'Admin' },
  { key: 'u6', name: 'Deepa Iyer',     email: 'deepa@cognologix.com',  role: 'Admin' },
];

const COLUMNS: ColumnsType<Member> = [
  {
    title: 'Name',
    dataIndex: 'name',
    key: 'name',
    render: (v: string) => <Text strong style={{ color: '#232323' }}>{v}</Text>,
  },
  {
    title: 'Email',
    dataIndex: 'email',
    key: 'email',
    render: (v: string) => <Text style={{ color: '#555555' }}>{v}</Text>,
  },
  {
    title: 'Role',
    dataIndex: 'role',
    key: 'role',
    render: () => <Tag color="error">Admin</Tag>,
  },
];

function DateFormatSettings() {
  const { format, setFormat } = useDateFormat();
  const [selected, setSelected] = useState<DateFormatOption>(format);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const current = await fetchDateFormat();
        setSelected(current);
        setFormat(current);
      } catch {
        notification.error({ message: 'Failed to load date format' });
      } finally {
        setLoading(false);
      }
    })();
  }, [setFormat]);

  useEffect(() => {
    setSelected(format);
  }, [format]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const saved = await updateDateFormat(selected);
      setFormat(saved);
      notification.success({ message: 'Date format saved' });
    } catch {
      notification.error({ message: 'Failed to save date format' });
    } finally {
      setSaving(false);
    }
  };

  const preview = formatDate(dayjs().toDate(), selected);

  return (
    <div style={{ marginBottom: 40 }}>
      <div
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 17,
          color: '#232323',
          marginBottom: 4,
        }}
      >
        Date Format
      </div>
      <div style={{ fontSize: 13, color: '#888888', marginBottom: 16 }}>
        Choose how dates are displayed across the application.
      </div>
      <Space direction="vertical" size="middle" style={{ maxWidth: 360 }}>
        <Select
          style={{ width: '100%' }}
          value={selected}
          loading={loading}
          options={DATE_FORMAT_OPTIONS.map((o) => ({ label: o, value: o }))}
          onChange={setSelected}
        />
        <Text type="secondary">
          Preview: <Text strong>{preview}</Text>
        </Text>
        <Button
          type="primary"
          loading={saving}
          onClick={handleSave}
          disabled={loading}
          style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
        >
          Save
        </Button>
      </Space>
    </div>
  );
}

export default function GeneralTab() {
  const [members, setMembers] = useState<Member[]>(INITIAL_MEMBERS);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const handleInvite = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    // TODO: wire to POST /api/users once the endpoint exists
    setMembers((prev) => [
      ...prev,
      { key: String(Date.now()), name: values.name, email: values.email, role: 'Admin' },
    ]);
    setSubmitting(false);
    setOpen(false);
    form.resetFields();
  };

  return (
    <>
      <DateFormatSettings />

      <div
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          justifyContent: 'space-between',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <div>
          <div
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 17,
              color: '#232323',
            }}
          >
            Members
          </div>
          <div style={{ fontSize: 13, color: '#888888', marginTop: 3 }}>
            People with access to this workspace. Everyone here is an administrator.
          </div>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setOpen(true)}
          style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
        >
          Add user
        </Button>
      </div>

      <Table<Member>
        columns={COLUMNS}
        dataSource={members}
        pagination={false}
        size="middle"
        bordered={false}
        style={{ background: '#ffffff', borderRadius: 8, border: '1px solid #d8d8d8' }}
      />

      <Modal
        title={
          <div>
            <div
              style={{
                fontFamily: HEADING_FONT,
                fontWeight: 700,
                fontSize: 18,
                color: '#232323',
              }}
            >
              Add user
            </div>
            <div style={{ fontSize: 13, color: '#888888', fontWeight: 400, marginTop: 4 }}>
              They'll be invited by email as an administrator.
            </div>
          </div>
        }
        open={open}
        onCancel={() => { setOpen(false); form.resetFields(); }}
        onOk={handleInvite}
        okText="Send invite"
        cancelText="Cancel"
        confirmLoading={submitting}
        okButtonProps={{ style: { fontFamily: HEADING_FONT, fontWeight: 600 } }}
        cancelButtonProps={{ style: { fontFamily: HEADING_FONT, fontWeight: 600 } }}
        width={440}
      >
        <Form form={form} layout="vertical" requiredMark={false} style={{ marginTop: 8 }}>
          <Form.Item
            label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Name</span>}
            name="name"
            rules={[{ required: true, message: 'Enter a name.' }]}
          >
            <Input placeholder="Full name" autoComplete="off" />
          </Form.Item>
          <Form.Item
            label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Email</span>}
            name="email"
            rules={[
              { required: true, message: 'Enter an email.' },
              { type: 'email', message: 'Enter a valid email address.' },
            ]}
          >
            <Input placeholder="name@cognologix.com" type="email" autoComplete="off" />
          </Form.Item>
          <Space align="center">
            <Text style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Role</Text>
            <Tag color="error">Admin</Tag>
            <Text type="secondary" style={{ fontSize: 12 }}>Only role available today</Text>
          </Space>
        </Form>
      </Modal>
    </>
  );
}
