import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import * as api from '../api';
import type { CustomerSummary, ProjectCode } from '../types';
import ProjectCodesSection from '../ProjectCodesSection';

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

const MOCK_CUSTOMERS: CustomerSummary[] = [
  { id: 'uuid-1', customerCode: 'ICERTI', customerName: 'Icertis', lifecycleStatus: 'ACTIVE' },
];

const MOCK_CODES: ProjectCode[] = [
  { id: 'code-1', projectCode: 'ICERTI-CX-2026', description: 'CX platform' },
  { id: 'code-2', projectCode: 'ICERTI-DATA-2026' },
];

const mockFetchCustomers = vi.mocked(api.fetchCustomers);
const mockFetchCodes = vi.mocked(api.fetchProjectCodes);
const mockAddCode = vi.mocked(api.addProjectCode);
const mockDeleteCode = vi.mocked(api.deleteProjectCode);

const noop = () => {};

beforeEach(() => {
  vi.clearAllMocks();
  mockFetchCustomers.mockResolvedValue(MOCK_CUSTOMERS);
  mockFetchCodes.mockResolvedValue(MOCK_CODES);
  mockAddCode.mockResolvedValue(MOCK_CODES[0]);
  mockDeleteCode.mockResolvedValue(undefined);
});

const waitForCodes = () => waitFor(() => screen.getByText('ICERTI-CX-2026'));

describe('ProjectCodesSection', () => {
  it('shows empty state when no customer is selected', async () => {
    render(<ProjectCodesSection selectedCustomerId={null} onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(screen.getByText(/select a customer to manage/i)).toBeInTheDocument(),
    );
  });

  it('loads and renders project codes for selected customer', async () => {
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() => expect(mockFetchCodes).toHaveBeenCalledWith('uuid-1'));
    await waitForCodes();
    expect(screen.getByText('CX platform')).toBeInTheDocument();
    expect(screen.getByText('ICERTI-DATA-2026')).toBeInTheDocument();
  });

  it('shows dash for project codes without a description', async () => {
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCodes();
    const rows = screen.getAllByRole('row');
    // row index 2 = second data row (code-2 has no description)
    expect(rows[2].textContent).toContain('—');
  });

  it('shows empty state when customer has no project codes', async () => {
    mockFetchCodes.mockResolvedValue([]);
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(screen.getByText(/no project codes yet/i)).toBeInTheDocument(),
    );
  });

  it('"Add Project Code" button is disabled when no customer is selected', async () => {
    render(<ProjectCodesSection selectedCustomerId={null} onSelectCustomer={noop} />);
    const btn = await waitFor(() =>
      screen.getByRole('button', { name: /add project code/i }),
    );
    expect(btn).toBeDisabled();
  });

  it('opens Add Project Code modal when button is clicked', async () => {
    const user = userEvent.setup();
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCodes();

    await user.click(screen.getByRole('button', { name: /add project code/i }));

    // Modal renders into document.body — find by dialog role
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Add Project Code')).toBeInTheDocument();
  });

  it('validates required project code field before submitting', async () => {
    const user = userEvent.setup();
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCodes();

    await user.click(screen.getByRole('button', { name: /add project code/i }));
    const dialog = await screen.findByRole('dialog');

    // Submit with empty form
    await user.click(within(dialog).getByRole('button', { name: /^add$/i }));

    await waitFor(() =>
      expect(screen.getByText('Project code is required')).toBeInTheDocument(),
    );
    expect(mockAddCode).not.toHaveBeenCalled();
  });

  it('calls addProjectCode with correct payload and reloads list', async () => {
    const user = userEvent.setup();
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCodes();

    await user.click(screen.getByRole('button', { name: /add project code/i }));
    const dialog = await screen.findByRole('dialog');

    await user.type(
      within(dialog).getByPlaceholderText(/ICERTI-CX-2026/i),
      'ICERTI-NEW-2026',
    );
    await user.type(
      within(dialog).getByPlaceholderText('Brief description'),
      'New initiative',
    );

    await user.click(within(dialog).getByRole('button', { name: /^add$/i }));

    await waitFor(() =>
      expect(mockAddCode).toHaveBeenCalledWith('uuid-1', {
        projectCode: 'ICERTI-NEW-2026',
        description: 'New initiative',
      }),
    );
    // Should reload after adding
    expect(mockFetchCodes).toHaveBeenCalledTimes(2);
  });

  it('calls deleteProjectCode after Popconfirm confirmation', async () => {
    const user = userEvent.setup();
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCodes();

    // Find the delete icon button in the first data row
    const deleteIcon = document.querySelector('.anticon-delete');
    expect(deleteIcon).not.toBeNull();
    const deleteBtn = deleteIcon!.closest('button')!;
    await user.click(deleteBtn);

    // Popconfirm floats into body
    await waitFor(() => screen.getByText('Remove this project code?'));
    const removeBtn = screen.getAllByRole('button', { name: /remove/i }).find(
      (b) => !b.closest('.ant-table'), // pick the popconfirm confirm btn, not any table btn
    );
    await user.click(removeBtn!);

    await waitFor(() =>
      expect(mockDeleteCode).toHaveBeenCalledWith('uuid-1', 'code-1'),
    );
    expect(mockFetchCodes).toHaveBeenCalledTimes(2);
  });

  it('shows error notification when project codes fail to load', async () => {
    mockFetchCodes.mockRejectedValue(new Error('Server error'));
    const { notification } = await import('antd');
    render(<ProjectCodesSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(notification.error).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Failed to load project codes' }),
      ),
    );
  });

  it('renders the customer selector combobox', async () => {
    render(<ProjectCodesSection selectedCustomerId={null} onSelectCustomer={noop} />);
    await waitFor(() => expect(screen.getByRole('combobox')).toBeInTheDocument());
  });
});
