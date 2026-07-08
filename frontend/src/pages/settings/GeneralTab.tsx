import { useState } from 'react';
import { Table, Button, Modal, Form, Input, Tag, Space, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { HEADING_FONT } from '@/theme/antdTheme';

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
