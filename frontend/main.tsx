import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import { SSEContextProvider } from './SSEContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SSEContextProvider notificationsHost='sse.dev'>
      <App />
    </SSEContextProvider>
  </StrictMode>,
)
