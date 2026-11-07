import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State { failed: boolean }

/**
 * Last line of defence for a render error. React unmounts the whole tree when a component throws while rendering, which
 * leaves a blank page with no way out. This shows a way out instead.
 *
 * It catches errors thrown while rendering, in lifecycle methods and in constructors below it. It does not catch errors in
 * event handlers or in promises: those are handled where they happen (try/catch and a toast).
 *
 * The fallback is deliberately plain markup with no context, router or shared component, because it must keep working
 * when the code that failed is what it would otherwise depend on.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // the browser console is the only report channel there is for now
    console.error('Render error caught by ErrorBoundary:', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className="mx-auto flex min-h-dvh max-w-[480px] flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-2xl font-extrabold">Something went wrong</h1>
        <p className="text-muted">The app hit an unexpected problem. Your data is safe on the server.</p>
        <div className="flex w-full gap-3">
          <button type="button" className="glass-btn flex-1 rounded-full py-3.5 font-semibold" onClick={() => this.setState({ failed: false })}>
            Try again
          </button>
          <button type="button" className="btn-accent flex-1 rounded-full py-3.5 font-bold" onClick={() => window.location.reload()}>
            Reload app
          </button>
        </div>
      </div>
    )
  }
}
