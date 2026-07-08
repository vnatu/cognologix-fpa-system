import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import * as api from '../api';
import type { CustomerSummary, CustomerDetail } from '../types';
import CustomersSection from '../CustomersSection';

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
  {
    id: 'uuid-1',
    customerCode: 'ICERTI',
    customerName: 'Icertis',
    lifecycleStatus: 'ACTIVE',
    relationshipOwnerEmployeeId: 'EMP042',
  },
  {
    id: 'uuid-2',
    customerCode: 'CADENT',
    customerName: 'Cadent',
    lifecycleStatus: 'AT_RISK',
  },
];

const MOCK_DETAIL: CustomerDetail = {
  ...MOCK_CUSTOMERS[0],
  commercialTerms: { dsoDays: 45 },
  projectCodes: [],
};

const mockFetch = vi.mocked(api.fetchCustomers);
const mockCreate = vi.mocked(api.createCustomer);
const mockUpdate = vi.mocked(api.updateCustomer);
const mockFetchOne = vi.mocked(api.fetchCustomer);

const noop = () => {};

beforeEach(() => {
  vi.clearAllMocks();
  mockFetch.mockResolvedValue(MOCK_CUSTOMERS);
  mockFetchOne.mockResolvedValue(MOCK_DETAIL);
  mockCreate.mockResolvedValue(MOCK_CUSTOMERS[0]);
  mockUpdate.mockResolvedValue(MOCK_DETAIL);
});

describe('CustomersSection', () => {
  it('shows skeleton while loading then renders customer table', async () => {
    render(<CustomersSection onSelectCustomer={noop} />);
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Icertis')).toBeInTheDocument());
    expect(screen.getByText('ICERTI')).toBeInTheDocument();
    expect(screen.getByText('Cadent')).toBeInTheDocument();
  });

  it('shows lifecycle status as a colored tag', async () => {
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('AT RISK')).toBeInTheDocument();
  });

  it('shows relationship owner employee ID in the table', async () => {
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('EMP042'));
    expect(screen.getByText('EMP042')).toBeInTheDocument();
  });

  it('shows empty state when no customers are returned', async () => {
    mockFetch.mockResolvedValue([]);
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(screen.getByText(/No customers yet/i)).toBeInTheDocument(),
    );
  });

  it('shows error notification when customer fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'));
    const { notification } = await import('antd');
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(notification.error).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Failed to load customers' }),
      ),
    );
  });

  it('opens Add Customer modal when button is clicked', async () => {
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    await user.click(screen.getByRole('button', { name: /add customer/i }));

    // Modal renders into document.body — find by dialog role
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Add Customer')).toBeInTheDocument();
    // Customer Code is enabled for a new customer
    expect(within(dialog).getByPlaceholderText('e.g. ICERTI')).not.toBeDisabled();
  });

  it('validates required fields before submitting create form', async () => {
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    await user.click(screen.getByRole('button', { name: /add customer/i }));
    const dialog = await screen.findByRole('dialog');

    // Submit with empty form
    await user.click(within(dialog).getByRole('button', { name: /create customer/i }));

    await waitFor(() =>
      expect(screen.getByText('Customer code is required')).toBeInTheDocument(),
    );
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it('calls createCustomer with correct payload on successful create', async () => {
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    await user.click(screen.getByRole('button', { name: /add customer/i }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByPlaceholderText('e.g. ICERTI'), 'NEWCO');
    await user.type(within(dialog).getByPlaceholderText('e.g. Icertis'), 'New Corp');

    // Select lifecycle status
    const comboboxes = within(dialog).getAllByRole('combobox');
    const statusCombobox = comboboxes.find((el) =>
      el.closest('.ant-form-item')?.textContent?.includes('Lifecycle'),
    );
    if (statusCombobox) {
      await user.click(statusCombobox);
      await waitFor(() => screen.getByText('Active'));
      await user.click(screen.getByText('Active'));
    }

    await user.click(within(dialog).getByRole('button', { name: /create customer/i }));

    await waitFor(() =>
      expect(mockCreate).toHaveBeenCalledWith(
        expect.objectContaining({
          customerCode: 'NEWCO',
          customerName: 'New Corp',
          lifecycleStatus: 'ACTIVE',
        }),
      ),
    );
  });

  it('opens Edit modal pre-populated with customer data', async () => {
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    await user.click(editButtons[0]);

    await waitFor(() => expect(mockFetchOne).toHaveBeenCalledWith('uuid-1'));

    // Customer Code should be pre-filled and disabled for edit
    await waitFor(() => {
      expect(screen.getByDisplayValue('ICERTI')).toBeDisabled();
    });
  });

  it('calls updateCustomer when editing an existing customer', async () => {
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    await user.click(editButtons[0]);
    await waitFor(() => screen.getByDisplayValue('ICERTI'));

    const dialog = screen.getByRole('dialog');
    const nameInput = within(dialog).getByDisplayValue('Icertis');
    await user.clear(nameInput);
    await user.type(nameInput, 'Icertis Updated');

    await user.click(within(dialog).getByRole('button', { name: /save changes/i }));

    await waitFor(() =>
      expect(mockUpdate).toHaveBeenCalledWith(
        'uuid-1',
        expect.objectContaining({ customerName: 'Icertis Updated' }),
      ),
    );
  });

  it('calls onSelectCustomer when row is clicked', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<CustomersSection onSelectCustomer={onSelect} />);
    await waitFor(() => screen.getByText('Icertis'));

    await user.click(screen.getByText('Icertis'));

    expect(onSelect).toHaveBeenCalledWith('uuid-1');
  });

  it('shows no delete button anywhere — customers are deactivated via status', async () => {
    render(<CustomersSection onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('Icertis'));

    const deleteButtons = screen.queryAllByRole('button', { name: /delete/i });
    expect(deleteButtons).toHaveLength(0);
  });
});
