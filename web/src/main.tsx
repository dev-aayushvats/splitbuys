import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/inter'
import './index.css'
import { App } from './App'
import { ErrorBoundary } from './ErrorBoundary'
import { AuthProvider } from './lib/auth'
import { persistOptions, queryClient } from './lib/queryClient'
import { FeedbackProvider } from './ui'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* outermost, so a crash anywhere (providers included) shows a way out instead of a blank page */}
    <ErrorBoundary>
      <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
        <BrowserRouter>
          <AuthProvider>
            <FeedbackProvider>
              <App />
            </FeedbackProvider>
          </AuthProvider>
        </BrowserRouter>
      </PersistQueryClientProvider>
    </ErrorBoundary>
  </StrictMode>,
)
