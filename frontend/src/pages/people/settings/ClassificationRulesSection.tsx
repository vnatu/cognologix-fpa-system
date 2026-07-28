import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Collapse,
  Input,
  Modal,
  Skeleton,
  Space,
  Tag,
  notification,
} from 'antd';
import {
  DownloadOutlined,
  ExportOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  addClassificationConfig,
  deleteClassificationConfig,
  downloadClassificationImportSample,
  exportClassificationConfig,
  fetchClassificationConfig,
  importClassificationConfig,
} from '../api';
import { CLASSIFICATION_CONFIG_LABELS } from '../constants';
import type {
  ClassificationConfigEntry,
  ClassificationConfigMap,
  ClassificationConfigType,
} from '../types';
import { AdminGate, useIsAdmin } from '@/components/AdminGate';
import SimpleExcelImportModal from '@/components/SimpleExcelImportModal';

const CONFIG_TYPES: ClassificationConfigType[] = [
  'DELIVERY_PU',
  'MANAGEMENT_BU',
  'LEADERSHIP_BU',
];

export default function ClassificationRulesSection() {
  const isAdmin = useIsAdmin();
  const [config, setConfig] = useState<ClassificationConfigMap>({});
  const [loading, setLoading] = useState(true);
  const [newValues, setNewValues] = useState<Record<string, string>>({});
  const [adding, setAdding] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] =
    useState<ClassificationConfigEntry | null>(null);
  const [importOpen, setImportOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setConfig(await fetchClassificationConfig());
    } catch {
      notification.error({ message: 'Failed to load classification rules' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleAdd = async (configType: ClassificationConfigType) => {
    const value = (newValues[configType] ?? '').trim();
    if (!value) return;
    setAdding(configType);
    try {
      await addClassificationConfig({ configType, value });
      notification.success({ message: 'Value added' });
      setNewValues((prev) => ({ ...prev, [configType]: '' }));
      load();
    } catch {
      notification.error({ message: 'Failed to add value' });
    } finally {
      setAdding(null);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteClassificationConfig(deleteTarget.id);
      notification.success({ message: 'Value removed' });
      setDeleteTarget(null);
      load();
    } catch {
      notification.error({ message: 'Failed to remove value' });
    }
  };

  if (loading) return <Skeleton active paragraph={{ rows: 6 }} />;

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Button
          icon={<ExportOutlined />}
          onClick={() => {
            exportClassificationConfig().catch(() =>
              notification.error({ message: 'Failed to export classification config' }),
            );
          }}
        >
          Export
        </Button>
        <Button
          icon={<DownloadOutlined />}
          onClick={() => {
            downloadClassificationImportSample().catch(() =>
              notification.error({ message: 'Failed to download sample file' }),
            );
          }}
        >
          Download Sample File
        </Button>
        <AdminGate>
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Import
          </Button>
        </AdminGate>
      </Space>

      <Collapse
        items={CONFIG_TYPES.map((type) => ({
          key: type,
          label: CLASSIFICATION_CONFIG_LABELS[type],
          children: (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Space wrap>
                {(config[type] ?? []).map((entry) => (
                  <Tag
                    key={entry.id}
                    closable={isAdmin}
                    onClose={(e) => {
                      e.preventDefault();
                      setDeleteTarget(entry);
                    }}
                  >
                    {entry.value}
                  </Tag>
                ))}
              </Space>
              {isAdmin && (
                <Space.Compact style={{ width: '100%', maxWidth: 400 }}>
                  <Input
                    placeholder="Add value"
                    value={newValues[type] ?? ''}
                    onChange={(e) =>
                      setNewValues((prev) => ({
                        ...prev,
                        [type]: e.target.value,
                      }))
                    }
                    onPressEnter={() => handleAdd(type)}
                  />
                  <Button
                    icon={<PlusOutlined />}
                    loading={adding === type}
                    onClick={() => handleAdd(type)}
                  >
                    Add
                  </Button>
                </Space.Compact>
              )}
            </Space>
          ),
        }))}
      />

      <Modal
        title="Remove classification value?"
        open={!!deleteTarget}
        onCancel={() => setDeleteTarget(null)}
        onOk={handleDelete}
        okText="Continue"
      >
        Removing this value will affect how employees are classified in future
        Master builds. Continue?
      </Modal>

      <SimpleExcelImportModal
        open={importOpen}
        title="Import Classification Config"
        onClose={() => setImportOpen(false)}
        onImported={load}
        importFile={importClassificationConfig}
      />
    </>
  );
}
