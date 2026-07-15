import { useState } from 'react';
import ProjectCodesSection from './ProjectCodesSection';

/** Top-level Project Codes screen (ADR-021). */
export default function ProjectCodesPage() {
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(
    null,
  );

  return (
    <div style={{ padding: 24 }}>
      <ProjectCodesSection
        selectedCustomerId={selectedCustomerId}
        onSelectCustomer={setSelectedCustomerId}
      />
    </div>
  );
}
