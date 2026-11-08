import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/inter'
import './index.css'
import { App } from './App'
import { ErrorBoundary } from './ErrorBoundary'
import { AuthProvider } from './lib/auth'
import { FeedbackProvider } from './ui'
import { Splash } from './ui/splash'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* outermost, so a crash anywhere (providers included) shows a way out instead of a blank page */}
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <FeedbackProvider>
            <App />
            <Splash />
          </FeedbackProvider>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
)
