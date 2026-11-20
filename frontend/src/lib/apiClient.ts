const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface HealthData {
  status: string;
  appName: string;
  version: string;
  environment: string;
  details: Record<string, string>;
}

export async function checkApiHealth(): Promise<ApiResponse<HealthData>> {
  try {
    const res = await fetch(`${API_BASE_URL}/health`, { credentials: 'omit' });
    if (!res.ok) {
      throw new Error(`Health check failed with status: ${res.status}`);
    }
    return await res.json();
  } catch (err: unknown) {
    return {
      success: false,
      message: err instanceof Error ? err.message : 'API offline or unreachable',
      data: {
        status: 'OFFLINE',
        appName: 'DineFlow',
        version: '1.0.0',
        environment: 'offline-preview',
        details: { mode: 'Local preview fallback' },
      },
      timestamp: new Date().toISOString(),
    };
  }
}