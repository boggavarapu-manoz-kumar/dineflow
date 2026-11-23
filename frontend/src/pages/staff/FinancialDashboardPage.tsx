import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reportClient, ReportPeriodType, FinancialReportParams } from '@/lib/reportClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  BarChart3,
  Download,
  FileText,
  TrendingUp,
  Wallet,
  AlertCircle,
  Clock,
  RotateCcw,
  Receipt,
  Building2,
  Loader2,
  Info,
} from 'lucide-react';

export const FinancialDashboardPage: React.FC = () => {
  const [period, setPeriod] = useState<ReportPeriodType>('MONTHLY');
  const [fromDate, setFromDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [toDate, setToDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [timezone, setTimezone] = useState<string>('Asia/Kolkata');
  const [cutoffHour, setCutoffHour] = useState<number>(0);
  const [trendMode, setTrendMode] = useState<'DAILY' | 'MONTHLY'>('DAILY');
  const [exportError, setExportError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<'csv' | 'pdf' | null>(null);

  const queryParams: FinancialReportParams = {
    period,
    fromDate: period === 'CUSTOM' ? fromDate : undefined,
    toDate: period === 'CUSTOM' ? toDate : undefined,
    timezone,
    businessDayCutoffHour: cutoffHour,
  };

  const { data: report, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['financial-report', queryParams],
    queryFn: () => reportClient.getFinancialReport(queryParams),
  });

  const handleExport = async (format: 'csv' | 'pdf') => {
    setExportError(null);
    setExporting(format);
    try {
      await reportClient.downloadExport(format, queryParams);
    } catch (err: any) {
      setExportError(err?.message || `Failed to export ${format.toUpperCase()}`);
    } finally {
      setExporting(null);
    }
  };

  const formatMoney = (amount: number | undefined, currency = 'INR') =>
    `${currency} ${(amount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const activeTrend = report ? (trendMode === 'DAILY' ? report.dailyTrend : report.monthlyTrend) : [];
  const maxTrendValue = activeTrend.reduce((max, p) => Math.max(max, p.totalBillValue), 0);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header & Export Controls */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center gap-3">
            <span className="p-2.5 rounded-xl bg-slate-900 text-emerald-400 shadow-md">
              <BarChart3 className="w-6 h-6" />
            </span>
            Financial Analytics & Revenue Reports
          </h1>
          <p className="text-sm text-stone-500 mt-1">
            Authoritative summaries aggregated from persisted bills, confirmed payments, and completed refunds.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            disabled={isLoading || exporting !== null}
            onClick={() => handleExport('csv')}
            className="inline-flex items-center gap-2 text-xs font-bold"
          >
            {exporting === 'csv' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Export CSV
          </Button>
          <Button
            disabled={isLoading || exporting !== null}
            onClick={() => handleExport('pdf')}
            className="bg-emerald-600 hover:bg-emerald-700 text-white inline-flex items-center gap-2 text-xs font-bold"
          >
            {exporting === 'pdf' ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
            Export PDF Report
          </Button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <Card className="p-4 bg-white border border-stone-200 rounded-2xl flex flex-wrap items-end gap-4">
        <div>
          <label className="block text-[11px] font-bold uppercase text-stone-500 mb-1">Period</label>
          <div className="flex rounded-lg border border-stone-200 overflow-hidden">
            {(['DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM'] as ReportPeriodType[]).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPeriod(p)}
                className={`px-3 py-1.5 text-xs font-bold transition-colors ${
                  period === p ? 'bg-slate-900 text-white' : 'bg-white text-stone-600 hover:bg-stone-50'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        {period === 'CUSTOM' && (
          <>
            <div>
              <label className="block text-[11px] font-bold uppercase text-stone-500 mb-1">From Date</label>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="px-3 py-1.5 rounded-lg border border-stone-300 text-xs font-medium"
              />
            </div>
            <div>
              <label className="block text-[11px] font-bold uppercase text-stone-500 mb-1">To Date</label>
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="px-3 py-1.5 rounded-lg border border-stone-300 text-xs font-medium"
              />
            </div>
          </>
        )}

        <div>
          <label className="block text-[11px] font-bold uppercase text-stone-500 mb-1">Business Timezone</label>
          <select
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            className="px-3 py-1.5 rounded-lg border border-stone-300 text-xs font-medium"
          >
            <option value="Asia/Kolkata">Asia/Kolkata (IST)</option>
            <option value="UTC">UTC</option>
            <option value="America/New_York">America/New_York (EST/EDT)</option>
            <option value="Europe/London">Europe/London (GMT/BST)</option>
            <option value="Asia/Dubai">Asia/Dubai (GST)</option>
          </select>
        </div>

        <div>
          <label className="block text-[11px] font-bold uppercase text-stone-500 mb-1">Day Cutoff Hour</label>
          <select
            value={cutoffHour}
            onChange={(e) => setCutoffHour(Number(e.target.value))}
            className="px-3 py-1.5 rounded-lg border border-stone-300 text-xs font-medium"
          >
            <option value={0}>00:00 (Standard Midnight)</option>
            <option value={3}>03:00 AM (Late Night Service)</option>
            <option value={4}>04:00 AM (Cross-Midnight Bar/Kitchen)</option>
          </select>
        </div>
      </Card>

      {exportError && (
        <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{exportError}</span>
        </div>
      )}

      {/* Loading / Error / Data States */}
      {isLoading ? (
        <Card className="p-16 flex flex-col items-center justify-center text-stone-500 gap-3 bg-white border border-stone-200 rounded-2xl">
          <Loader2 className="w-8 h-8 animate-spin text-emerald-600" />
          <p className="text-sm font-medium">Aggregating financial ledger metrics...</p>
        </Card>
      ) : isError ? (
        <Card className="p-10 bg-rose-50 border border-rose-200 rounded-2xl flex flex-col items-center text-center gap-3">
          <AlertCircle className="w-10 h-10 text-rose-600" />
          <h3 className="text-lg font-bold text-rose-900">Unable to load financial data</h3>
          <p className="text-xs text-rose-700 max-w-md">
            {(error as Error)?.message || 'An unexpected error occurred while querying the reporting API.'}
          </p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Retry Query
          </Button>
        </Card>
      ) : report ? (
        <>
          {/* Primary KPI Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
              <div className="flex items-center justify-between text-xs font-bold uppercase text-stone-500">
                <span>Net Sales (Pre-Tax)</span>
                <TrendingUp className="w-4 h-4 text-emerald-500" />
              </div>
              <div className="text-2xl font-black text-slate-900 mt-2">
                {formatMoney(report.netSales, report.currencyCode)}
              </div>
              <p className="text-[11px] text-stone-500 mt-1">
                Gross {formatMoney(report.grossSales, report.currencyCode)} − Disc {formatMoney(report.discountTotal, report.currencyCode)}
              </p>
            </Card>

            <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
              <div className="flex items-center justify-between text-xs font-bold uppercase text-emerald-700">
                <span>Net Confirmed Collection</span>
                <Wallet className="w-4 h-4 text-emerald-600" />
              </div>
              <div className="text-2xl font-black text-emerald-700 mt-2">
                {formatMoney(report.netConfirmedCollection, report.currencyCode)}
              </div>
              <p className="text-[11px] text-stone-500 mt-1">
                Confirmed {formatMoney(report.confirmedCollectionTotal, report.currencyCode)} − Refunds {formatMoney(report.refundTotal, report.currencyCode)}
              </p>
            </Card>

            <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
              <div className="flex items-center justify-between text-xs font-bold uppercase text-amber-700">
                <span>Outstanding Bills</span>
                <Clock className="w-4 h-4 text-amber-600" />
              </div>
              <div className="text-2xl font-black text-amber-700 mt-2">
                {formatMoney(report.outstandingBalance, report.currencyCode)}
              </div>
              <p className="text-[11px] text-stone-500 mt-1">
                {report.outstandingBillCount} unpaid / partially paid bills
              </p>
            </Card>

            <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
              <div className="flex items-center justify-between text-xs font-bold uppercase text-rose-700">
                <span>Completed Refunds</span>
                <RotateCcw className="w-4 h-4 text-rose-500" />
              </div>
              <div className="text-2xl font-black text-rose-700 mt-2">
                {formatMoney(report.refundTotal, report.currencyCode)}
              </div>
              <p className="text-[11px] text-stone-500 mt-1">
                {report.refundCount} settled refunds • {report.cancelledOrderCount} cancelled orders
              </p>
            </Card>
          </div>

          {/* Secondary Fiscal Breakdown */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <Card className="p-4 bg-stone-50 border border-stone-200 rounded-xl">
              <div className="text-[11px] font-bold uppercase text-stone-500">Tax Collected</div>
              <div className="text-lg font-extrabold text-slate-900 mt-1">{formatMoney(report.taxTotal, report.currencyCode)}</div>
            </Card>
            <Card className="p-4 bg-stone-50 border border-stone-200 rounded-xl">
              <div className="text-[11px] font-bold uppercase text-stone-500">Service Charges</div>
              <div className="text-lg font-extrabold text-slate-900 mt-1">{formatMoney(report.serviceChargeTotal, report.currencyCode)}</div>
            </Card>
            <Card className="p-4 bg-stone-50 border border-stone-200 rounded-xl">
              <div className="text-[11px] font-bold uppercase text-stone-500">Average Bill Value</div>
              <div className="text-lg font-extrabold text-slate-900 mt-1">{formatMoney(report.averageBillValue, report.currencyCode)}</div>
              <div className="text-[11px] text-stone-500">{report.billCount} issued bills</div>
            </Card>
            <Card className="p-4 bg-stone-50 border border-stone-200 rounded-xl">
              <div className="text-[11px] font-bold uppercase text-stone-500">Cash vs Online</div>
              <div className="text-xs font-bold text-slate-800 mt-1">
                Cash: {formatMoney(report.cashCollectionTotal, report.currencyCode)}
              </div>
              <div className="text-xs font-bold text-emerald-700">
                Online: {formatMoney(report.onlineCollectionTotal, report.currencyCode)}
              </div>
            </Card>
          </div>

          {/* Sales Trend Chart & Payment Method Distribution */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <Card className="lg:col-span-2 p-6 bg-white border border-stone-200 rounded-2xl space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Receipt className="w-4 h-4 text-emerald-600" />
                  Sales Trend ({report.fromDate} → {report.toDate})
                </h2>
                <div className="flex rounded-lg border border-stone-200 overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setTrendMode('DAILY')}
                    className={`px-2.5 py-1 text-xs font-bold ${trendMode === 'DAILY' ? 'bg-emerald-600 text-white' : 'text-stone-600'}`}
                  >
                    Daily
                  </button>
                  <button
                    type="button"
                    onClick={() => setTrendMode('MONTHLY')}
                    className={`px-2.5 py-1 text-xs font-bold ${trendMode === 'MONTHLY' ? 'bg-emerald-600 text-white' : 'text-stone-600'}`}
                  >
                    Monthly
                  </button>
                </div>
              </div>

              {activeTrend.length === 0 ? (
                <div className="py-12 text-center text-xs text-stone-500">
                  Zero billed sales recorded in this date window ({report.currencyCode} 0.00).
                </div>
              ) : (
                <div className="space-y-3 pt-2">
                  {activeTrend.map((point) => {
                    const widthPct = maxTrendValue > 0 ? Math.max(4, Math.round((point.totalBillValue / maxTrendValue) * 100)) : 0;
                    return (
                      <div key={point.bucketLabel} className="space-y-1">
                        <div className="flex justify-between text-xs">
                          <span className="font-mono font-bold text-slate-700">{point.bucketLabel}</span>
                          <span className="font-semibold text-slate-900">
                            {formatMoney(point.totalBillValue, report.currencyCode)} ({point.billCount} bills)
                          </span>
                        </div>
                        <div className="w-full bg-stone-100 rounded-full h-2.5 overflow-hidden">
                          <div
                            className="bg-emerald-600 h-2.5 rounded-full transition-all duration-300"
                            style={{ width: `${widthPct}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>

            <Card className="p-6 bg-white border border-stone-200 rounded-2xl space-y-4">
              <h2 className="text-base font-bold text-slate-900">Payment Method Distribution</h2>
              {report.paymentMethodBreakdown.length === 0 ? (
                <div className="py-10 text-center text-xs text-stone-500">
                  No confirmed payments in this window.
                </div>
              ) : (
                <div className="space-y-4">
                  {report.paymentMethodBreakdown.map((pm) => {
                    const share =
                      report.confirmedCollectionTotal > 0
                        ? Math.round((pm.confirmedAmount / report.confirmedCollectionTotal) * 100)
                        : 0;
                    return (
                      <div key={pm.paymentMethod} className="space-y-1">
                        <div className="flex justify-between text-xs font-semibold">
                          <span className="text-slate-800">{pm.paymentMethod.replace(/_/g, ' ')}</span>
                          <span className="text-emerald-700">
                            {formatMoney(pm.confirmedAmount, report.currencyCode)} ({share}%)
                          </span>
                        </div>
                        <div className="w-full bg-stone-100 h-2 rounded-full overflow-hidden">
                          <div className="bg-slate-900 h-2 rounded-full" style={{ width: `${share}%` }} />
                        </div>
                        <div className="text-[11px] text-stone-500">{pm.transactionCount} confirmed transactions</div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>
          </div>

          {/* Branch Comparison Table */}
          <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
            <div className="px-6 py-4 border-b border-stone-200 flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Building2 className="w-4 h-4 text-emerald-600" />
                Branch-Wise Financial Comparison
              </h2>
              <span className="text-xs text-stone-500">
                UTC Window: {new Date(report.windowStartUtc).toISOString()} → {new Date(report.windowEndUtc).toISOString()}
              </span>
            </div>
            {report.branchBreakdown.length === 0 ? (
              <div className="p-8 text-center text-xs text-stone-500">No branch billing records for the selected period.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-6 py-3 font-bold">Branch</th>
                      <th className="px-6 py-3 font-bold">Bills</th>
                      <th className="px-6 py-3 font-bold">Net Sales</th>
                      <th className="px-6 py-3 font-bold">Billed Total</th>
                      <th className="px-6 py-3 font-bold">Confirmed Collected</th>
                      <th className="px-6 py-3 font-bold">Refunds</th>
                      <th className="px-6 py-3 font-bold">Net Collected</th>
                      <th className="px-6 py-3 font-bold">Outstanding</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {report.branchBreakdown.map((b, idx) => (
                      <tr key={b.branchId || idx} className="hover:bg-stone-50/70">
                        <td className="px-6 py-3.5 font-bold text-slate-900">{b.branchName}</td>
                        <td className="px-6 py-3.5 font-mono">{b.billCount}</td>
                        <td className="px-6 py-3.5">{formatMoney(b.netSales, report.currencyCode)}</td>
                        <td className="px-6 py-3.5 font-semibold">{formatMoney(b.totalBillValue, report.currencyCode)}</td>
                        <td className="px-6 py-3.5 text-emerald-700 font-semibold">{formatMoney(b.confirmedCollection, report.currencyCode)}</td>
                        <td className="px-6 py-3.5 text-rose-600">{formatMoney(b.refundTotal, report.currencyCode)}</td>
                        <td className="px-6 py-3.5 text-emerald-800 font-bold">{formatMoney(b.netConfirmedCollection, report.currencyCode)}</td>
                        <td className="px-6 py-3.5 text-amber-700 font-semibold">{formatMoney(b.outstandingBalance, report.currencyCode)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          {/* Financial Formula Definitions Footer */}
          <Card className="p-5 bg-stone-50 border border-stone-200 rounded-2xl space-y-2">
            <div className="text-xs font-bold uppercase text-stone-600 flex items-center gap-1.5">
              <Info className="w-3.5 h-3.5" /> Financial Metric Definitions
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px] text-stone-600">
              {Object.entries(report.formulaDefinitions).map(([k, v]) => (
                <div key={k}>
                  <span className="font-mono font-bold text-slate-800">{k}:</span> {v}
                </div>
              ))}
            </div>
          </Card>
        </>
      ) : null}
    </div>
  );
};
