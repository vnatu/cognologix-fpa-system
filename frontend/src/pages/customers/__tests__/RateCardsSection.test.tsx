import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { DateFormatProvider } from '@/context/DateFormatContext';
import { AuthProvider } from '@/context/AuthContext';
import * as api from '../api';
import type { CustomerSummary, RateCard } from '../types';
import RateCardsSection from '../RateCardsSection';

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
  { id: 'uuid-2', customerCode: 'CADENT', customerName: 'Cadent', lifecycleStatus: 'AT_RISK' },
];

const ACTIVE_FLAT_CARD: RateCard = {
  id: 'card-1',
  name: 'FY2526 Standard',
  rateCardType: 'FLAT',
  currency: 'INR',
  effectiveFrom: '2026-01-01',
  lines: [{ id: 'line-1', rateAmount: 150000 }],
};

const TIERED_CARD: RateCard = {
  id: 'card-2',
  name: 'FY2526 Tiered',
  rateCardType: 'TIERED',
  currency: 'INR',
  effectiveFrom: '2025-01-01',
  effectiveTo: '2025-12-31',
  lines: [
    { id: 'line-2', jobLevel: 'L3', rateAmount: 120000 },
    { id: 'line-3', jobLevel: 'L4', rateAmount: 150000 },
  ],
};

const mockFetchCustomers = vi.mocked(api.fetchCustomers);
const mockFetchRateCards = vi.mocked(api.fetchRateCards);
const mockCreateRateCard = vi.mocked(api.createRateCard);

const noop = () => {};

function renderWithDateFormat(ui: React.ReactElement) {
  return render(
    <AuthProvider>
      <DateFormatProvider>{ui}</DateFormatProvider>
    </AuthProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.setItem('fpa_token', 'test-token');
  mockFetchCustomers.mockResolvedValue(MOCK_CUSTOMERS);
  mockFetchRateCards.mockResolvedValue([ACTIVE_FLAT_CARD, TIERED_CARD]);
  mockCreateRateCard.mockResolvedValue(ACTIVE_FLAT_CARD);
});

/** Wait until rate cards have loaded */
const waitForCards = () => waitFor(() => screen.getByText('FY2526 Standard'));

describe('RateCardsSection', () => {
  it('shows empty state when no customer is selected', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId={null} onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(screen.getByText(/select a customer to view/i)).toBeInTheDocument(),
    );
  });

  it('loads rate cards when a customer is pre-selected', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() => expect(mockFetchRateCards).toHaveBeenCalledWith('uuid-1'));
    await waitForCards();
    expect(screen.getByText('FY2526 Tiered')).toBeInTheDocument();
  });

  it('displays card names and effective dates', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();
    // Dates use configured format (default DD MMM YYYY)
    expect(screen.getByText(/01 Jan 2026/)).toBeInTheDocument();
    expect(screen.getByText(/01 Jan 2025/)).toBeInTheDocument();
  });

  it('shows Active tag for the card with no effectiveTo', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('renders flat rate card with rate column and currency in header', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();
    expect(screen.getAllByText(/150,000/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('INR').length).toBeGreaterThan(0);
    expect(screen.queryByRole('columnheader', { name: 'Currency' })).not.toBeInTheDocument();
  });

  it('renders tiered rate card with job level column', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();
    expect(screen.getByText('L3')).toBeInTheDocument();
    expect(screen.getByText('L4')).toBeInTheDocument();
    expect(screen.getByText('Job Level')).toBeInTheDocument();
  });

  it('renders tiered lines sorted by rate ascending', async () => {
    mockFetchRateCards.mockResolvedValue([
      {
        ...TIERED_CARD,
        lines: [
          { id: 'line-3', jobLevel: 'L4', rateAmount: 150000 },
          { id: 'line-2', jobLevel: 'L3', rateAmount: 120000 },
        ],
      },
    ]);
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() => screen.getByText('FY2526 Tiered'));

    const rows = screen.getAllByRole('row');
    const tieredRowText = rows.map((r) => r.textContent).join('\n');
    expect(tieredRowText.indexOf('L3')).toBeLessThan(tieredRowText.indexOf('L4'));
    expect(tieredRowText.indexOf('120,000')).toBeLessThan(tieredRowText.indexOf('150,000'));
  });

  it('"New Rate Card" button is disabled when no customer is selected', async () => {
    renderWithDateFormat(<RateCardsSection selectedCustomerId={null} onSelectCustomer={noop} />);
    const btn = await waitFor(() =>
      screen.getByRole('button', { name: /new rate card/i }),
    );
    expect(btn).toBeDisabled();
  });

  it('opens New Rate Card modal when button clicked with customer selected', async () => {
    const user = userEvent.setup();
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();

    await user.click(screen.getByRole('button', { name: /new rate card/i }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
  });

  it('modal has Name, Type, Currency, and Effective From fields', async () => {
    const user = userEvent.setup();
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();

    await user.click(screen.getByRole('button', { name: /new rate card/i }));
    const dialog = await screen.findByRole('dialog');

    // Scope to dialog to avoid collision with table column headers
    expect(within(dialog).getByText('Rate Card Name')).toBeInTheDocument();
    expect(within(dialog).getByText('Rate Card Type')).toBeInTheDocument();
    expect(within(dialog).getByText('Currency')).toBeInTheDocument();
    expect(within(dialog).getByText('Effective From')).toBeInTheDocument();
  });

  it('shows Blended Rate label for FLAT type (default)', async () => {
    const user = userEvent.setup();
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();

    await user.click(screen.getByRole('button', { name: /new rate card/i }));
    await screen.findByRole('dialog');

    expect(screen.getByText('Blended Rate')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /add job level/i })).not.toBeInTheDocument();
  });

  it('shows dynamic job-level rows for TIERED type', async () => {
    const user = userEvent.setup();
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();

    await user.click(screen.getByRole('button', { name: /new rate card/i }));
    await screen.findByRole('dialog');

    const typeCombobox = screen
      .getAllByRole('combobox')
      .find((el) =>
        el.closest('.ant-form-item')?.textContent?.includes('Rate Card Type'),
      );
    if (typeCombobox) {
      await user.click(typeCombobox);
      await waitFor(() => screen.getByText(/tiered — by job level/i));
      await user.click(screen.getByText(/tiered — by job level/i));
    }

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /add job level/i })).toBeInTheDocument(),
    );
  });

  it('cancelling the modal does not trigger a reload', async () => {
    const user = userEvent.setup();
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitForCards();

    await user.click(screen.getByRole('button', { name: /new rate card/i }));
    await screen.findByRole('dialog');

    mockFetchRateCards.mockClear();
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    expect(mockFetchRateCards).not.toHaveBeenCalled();
  });

  it('shows error notification when rate cards fail to load', async () => {
    mockFetchRateCards.mockRejectedValue(new Error('Network error'));
    const { notification } = await import('antd');
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(notification.error).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'Failed to load rate cards' }),
      ),
    );
  });

  it('shows empty state when customer has no rate cards', async () => {
    mockFetchRateCards.mockResolvedValue([]);
    renderWithDateFormat(<RateCardsSection selectedCustomerId="uuid-1" onSelectCustomer={noop} />);
    await waitFor(() =>
      expect(
        screen.getByText('No active rate card — create one below'),
      ).toBeInTheDocument(),
    );
  });
});
