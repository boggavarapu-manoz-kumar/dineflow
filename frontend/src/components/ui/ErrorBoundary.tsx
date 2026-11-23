import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallbackTitle?: string;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

/**
 * Production React Error Boundary protecting lazy-loaded routes and operational dashboards
 * from chunk-load failures or unexpected runtime errors without exposing internal stack traces.
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  override componentDidCatch(_error: Error, _info: React.ErrorInfo): void {
    // Error captured cleanly; UI displays safe recovery state without leaking stack traces
  }

  private handleReset = () => {
    this.setState({ hasError: false });
  };

  override render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-[50vh] flex items-center justify-center p-6" role="alert">
          <Card className="max-w-md w-full p-6 text-center space-y-4 bg-white border border-stone-200">
            <div className="w-12 h-12 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center mx-auto">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {this.props.fallbackTitle || 'Something went wrong loading this view'}
              </h2>
              <p className="text-xs text-stone-500 mt-1">
                A network interruption or unexpected display error occurred. Your orders and payments remain safely stored on the server.
              </p>
            </div>
            <div className="flex items-center justify-center gap-2 pt-1">
              <Button variant="outline" size="sm" onClick={this.handleReset}>
                Try Again
              </Button>
              <Button
                variant="emerald"
                size="sm"
                onClick={() => window.location.reload()}
              >
                <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
                Reload Page
              </Button>
            </div>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}
