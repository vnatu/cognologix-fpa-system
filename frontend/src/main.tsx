import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { antdTheme } from '@/theme/antdTheme';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider theme={antdTheme}>
      <App />
    </ConfigProvider>
  </React.StrictMode>,
);
