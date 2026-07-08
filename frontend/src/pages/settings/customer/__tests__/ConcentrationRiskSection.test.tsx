import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import * as api from '../api';
import type { ConcentrationRiskConfig } from '../types';
import ConcentrationRiskSection from '../ConcentrationRiskSection';

vi.mock('../api');
vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>();
  return {
    ...actual,
    notification: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  };
});

const MOCK_CONFIG: ConcentrationRiskConfig = {
  id: 'cfg-1',
  singleClientThresholdPct: 30,
};

const mockFetch = vi.mocked(api.fetchConcentrationRiskConfig);
const mockUpdate = vi.mocked(api.updateConcentrationRiskConfig);

beforeEach(() => {
  vi.clearAllMocks();
  mockFetch.mockResolvedValue(MOCK_CONFIG);
  mockUpdate.mockResolvedValue({ ...MOCK_CONFIG, singleClientThresholdPct: 25 });
});

describe('ConcentrationRiskSection', () => {
  it('shows skeleton while loading', () => {
    render(<ConcentrationRiskSection />);
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
  });

  it('displays current threshold after loading', async () => {
    render(<ConcentrationRiskSection />);
    await waitFor(() =>
      expect(screen.getByText(/currently 30%/i)).toBeInTheDocument(),
    );
  });

  it('pre-fills the threshold input with the loaded value', async () => {
    render(<ConcentrationRiskSection />);
    // InputNumber with precision={2} renders the value as "30.00" (string)
    await waitFor(() =>
      expect(screen.getByRole('spinbutton')).toHaveValue('30.00'),
    );
  });

  it('calls updateConcentrationRiskConfig with the new value on save', async () => {
    const user = userEvent.setup();
    render(<ConcentrationRiskSection />);
    await waitFor(() => screen.getByRole('spinbutton'));

    const input = screen.getByRole('spinbutton');
    await user.tripleClick(input);
    await user.keyboard('{Backspace}25');

    await user.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(mockUpdate).toHaveBeenCalledWith(25),
    );
  });

  it('shows success notification after saving', async () => {
    const { notification } = await import('antd');
    render(<ConcentrationRiskSection />);
    await waitFor(() => screen.getByRole('spinbutton'));

    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(notification.success).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Threshold updated' }),
      ),
    );
  });

  it('shows error notification when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'));
    const { notification } = await import('antd');
    render(<ConcentrationRiskSection />);
    await waitFor(() =>
      expect(notification.error).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Failed to load concentration risk config',
        }),
      ),
    );
  });

  it('shows error notification when save fails', async () => {
    mockUpdate.mockRejectedValue(new Error('Server error'));
    const { notification } = await import('antd');
    render(<ConcentrationRiskSection />);
    await waitFor(() => screen.getByRole('spinbutton'));

    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(notification.error).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Failed to update threshold' }),
      ),
    );
  });

  it('shows Watch Groups section with backend-pending info alert', async () => {
    render(<ConcentrationRiskSection />);
    await waitFor(() =>
      expect(screen.getByText('Combined-Client Watch Groups')).toBeInTheDocument(),
    );
    expect(screen.getByText('Backend endpoints pending')).toBeInTheDocument();
  });

  it('shows description text about deferred calculation', async () => {
    render(<ConcentrationRiskSection />);
    await waitFor(() =>
      expect(
        screen.getByText(/Actual risk percentages are calculated once/i),
      ).toBeInTheDocument(),
    );
  });
});
