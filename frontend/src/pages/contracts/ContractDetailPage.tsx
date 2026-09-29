import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Collapse,
  Descriptions,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
  Input,
  notification,
  theme,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate, useIsAdmin } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { formatCurrency } from '@/utils/formatDate';
import {
  addContractVersion,
  apiError,
  downloadContractDocument,
  fetchContract,
  updateVersionStatus,
} from './api';
import ContractFormModal from './ContractFormModal';
import type { ContractDetail, ContractStatus, VersionResponse, VersionStatus } from './types';

const { Title, Text, Paragraph } = Typography;

const STATUS_COLOR: Record<ContractStatus, string> = {
  DRAFT: 'default',
  ACTIVE: 'success',
  EXPIRED: 'warning',
  TERMINATED: 'error',
};

const VERSION_COLOR: Record<VersionStatus, string> = {
  DRAFT: 'default',
  UNDER_REVIEW: 'processing',
  SIGNED: 'success',
  SUPERSEDED: 'default',
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function ContractDetailPage() {
  const { id } = useParams();
  const { token } = theme.useToken();
  const { formatDate, formatDateTime } = useDateFormat();
  const isAdmin = useIsAdmin();
  const [loading, setLoading] = useState(true);
  const [contract, setContract] = useState<ContractDetail | null>(null);
  const [editing, setEditing] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);
  const [savingVersion, setSavingVersion] = useState(false);
  const [versionLabel, setVersionLabel] = useState('');
  const [notes, setNotes] = useState('');
  const [versionStatus, setVersionStatus] = useState<VersionStatus>('DRAFT');
  const [primaryFile, setPrimaryFile] = useState<File | null>(null);
  const [supportingFiles, setSupportingFiles] = useState<File[]>([]);

  const load = () => {
    if (!id) return;
    setLoading(true);
    fetchContract(id)
      .then(setContract)
      .catch((error) => notification.error({ message: apiError(error, 'Failed to load contract') }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (loading) {
    return (
      <div style={{ padding: 28 }}>
        <Skeleton active />
      </div>
    );
  }

  if (!contract) {
    return (
      <div style={{ padding: 28 }}>
        <Title level={4} style={{ fontFamily: HEADING_FONT }}>
          Contract not found
        </Title>
      </div>
    );
  }

  const daysColor =
    contract.evergreen || contract.daysRemaining == null
      ? token.colorTextDescription
      : contract.daysRemaining < 30
        ? token.colorError
        : contract.daysRemaining < 60
          ? token.colorWarning
          : token.colorSuccess;

  const submitVersion = async () => {
    if (!versionLabel.trim()) {
      notification.error({ message: 'Version label is required' });
      return;
    }
    if (!primaryFile) {
      notification.error({ message: 'A primary PDF or Word document is required' });
      return;
    }
    setSavingVersion(true);
    try {
      await addContractVersion({
        contractId: contract.id,
        versionLabel: versionLabel.trim(),
        notes,
        status: versionStatus,
        primaryFile,
        supportingFiles,
      });
      notification.success({ message: 'Version added' });
      setVersionOpen(false);
      setVersionLabel('');
      setNotes('');
      setVersionStatus('DRAFT');
      setPrimaryFile(null);
      setSupportingFiles([]);
      load();
    } catch (error) {
      notification.error({ message: apiError(error, 'Failed to add version') });
    } finally {
      setSavingVersion(false);
    }
  };

  const changeStatus = async (version: VersionResponse, status: VersionStatus) => {
    try {
      await updateVersionStatus(contract.id, version.id, status);
      notification.success({ message: 'Version status updated' });
      load();
    } catch (error) {
      notification.error({ message: apiError(error, 'Failed to update status') });
    }
  };

  const upcomingColumns: ColumnsType<ContractDetail['upcomingNotifications'][number]> = [
    {
      title: 'Date',
      dataIndex: 'notificationDate',
      render: (value: string) => formatDate(value),
    },
    { title: 'Days before expiry', dataIndex: 'daysBeforeExpiry' },
  ];

  const historyColumns: ColumnsType<ContractDetail['notificationHistory'][number]> = [
    { title: 'Channel', dataIndex: 'notificationType', width: 120 },
    { title: 'Days before expiry', dataIndex: 'daysBeforeExpiry', width: 160 },
    {
      title: 'Sent',
      dataIndex: 'sentAt',
      width: 180,
      render: (value: string) => formatDateTime(value),
    },
    { title: 'Recipients', dataIndex: 'recipients' },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Space direction="vertical" size={4} style={{ marginBottom: 16 }}>
        <Text type="secondary">{contract.contractNumber}</Text>
        <Title level={3} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
          {contract.title}
        </Title>
        <Space wrap>
          <Text>{contract.partyDisplayName}</Text>
          <Tag>{contract.contractTypeName}</Tag>
          <Tag>{contract.paperType === 'THIRD_PARTY' ? 'Third Party' : 'Own'}</Tag>
          <Tag color={STATUS_COLOR[contract.status]}>{contract.status}</Tag>
          <Text type="secondary">Owner: {contract.ownerName}</Text>
          <Text style={{ color: daysColor, fontWeight: 600 }}>
            {contract.evergreen
              ? 'Evergreen'
              : contract.daysRemaining == null
                ? ''
                : `${contract.daysRemaining} days remaining`}
          </Text>
        </Space>
      </Space>

      <Tabs
        items={[
          {
            key: 'overview',
            label: 'Overview',
            children: (
              <>
                <AdminGate fallback="hide">
                  <Button style={{ marginBottom: 16 }} onClick={() => setEditing(true)}>
                    Edit
                  </Button>
                </AdminGate>
                <Descriptions bordered column={2} size="small">
                  <Descriptions.Item label="Contract #">{contract.contractNumber}</Descriptions.Item>
                  <Descriptions.Item label="Type">{contract.contractTypeName}</Descriptions.Item>
                  <Descriptions.Item label="Paper type">
                    {contract.paperType === 'THIRD_PARTY' ? 'Third Party' : 'Own'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Status">{contract.status}</Descriptions.Item>
                  <Descriptions.Item label="Party">{contract.partyDisplayName}</Descriptions.Item>
                  <Descriptions.Item label="Owner">{contract.ownerName}</Descriptions.Item>
                  <Descriptions.Item label="Effective">{formatDate(contract.effectiveDate)}</Descriptions.Item>
                  <Descriptions.Item label="Expiry">
                    {contract.evergreen ? 'Evergreen' : formatDate(contract.expiryDate)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Parent">
                    {contract.parentContractNumber
                      ? `${contract.parentContractNumber} — ${contract.parentTitle}`
                      : '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Value">
                    {contract.contractValue == null
                      ? '—'
                      : `${contract.billingCurrency ?? ''} ${formatCurrency(contract.contractValue)}`.trim()}
                  </Descriptions.Item>
                  <Descriptions.Item label="Payment terms">{contract.paymentTerms || '—'}</Descriptions.Item>
                  <Descriptions.Item label="Reminder override">
                    {contract.reminderDaysOverride?.join(', ') || 'System default'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Description" span={2}>
                    {contract.description || '—'}
                  </Descriptions.Item>
                </Descriptions>
              </>
            ),
          },
          {
            key: 'versions',
            label: 'Versions',
            children: (
              <>
                <AdminGate fallback="hide">
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    style={{ marginBottom: 16 }}
                    onClick={() => setVersionOpen(true)}
                  >
                    Add Version
                  </Button>
                </AdminGate>
                <Collapse
                  items={contract.versions.map((version) => ({
                    key: version.id,
                    label: (
                      <Space wrap>
                        <Text strong>{version.versionLabel}</Text>
                        <Tag color={VERSION_COLOR[version.status]}>{version.status}</Tag>
                        <Text type="secondary">{formatDateTime(version.uploadedAt)}</Text>
                        <Text type="secondary">{version.uploadedBy}</Text>
                      </Space>
                    ),
                    children: (
                      <Space direction="vertical" style={{ width: '100%' }}>
                        {version.notes ? <Paragraph>{version.notes}</Paragraph> : null}
                        {isAdmin && version.status !== 'SUPERSEDED' ? (
                          <Select
                            value={version.status}
                            style={{ width: 180 }}
                            options={(['DRAFT', 'UNDER_REVIEW', 'SIGNED'] as VersionStatus[]).map((value) => ({
                              value,
                              label: value,
                            }))}
                            onChange={(value) => changeStatus(version, value)}
                          />
                        ) : null}
                        {version.documents
                          .filter((doc) => doc.documentType === 'PRIMARY')
                          .map((doc) => (
                            <Space key={doc.id}>
                              <Text strong>Primary:</Text>
                              <Text>{doc.filename}</Text>
                              <Text type="secondary">{formatBytes(doc.fileSizeBytes)}</Text>
                              <Button
                                size="small"
                                onClick={() =>
                                  downloadContractDocument(contract.id, version.id, doc.id, doc.filename).catch(
                                    (error) =>
                                      notification.error({
                                        message: apiError(error, 'Download failed'),
                                      }),
                                  )
                                }
                              >
                                Download
                              </Button>
                            </Space>
                          ))}
                        {version.documents
                          .filter((doc) => doc.documentType === 'SUPPORTING')
                          .map((doc) => (
                            <Space key={doc.id}>
                              <Text>Supporting:</Text>
                              <Text>{doc.filename}</Text>
                              <Text type="secondary">{formatBytes(doc.fileSizeBytes)}</Text>
                              <Button
                                size="small"
                                onClick={() =>
                                  downloadContractDocument(contract.id, version.id, doc.id, doc.filename).catch(
                                    (error) =>
                                      notification.error({
                                        message: apiError(error, 'Download failed'),
                                      }),
                                  )
                                }
                              >
                                Download
                              </Button>
                            </Space>
                          ))}
                      </Space>
                    ),
                  }))}
                />
              </>
            ),
          },
          {
            key: 'notifications',
            label: 'Notifications',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                <div>
                  <Title level={5} style={{ fontFamily: HEADING_FONT }}>
                    Upcoming
                  </Title>
                  <Table
                    rowKey={(row) => `${row.notificationDate}-${row.daysBeforeExpiry}`}
                    columns={upcomingColumns}
                    dataSource={contract.upcomingNotifications}
                    pagination={false}
                    locale={{ emptyText: 'No upcoming reminders' }}
                  />
                </div>
                <div>
                  <Title level={5} style={{ fontFamily: HEADING_FONT }}>
                    History
                  </Title>
                  <Table
                    rowKey="id"
                    columns={historyColumns}
                    dataSource={contract.notificationHistory}
                    pagination={false}
                    locale={{ emptyText: 'No notifications sent yet' }}
                  />
                </div>
              </Space>
            ),
          },
        ]}
      />

      <ContractFormModal
        open={editing}
        contract={contract}
        onClose={() => setEditing(false)}
        onSaved={() => {
          setEditing(false);
          load();
        }}
      />

      <Modal
        open={versionOpen}
        title="Add version"
        onCancel={() => setVersionOpen(false)}
        onOk={submitVersion}
        confirmLoading={savingVersion}
        okText="Upload"
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Adding a new version will automatically supersede the current version."
        />
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input
            placeholder="Version label"
            value={versionLabel}
            onChange={(event) => setVersionLabel(event.target.value)}
          />
          <Input.TextArea
            placeholder="Notes"
            rows={3}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
          />
          <Select
            value={versionStatus}
            onChange={setVersionStatus}
            options={(['DRAFT', 'UNDER_REVIEW', 'SIGNED'] as VersionStatus[]).map((value) => ({
              value,
              label: value,
            }))}
          />
          <Upload
            accept=".pdf,.doc,.docx,application/pdf"
            maxCount={1}
            beforeUpload={(file) => {
              setPrimaryFile(file);
              return false;
            }}
            onRemove={() => setPrimaryFile(null)}
            fileList={
              primaryFile
                ? [{ uid: 'primary', name: primaryFile.name, status: 'done' }]
                : []
            }
          >
            <Button>Upload primary document (PDF or Word)</Button>
          </Upload>
          <Upload
            multiple
            beforeUpload={(file) => {
              setSupportingFiles((current) => [...current, file]);
              return false;
            }}
            onRemove={(file) =>
              setSupportingFiles((current) => current.filter((item) => item.name !== file.name))
            }
            fileList={supportingFiles.map((file) => ({
              uid: file.name,
              name: file.name,
              status: 'done' as const,
            }))}
          >
            <Button>Upload supporting documents</Button>
          </Upload>
        </Space>
      </Modal>
    </div>
  );
}
