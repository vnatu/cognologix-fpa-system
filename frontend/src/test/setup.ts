import '@testing-library/jest-dom';

// Ant Design components use matchMedia and ResizeObserver which jsdom doesn't provide.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

(globalThis as typeof globalThis & { ResizeObserver: typeof ResizeObserver }).ResizeObserver =
  class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Ant Design's virtual list needs scrollTo
Object.defineProperty(window, 'scrollTo', { value: () => {}, writable: true });

// Suppress Ant Design's CSS-in-JS warnings in test output
const originalError = console.error;
console.error = (...args: unknown[]) => {
  if (
    typeof args[0] === 'string' &&
    (args[0].includes('Warning: An update to') ||
      args[0].includes('Each child in a list'))
  ) {
    return;
  }
  originalError(...args);
};
