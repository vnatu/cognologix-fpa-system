import { Modal } from 'antd';
import { discardRun } from './api';
import type { ReconRun } from './types';

export function confirmDiscardRun(run: Pick<ReconRun, 'id' | 'runNumber'>): Promise<void> {
  return new Promise((resolve, reject) => {
    Modal.confirm({
      title: `Discard run ${run.runNumber}?`,
      content:
        'This will permanently delete this run and its transactions. Learned mappings confirmed during this run will be kept.',
      okText: 'Discard',
      okButtonProps: { danger: true },
      onOk: () =>
        discardRun(run.id)
          .then(resolve)
          .catch(reject),
    });
  });
}
