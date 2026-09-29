import { useEffect, useState } from 'react';
import {
  Button,
  Checkbox,
  Col,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Switch,
  notification,
} from 'antd';
import dayjs from 'dayjs';
import { fetchCustomers } from '@/pages/customers/api';
import { fetchMe, fetchUsers } from '@/api/users';
import { apiError, createContract, fetchContracts, fetchContractTypes, updateContract } from './api';
import type { ContractDetail, ContractPayload, ContractStatus, ContractType, PaperType } from './types';

interface Props {
  open: boolean;
  contract?: ContractDetail | null;
  onClose: () => void;
  onSaved: (id: string) => void;
}

export default function ContractFormModal({ open, contract, onClose, onSaved }: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [types, setTypes] = useState<ContractType[]>([]);
  const [customers, setCustomers] = useState<{ id: string; label: string }[]>([]);
  const [parents, setParents] = useState<{ id: string; label: string }[]>([]);
  const [owners, setOwners] = useState<{ id: string; label: string }[]>([]);
  const notInSystem = Form.useWatch('notInSystem', form);
  const evergreen = Form.useWatch('evergreen', form);

  useEffect(() => {
    if (!open) return;
    fetchContractTypes().then(setTypes).catch(() => undefined);
    fetchCustomers(false)
      .then((rows) =>
        setCustomers(rows.map((row) => ({ id: row.id, label: `${row.customerCode} — ${row.customerName}` }))),
      )
      .catch(() => undefined);
    fetchContracts({ page: 0, size: 200 })
      .then((page) =>
        setParents(
          page.content
            .filter((row) => row.id !== contract?.id)
            .map((row) => ({ id: row.id, label: `${row.contractNumber} — ${row.title}` })),
        ),
      )
      .catch(() => undefined);
    Promise.all([fetchMe(), fetchUsers().catch(() => [])]).then(([me, users]) => {
      const options = (users.length ? users : [me])
        .filter((user) => user.active)
        .map((user) => ({ id: user.id, label: `${user.fullName} (${user.email})` }));
      setOwners(options);
      if (!contract) {
        form.setFieldsValue({
          ownerUserId: me.id,
          status: 'ACTIVE',
          paperType: 'THIRD_PARTY',
          evergreen: false,
          notInSystem: false,
        });
      }
    });
  }, [open, contract, form]);

  useEffect(() => {
    if (!open) return;
    if (!contract) {
      form.resetFields();
      return;
    }
    form.setFieldsValue({
      title: contract.title,
      contractTypeId: contract.contractTypeId,
      paperType: contract.paperType,
      notInSystem: !contract.customerId,
      customerId: contract.customerId ?? undefined,
      partyName: contract.partyName ?? undefined,
      effectiveDate: contract.effectiveDate ? dayjs(contract.effectiveDate) : undefined,
      expiryDate: contract.expiryDate ? dayjs(contract.expiryDate) : undefined,
      evergreen: contract.evergreen,
      status: contract.status,
      parentContractId: contract.parentContractId ?? undefined,
      contractValue: contract.contractValue ?? undefined,
      billingCurrency: contract.billingCurrency ?? undefined,
      paymentTerms: contract.paymentTerms ?? undefined,
      reminderDays: contract.reminderDaysOverride?.join(',') ?? '',
      description: contract.description ?? undefined,
      ownerUserId: contract.ownerUserId,
    });
  }, [open, contract, form]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const reminder = String(values.reminderDays ?? '')
        .split(',')
        .map((part: string) => part.trim())
        .filter(Boolean)
        .map((part: string) => Number(part));
      const payload: ContractPayload = {
        title: values.title,
        contractTypeId: values.contractTypeId,
        paperType: values.paperType as PaperType,
        customerId: values.notInSystem ? null : values.customerId,
        partyName: values.notInSystem ? values.partyName : null,
        effectiveDate: values.effectiveDate ? values.effectiveDate.format('YYYY-MM-DD') : null,
        expiryDate: values.evergreen || !values.expiryDate ? null : values.expiryDate.format('YYYY-MM-DD'),
        evergreen: Boolean(values.evergreen),
        status: values.status as ContractStatus,
        parentContractId: values.parentContractId ?? null,
        contractValue: values.contractValue ?? null,
        billingCurrency: values.billingCurrency ?? null,
        paymentTerms: values.paymentTerms ?? null,
        reminderDaysOverride: reminder.length ? reminder : null,
        description: values.description ?? null,
        ownerUserId: values.ownerUserId,
      };
      setSaving(true);
      const saved = contract
        ? await updateContract(contract.id, payload)
        : await createContract(payload);
      notification.success({ message: contract ? 'Contract updated' : 'Contract created' });
      onSaved(saved.id);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      notification.error({ message: apiError(error, 'Failed to save contract') });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={contract ? 'Edit contract' : 'Add contract'}
      onCancel={onClose}
      onOk={submit}
      confirmLoading={saving}
      width={760}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="title" label="Title" rules={[{ required: true, message: 'Title is required' }]}>
          <Input />
        </Form.Item>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="contractTypeId" label="Contract type" rules={[{ required: true }]}>
              <Select options={types.map((type) => ({ value: type.id, label: type.displayName }))} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="paperType" label="Paper type" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'THIRD_PARTY', label: 'Third Party' },
                  { value: 'OWN', label: 'Own' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="notInSystem" label="Client not in FPA" valuePropName="checked">
          <Switch />
        </Form.Item>
        {notInSystem ? (
          <Form.Item name="partyName" label="Party name" rules={[{ required: true, message: 'Party name is required' }]}>
            <Input />
          </Form.Item>
        ) : (
          <Form.Item name="customerId" label="Client" rules={[{ required: true, message: 'Client is required' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={customers.map((customer) => ({ value: customer.id, label: customer.label }))}
            />
          </Form.Item>
        )}
        <Row gutter={12}>
          <Col span={8}>
            <Form.Item name="effectiveDate" label="Effective date">
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="expiryDate" label="Expiry date">
              <DatePicker style={{ width: '100%' }} disabled={evergreen} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="evergreen" valuePropName="checked" label=" ">
              <Checkbox>Evergreen</Checkbox>
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="status" label="Status" rules={[{ required: true }]}>
              <Select
                options={['DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED'].map((value) => ({
                  value,
                  label: value,
                }))}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="parentContractId" label="Parent contract">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                options={parents.map((parent) => ({ value: parent.id, label: parent.label }))}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={8}>
            <Form.Item name="contractValue" label="Contract value">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="billingCurrency" label="Billing currency">
              <Select
                allowClear
                options={[
                  { value: 'INR', label: 'INR' },
                  { value: 'USD', label: 'USD' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="paymentTerms" label="Payment terms">
              <Input />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="reminderDays" label="Reminder days override" extra="Comma-separated, for example 180,90,30,7. Leave blank to use the system default.">
          <Input />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item name="ownerUserId" label="Owner" rules={[{ required: true }]}>
          <Select options={owners.map((owner) => ({ value: owner.id, label: owner.label }))} />
        </Form.Item>
        <Button htmlType="submit" style={{ display: 'none' }} />
      </Form>
    </Modal>
  );
}
