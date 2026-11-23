import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export type ReportPeriodType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM';

export interface PaymentMethodBreakdown {
  paymentMethod: string;
  transactionCount: number;
  confirmedAmount: number;
}

export interface BranchSalesSummary {
  branchId?: string;
  branchName: string;
  billCount: number;
  grossSales: number;
  discountTotal: number;
  netSales: number;
  totalBillValue: number;
  confirmedCollection: number;
  refundTotal: number;
  netConfirmedCollection: number;
  outstandingBalance: number;
}

export interface SalesTrendPoint {
  bucketLabel: string;
  billCount: number;
  grossSales: number;
  netSales: number;
  totalBillValue: number;
}

export interface FinancialReportResponse {
  tenantId: string;
  branchId?: string;
  periodType: ReportPeriodType;
  fromDate: string;
  toDate: string;
  timezone: string;
  businessDayCutoffHour: number;
  windowStartUtc: string;
  windowEndUtc: string;
  currencyCode: string;
  billCount: number;
  grossSales: number;
  discountTotal: number;
  netSales: number;
  serviceChargeTotal: number;
  taxTotal: number;
  totalBillValue: number;
  averageBillValue: number;
  outstandingBillCount: number;
  outstandingBalance: number;
  confirmedCollectionTotal: number;
  cashCollectionTotal: number;
  onlineCollectionTotal: number;
  refundCount: number;
  refundTotal: number;
  netConfirmedCollection: number;
  totalOrderCount: number;
  totalOrderValue: number;
  cancelledOrderCount: number;
  cancelledOrderValue: number;
  paymentMethodBreakdown: PaymentMethodBreakdown[];
  branchBreakdown: BranchSalesSummary[];
  dailyTrend: SalesTrendPoint[];
  monthlyTrend: SalesTrendPoint[];
  formulaDefinitions: Record<string, string>;
}

export interface FinancialReportParams {
  period: ReportPeriodType;
  fromDate?: string;
  toDate?: string;
  branchId?: string;
  paymentMethod?: string;
  timezone?: string;
  businessDayCutoffHour?: number;
}

function buildQuery(params: FinancialReportParams): string {
  const qs = new URLSearchParams();
  qs.set('period', params.period);
  if (params.fromDate) qs.set('fromDate', params.fromDate);
  if (params.toDate) qs.set('toDate', params.toDate);
  if (params.branchId) qs.set('branchId', params.branchId);
  if (params.paymentMethod) qs.set('paymentMethod', params.paymentMethod);
  if (params.timezone) qs.set('timezone', params.timezone);
  if (params.businessDayCutoffHour !== undefined) {
    qs.set('businessDayCutoffHour', String(params.businessDayCutoffHour));
  }
  return qs.toString();
}

function getAuthHeaders(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export const reportClient = {
  getFinancialReport: async (params: FinancialReportParams): Promise<FinancialReportResponse> => {
    const response = await fetch(`${API_BASE_URL}/reports/financial?${buildQuery(params)}`, {
      headers: {
        'Content-Type': 'application/json',
        ...getAuthHeaders(),
      },
    });
    const body = await response.json();
    if (!response.ok) {
      throw new Error(body?.message || `Failed to load financial report (${response.status})`);
    }
    return body.data;
  },

  downloadExport: async (format: 'csv' | 'pdf', params: FinancialReportParams): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/reports/financial/export/${format}?${buildQuery(params)}`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`);
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `dineflow-financial-report-${params.period.toLowerCase()}.${format}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },
};
