import type { ThemeConfig } from 'antd';

/**
 * Centralized Ant Design theme — single source of truth for all brand values.
 * Hex values are defined here only; never hardcoded in components (ADR-014).
 *
 * Token reference: https://ant.design/docs/react/customize-theme
 */
export const antdTheme: ThemeConfig = {
  token: {
    // Brand
    colorPrimary: '#f05756',
    colorLink: '#f05756',
    colorLinkHover: '#d94a49',
    colorLinkActive: '#c23f3e',

    // Backgrounds
    colorBgLayout: '#f7f6f4',   // Light BG — sidebar/content areas
    colorBgContainer: '#ffffff', // Card/form surfaces

    // Typography — Lato for body text (ADR-014)
    fontFamily: "'Lato', system-ui, sans-serif",
    fontSize: 14,

    // Borders & radii
    colorBorder: '#d8d8d8',
    colorBorderSecondary: '#c4c4c4',
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,

    // Text
    colorText: '#2a2a2a',
    colorTextSecondary: '#555555',
    colorTextDescription: '#888888',
    colorTextHeading: '#232323',

    // Status
    colorSuccess: '#3f9d6c',
    colorWarning: '#f68c45',
    colorError: '#f05756',

    // Shadows
    boxShadow: '0 1px 3px rgba(35,35,35,.08), 0 1px 2px rgba(35,35,35,.04)',
    boxShadowSecondary: '0 8px 32px rgba(35,35,35,.18)',
  },
  components: {
    Layout: {
      // Header bar — Black #232323 per ADR-013
      headerBg: '#232323',
      headerHeight: 64,
      // Sidebar
      siderBg: '#ffffff',
      // Content area
      bodyBg: '#f7f6f4',
    },
    Menu: {
      // Sidebar menu on light background
      itemBg: '#ffffff',
      itemSelectedBg: 'rgba(240,87,86,0.09)',
      itemSelectedColor: '#f05756',
      itemHoverBg: '#f0efec',
    },
  },
};

// Heading font — Montserrat (ADR-014).
// Applied via className on Typography.Title or a wrapper element,
// since Ant Design has no dedicated heading-font token.
export const HEADING_FONT = "'Montserrat', 'Trebuchet MS', system-ui, sans-serif";
