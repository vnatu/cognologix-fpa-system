import { useState } from 'react';
import RateCardsSection from './RateCardsSection';

/** Top-level Rate Cards screen (ADR-021). */
export default function RateCardsPage() {
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(
    null,
  );

  return (
    <div style={{ padding: 24 }}>
      <RateCardsSection
        selectedCustomerId={selectedCustomerId}
        onSelectCustomer={setSelectedCustomerId}
      />
    </div>
  );
}
