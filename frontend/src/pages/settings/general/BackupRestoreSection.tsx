import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Modal,
  Space,
  Spin,
  Table,
  Typography,
  Upload,
  notification,
} from 'antd';
import { DownloadOutlined, InboxOutlined, WarningOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import {
  confirmSystemRestore,
  downloadSystemBackup,
  fetchBackupMeta,
  prepareSystemRestore,
  type RestoreConfirmResult,
  type RestoreDryRun,
} from '@/api/system';

const { Text, Paragraph } = Typography;
const { Dragger } = Upload;

export default function BackupRestoreSection() {
  const { formatDateTime } = useDateFormat();
  const [lastBackupAt, setLastBackupAt] = useState<string | null>(null);
  const [backingUp, setBackingUp] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [dryRun, setDryRun] = useState<RestoreDryRun | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [result, setResult] = useState<RestoreConfirmResult | null>(null);
  const [resultOpen, setResultOpen] = useState(false);

  const loadMeta = useCallback(async () => {
    try {
      const meta = await fetchBackupMeta();
      setLastBackupAt(meta.lastBackupAt || null);
    } catch {
      setLastBackupAt(null);
    }
  }, []);

  useEffect(() => {
    loadMeta();
  }, [loadMeta]);

  const handleBackup = async () => {
    setBackingUp(true);
    try {
      await downloadSystemBackup();
      notification.success({ message: 'Backup downloaded' });
      await loadMeta();
    } catch {
      notification.error({ message: 'Failed to generate backup' });
    } finally {
      setBackingUp(false);
    }
  };

  const handleUpload = async (file: File) => {
    try {
      const summary = await prepareSystemRestore(file);
      setDryRun(summary);
      setConfirmOpen(true);
    } catch {
      notification.error({ message: 'Failed to analyse backup ZIP' });
    }
    return false;
  };

  const handleConfirmRestore = async () => {
    if (!dryRun) return;
    setRestoring(true);
    try {
      const restoreResult = await confirmSystemRestore(dryRun.restoreToken);
      setResult(restoreResult);
      setConfirmOpen(false);
      setResultOpen(true);
      setFileList([]);
      setDryRun(null);
    } catch {
      notification.error({
        message: 'Restore failed',
        description: 'The dry-run token may have expired — upload the ZIP again.',
      });
    } finally {
      setRestoring(false);
    }
  };

  const countColumns: ColumnsType<{ key: string; type: string; count: number }> = [
    { title: 'Data type', dataIndex: 'type', key: 'type' },
    { title: 'Records', dataIndex: 'count', key: 'count', width: 120 },
  ];

  const countRows = dryRun
    ? Object.entries(dryRun.recordCounts).map(([type, count]) => ({
        key: type,
        type,
        count,
      }))
    : [];

  const resultRows = result
    ? Object.entries(result.recordsRestored).map(([type, count]) => ({
        key: type,
        type,
        count,
      }))
    : [];

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
        Backup &amp; Restore
      </div>
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Full system backup as a ZIP of Excel files. Restore replaces all data (Admin only).
      </Text>

      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Card size="small" title="Download backup">
          <Paragraph type="secondary" style={{ marginBottom: 12 }}>
            Download a complete system backup containing one Excel file per data type.
          </Paragraph>
          <Paragraph style={{ marginBottom: 16 }}>
            Last backup:{' '}
            <Text strong>
              {lastBackupAt ? formatDateTime(lastBackupAt) : 'Never'}
            </Text>
          </Paragraph>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            loading={backingUp}
            onClick={handleBackup}
            style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
          >
            Download Backup
          </Button>
        </Card>

        <Card
          size="small"
          title={
            <Space>
              <WarningOutlined style={{ color: '#cf1322' }} />
              Restore from backup
            </Space>
          }
        >
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="Restoring will permanently delete all existing data and replace it with the backup contents. This cannot be undone."
          />
          <Dragger
            accept=".zip"
            maxCount={1}
            fileList={fileList}
            beforeUpload={(file) => {
              setFileList([
                {
                  uid: file.name,
                  name: file.name,
                  status: 'done',
                },
              ]);
              void handleUpload(file);
              return false;
            }}
            onRemove={() => {
              setFileList([]);
              setDryRun(null);
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Click or drag a backup ZIP to upload</p>
            <p className="ant-upload-hint">Only .zip files produced by Download Backup</p>
          </Dragger>
        </Card>
      </Space>

      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
            Confirm full restore
          </span>
        }
        open={confirmOpen}
        onCancel={() => {
          if (!restoring) {
            setConfirmOpen(false);
            setDryRun(null);
          }
        }}
        width={640}
        footer={[
          <Button key="cancel" disabled={restoring} onClick={() => setConfirmOpen(false)}>
            Cancel
          </Button>,
          <Button
            key="confirm"
            type="primary"
            danger
            loading={restoring}
            onClick={handleConfirmRestore}
          >
            Confirm Restore
          </Button>,
        ]}
      >
        <Spin spinning={restoring}>
          {dryRun && (
            <>
              <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={dryRun.warning} />
              {dryRun.filesMissing.length > 0 && (
                <Paragraph type="secondary">
                  Optional/missing files (restore continues):{' '}
                  {dryRun.filesMissing.join(', ')}
                </Paragraph>
              )}
              <Table
                size="small"
                pagination={false}
                columns={countColumns}
                dataSource={countRows}
                scroll={{ y: 280 }}
              />
            </>
          )}
        </Spin>
      </Modal>

      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>Restore complete</span>
        }
        open={resultOpen}
        onCancel={() => {
          setResultOpen(false);
          window.location.reload();
        }}
        footer={[
          <Button
            key="done"
            type="primary"
            onClick={() => {
              setResultOpen(false);
              window.location.reload();
            }}
          >
            Done
          </Button>,
        ]}
        width={640}
      >
        {result && (
          <>
            <Paragraph>{result.message}</Paragraph>
            <Table
              size="small"
              pagination={false}
              columns={countColumns}
              dataSource={resultRows}
              scroll={{ y: 280 }}
              style={{ marginBottom: 16 }}
            />
            {result.errors.length > 0 && (
              <Alert
                type="error"
                showIcon
                message="Errors"
                description={
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {result.errors.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                }
              />
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
