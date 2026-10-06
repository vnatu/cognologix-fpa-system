type SidebarIconProps = {
  size?: number;
  color?: string;
};

// Shown when sidebar is OPEN (click to collapse)
export const SidebarCollapseIcon = ({ size = 18, color = 'currentColor' }: SidebarIconProps) => (
  <svg viewBox="-0.5 -0.5 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" width={size} height={size}>
    <path d="M12.7769375 14.284625H2.2230625c-0.8326875 0 -1.5076875 -0.675 -1.5076875 -1.5076875l0 -10.553875c0 -0.8326875 0.675 -1.5076875 1.5076875 -1.5076875h10.553875c0.8326875 0 1.5076875 0.675 1.5076875 1.5076875v10.553875c0 0.8326875 -0.675 1.5076875 -1.5076875 1.5076875Z" stroke={color} strokeLinecap="round" strokeLinejoin="round" strokeWidth="1"/>
    <path d="M3.9192500000000003 5.9923125 2.6 7.5l1.3192499999999998 1.5076875" stroke={color} strokeLinecap="round" strokeLinejoin="round" strokeWidth="1"/>
    <path d="M5.615375 14.284625V0.7153750000000001" stroke={color} strokeLinecap="round" strokeLinejoin="round" strokeWidth="1"/>
  </svg>
);

// Shown when sidebar is CLOSED (click to expand)
export const SidebarExpandIcon = ({ size = 18, color = 'currentColor' }: SidebarIconProps) => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke={color} strokeLinecap="round" strokeLinejoin="round" width={size} height={size}>
    <path d="M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2z" strokeWidth="2"/>
    <path d="M15 4v16" strokeWidth="2"/>
    <path d="m9 10 2 2 -2 2" strokeWidth="2"/>
  </svg>
);
