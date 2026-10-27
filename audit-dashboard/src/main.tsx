import '@carbon/react/index.scss';
import './styles/motion.css';
import './styles/theme.css';
import './styles/paletas.css';
import './styles/responsivo.css';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { applyTheme, useThemeStore } from './store/useThemeStore';

// Aplica o tema salvo antes do primeiro desenho, para a página não piscar clara.
applyTheme(useThemeStore.getState().mode);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
