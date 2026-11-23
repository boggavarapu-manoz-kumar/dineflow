import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { billClient, BillResponse } from '@/lib/billClient';
import { invoiceClient, InvoiceResponse } from '@/lib/invoiceClient';
import { paymentClient, PaymentResponse, RefundResponse } from '@/lib/paymentClient';
import { reportClient } from '@/lib/reportClient';
import { useAuth } from '@/context/AuthContext';
import { BillDetailModal } from '@/pages/staff/components/BillDetailModal';
import { StaffPaymentModal } from '@/pages/staff/components/StaffPaymentModal';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  LayoutDashboard,
  Receipt,
  FileText,
  CreditCard,
  RotateCcw,
  Search,
  Download,
  Banknote,
  CheckCircle2,
  XCircle,
  Clock,
  AlertCircle,
  Loader2,
  Eye,
  Ban,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

type AdminTab = 'OVERVIEW' | 'BILLS' | 'INVOICES' | 'PAYMENTS' | 'REFUNDS';

const PAGE_SIZE = 10;

export const FinancialAdminDashboardPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { hasPermission, hasRole } = useAuth();

  const canCollectCash = hasPermission('PAYMENT_PROCESS') || hasPermission('BILL_COLLECT_PAYMENT') || hasRole('HOTEL_OWNER') || hasRole('HOTEL_ADMIN');
  const canManageInvoices = hasPermission('INVOICE_GENERATE') || hasRole('HOTEL_OWNER') || hasRole('HOTEL_ADMIN') || hasRole('BRANCH_MANAGER');
  const canApproveRefunds = hasPermission('REFUND_APPROVE') || hasRole('HOTEL_OWNER') || hasRole('HOTEL_ADMIN') || hasRole('BRANCH_MANAGER');
  const canExecuteRefunds = hasPermission('REFUND_EXECUTE') || hasRole('HOTEL_OWNER') || hasRole('HOTEL_ADMIN') || hasRole('BRANCH_MANAGER');

  const [activeTab, setActiveTab] = useState<AdminTab>('OVERVIEW');
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(1);

  // Modals & action feedback
  const [selectedBill, setSelectedBill] = useState<BillResponse | null>(null);
  const [cashModalBill, setCashModalBill] = useState<BillResponse | null>(null);
  const [refundTargetPayment, setRefundTargetPayment] = useState<PaymentResponse | null>(null);
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [cancelTargetInvoice, setCancelTargetInvoice] = useState<InvoiceResponse | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [actionBanner, setActionBanner] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Debounce search input (300ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchInput.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // Queries
  const overviewQuery = useQuery({
    queryKey: ['finance-admin-overview'],
    queryFn: () => reportClient.getFinancialReport({ period: 'MONTHLY', timezone: 'Asia/Kolkata' }),
    enabled: activeTab === 'OVERVIEW',
  });

  const billsQuery = useQuery({
    queryKey: ['finance-admin-bills', statusFilter, debouncedSearch],
    queryFn: () => billClient.listBills({ status: statusFilter || undefined, search: debouncedSearch || undefined }),
    enabled: activeTab === 'BILLS',
  });

  const invoicesQuery = useQuery({
    queryKey: ['finance-admin-invoices', debouncedSearch],
    queryFn: () => invoiceClient.listInvoices({ search: debouncedSearch || undefined }),
    enabled: activeTab === 'INVOICES',
  });

  const paymentsQuery = useQuery({
    queryKey: ['finance-admin-payments', statusFilter, debouncedSearch],
    queryFn: () => paymentClient.listPayments({ status: statusFilter || undefined, search: debouncedSearch || undefined }),
    enabled: activeTab === 'PAYMENTS',
  });

  const refundsQuery = useQuery({
    queryKey: ['finance-admin-refunds', statusFilter],
    queryFn: () => paymentClient.listRefunds(statusFilter || undefined),
    enabled: activeTab === 'REFUNDS',
  });

  // Mutations
  const generateInvoiceMutation = useMutation({
    mutationFn: (billId: string) => invoiceClient.generateInvoice({ billId }),
    onSuccess: (inv) => {
      queryClient.invalidateQueries({ queryKey: ['finance-admin-invoices'] });
      setActionBanner({ type: 'success', text: `Tax invoice ${inv.invoiceNumber} generated.` });
    },
    onError: (err: any) => setActionBanner({ type: 'error', text: err?.message || 'Failed to generate invoice.' }),
  });

  const cancelInvoiceMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => invoiceClient.cancelInvoice(id, reason),
    onSuccess: (inv) => {
      queryClient.invalidateQueries({ queryKey: ['finance-admin-invoices'] });
      setCancelTargetInvoice(null);
      setCancelReason('');
      setActionBanner({ type: 'success', text: `Invoice ${inv.invoiceNumber} cancelled.` });
    },
    onError: (err: any) => setActionBanner({ type: 'error', text: err?.message || 'Failed to cancel invoice.' }),
  });

  const requestRefundMutation = useMutation({
    mutationFn: () =>
      paymentClient.requestRefund({
        paymentId: refundTargetPayment!.id,
        amount: parseFloat(refundAmount),
        refundMethod: refundTargetPayment!.paymentMethod === 'ONLINE_PAYMENT' ? 'ONLINE_GATEWAY' : 'CASH',
        reason: refundReason.trim(),
      }),
    onSuccess: (ref) => {
      queryClient.invalidateQueries({ queryKey: ['finance-admin-refunds'] });
      queryClient.invalidateQueries({ queryKey: ['finance-admin-payments'] });
      setRefundTargetPayment(null);
      setRefundAmount('');
      setRefundReason('');
      setActionBanner({ type: 'success', text: `Refund ${ref.refundReference} created (${ref.refundStatus}).` });
    },
    onError: (err: any) => setActionBanner({ type: 'error', text: err?.message || 'Failed to request refund.' }),
  });

  const approveRefundMutation = useMutation({
    mutationFn: (id: string) => paymentClient.approveRefund(id),
    onSuccess: (ref) => {
      queryClient.invalidateQueries({ queryKey: ['finance-admin-refunds'] });
      setActionBanner({ type: 'success', text: `Refund ${ref.refundReference} approved.` });
    },
    onError: (err: any) => setActionBanner({ type: 'error', text: err?.message || 'Approval failed.' }),
  });

  const executeRefundMutation = useMutation({
    mutationFn: (id: string) => paymentClient.executeRefund(id),
    onSuccess: (ref) => {
      queryClient.invalidateQueries({ queryKey: ['finance-admin-refunds'] });
      queryClient.invalidateQueries({ queryKey: ['finance-admin-payments'] });
      queryClient.invalidateQueries({ queryKey: ['finance-admin-bills'] });
      setActionBanner({ type: 'success', text: `Refund ${ref.refundReference} executed successfully.` });
    },
    onError: (err: any) => setActionBanner({ type: 'error', text: err?.message || 'Execution failed.' }),
  });

  const switchTab = (tab: AdminTab) => {
    setActiveTab(tab);
    setSearchInput('');
    setDebouncedSearch('');
    setStatusFilter('');
    setPage(1);
    setActionBanner(null);
  };

  const paginate = <T,>(items: T[]) => {
    const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
    const safePage = Math.min(page, totalPages);
    const slice = items.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);
    return { slice, totalPages, safePage };
  };

  const renderPaymentStatusBadge = (status: PaymentResponse['paymentStatus']) => {
    switch (status) {
      case 'SUCCEEDED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800"><CheckCircle2 className="w-3 h-3" /> Succeeded</span>;
      case 'FAILED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-rose-100 text-rose-800"><XCircle className="w-3 h-3" /> Failed</span>;
      case 'REFUNDED':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-purple-800"><RotateCcw className="w-3 h-3" /> Refunded</span>;
      case 'PROCESSING':
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-100 text-blue-800"><Loader2 className="w-3 h-3 animate-spin" /> Processing</span>;
      default:
        return <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-100 text-amber-800"><Clock className="w-3 h-3" /> {status}</span>;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black text-slate-900 tracking-tight">Financial Operations Console</h1>
          <p className="text-sm text-stone-500 mt-1">
            Unified administration for bills, tax invoices, payment settlements, refunds, and revenue telemetry.
          </p>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-stone-200 pb-3" role="tablist">
        {(
          [
            { id: 'OVERVIEW', label: 'Financial Overview', icon: LayoutDashboard },
            { id: 'BILLS', label: 'Bills', icon: Receipt },
            { id: 'INVOICES', label: 'Invoices', icon: FileText },
            { id: 'PAYMENTS', label: 'Payments', icon: CreditCard },
            { id: 'REFUNDS', label: 'Refunds', icon: RotateCcw },
          ] as const
        ).map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            role="tab"
            aria-selected={activeTab === id}
            onClick={() => switchTab(id)}
            className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
              activeTab === id
                ? 'bg-slate-900 text-white shadow-sm'
                : 'bg-white text-stone-600 border border-stone-200 hover:bg-stone-50'
            }`}
          >
            <Icon className="w-4 h-4" />
            {label}
          </button>
        ))}
      </div>

      {actionBanner && (
        <div
          className={`p-3 rounded-xl text-xs font-semibold flex items-center justify-between ${
            actionBanner.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
              : 'bg-rose-50 text-rose-800 border border-rose-200'
          }`}
        >
          <span>{actionBanner.text}</span>
          <button onClick={() => setActionBanner(null)} className="text-current opacity-70 hover:opacity-100">&times;</button>
        </div>
      )}

      {/* Search & Filter Bar for List Tabs */}
      {activeTab !== 'OVERVIEW' && (
        <Card className="p-4 bg-white border border-stone-200 rounded-2xl flex flex-col sm:flex-row gap-3 items-center justify-between">
          {activeTab !== 'REFUNDS' ? (
            <div className="relative w-full sm:max-w-sm">
              <Search className="w-4 h-4 text-stone-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={
                  activeTab === 'BILLS'
                    ? 'Search bill #, order #, or table...'
                    : activeTab === 'INVOICES'
                    ? 'Search invoice # or customer...'
                    : 'Search payment ref, gateway ref, or bill #...'
                }
                className="w-full pl-9 pr-3 py-2 rounded-lg border border-stone-300 text-xs"
              />
            </div>
          ) : (
            <div className="text-xs font-semibold text-stone-600">Filter refunds by workflow status</div>
          )}

          {activeTab === 'BILLS' && (
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
              className="px-3 py-2 rounded-lg border border-stone-300 text-xs font-semibold"
            >
              <option value="">All Bill Statuses</option>
              <option value="ISSUED">Issued (Unpaid)</option>
              <option value="PARTIALLY_PAID">Partially Paid</option>
              <option value="PAID">Paid</option>
              <option value="VOIDED">Voided</option>
            </select>
          )}

          {activeTab === 'PAYMENTS' && (
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
              className="px-3 py-2 rounded-lg border border-stone-300 text-xs font-semibold"
            >
              <option value="">All Payment Statuses</option>
              <option value="INITIATED">Initiated / Pending</option>
              <option value="PROCESSING">Processing</option>
              <option value="SUCCEEDED">Succeeded</option>
              <option value="FAILED">Failed</option>
              <option value="REFUNDED">Refunded</option>
            </select>
          )}

          {activeTab === 'REFUNDS' && (
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
              className="px-3 py-2 rounded-lg border border-stone-300 text-xs font-semibold"
            >
              <option value="">All Refund Statuses</option>
              <option value="PENDING_APPROVAL">Pending Approval</option>
              <option value="APPROVED">Approved (Ready to Execute)</option>
              <option value="SUCCEEDED">Succeeded</option>
              <option value="FAILED">Failed</option>
            </select>
          )}
        </Card>
      )}

      {/* TAB 1: OVERVIEW */}
      {activeTab === 'OVERVIEW' && (
        overviewQuery.isLoading ? (
          <Card className="p-12 flex justify-center items-center gap-2 text-stone-500">
            <Loader2 className="w-5 h-5 animate-spin" /> Loading live financial summary...
          </Card>
        ) : overviewQuery.isError ? (
          <Card className="p-8 bg-rose-50 border border-rose-200 text-rose-700 text-sm flex items-center gap-2">
            <AlertCircle className="w-5 h-5 shrink-0" />
            {(overviewQuery.error as Error)?.message || 'Failed to load financial overview.'}
          </Card>
        ) : overviewQuery.data ? (
          <div className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
                <div className="text-xs font-bold uppercase text-stone-500">Monthly Net Sales</div>
                <div className="text-2xl font-black text-slate-900 mt-1">
                  {overviewQuery.data.currencyCode} {overviewQuery.data.netSales.toFixed(2)}
                </div>
                <div className="text-xs text-stone-500 mt-1">{overviewQuery.data.billCount} non-voided bills</div>
              </Card>
              <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
                <div className="text-xs font-bold uppercase text-emerald-700">Confirmed Collections</div>
                <div className="text-2xl font-black text-emerald-700 mt-1">
                  {overviewQuery.data.currencyCode} {overviewQuery.data.confirmedCollectionTotal.toFixed(2)}
                </div>
                <div className="text-xs text-stone-500 mt-1">
                  Net after refunds: {overviewQuery.data.currencyCode} {overviewQuery.data.netConfirmedCollection.toFixed(2)}
                </div>
              </Card>
              <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
                <div className="text-xs font-bold uppercase text-amber-700">Outstanding Balance</div>
                <div className="text-2xl font-black text-amber-700 mt-1">
                  {overviewQuery.data.currencyCode} {overviewQuery.data.outstandingBalance.toFixed(2)}
                </div>
                <div className="text-xs text-stone-500 mt-1">{overviewQuery.data.outstandingBillCount} open bills</div>
              </Card>
              <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
                <div className="text-xs font-bold uppercase text-rose-700">Completed Refunds</div>
                <div className="text-2xl font-black text-rose-700 mt-1">
                  {overviewQuery.data.currencyCode} {overviewQuery.data.refundTotal.toFixed(2)}
                </div>
                <div className="text-xs text-stone-500 mt-1">{overviewQuery.data.refundCount} settled refunds</div>
              </Card>
            </div>
          </div>
        ) : null
      )}

      {/* TAB 2: BILLS */}
      {activeTab === 'BILLS' && (() => {
        if (billsQuery.isLoading) return <Card className="p-12 flex justify-center text-stone-500"><Loader2 className="w-5 h-5 animate-spin" /></Card>;
        if (billsQuery.isError) return <Card className="p-8 bg-rose-50 border border-rose-200 text-rose-700 text-xs">{(billsQuery.error as Error)?.message}</Card>;
        const { slice, totalPages, safePage } = paginate(billsQuery.data ?? []);
        return (
          <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
            {slice.length === 0 ? (
              <div className="p-10 text-center text-xs text-stone-500">No bills match the current filters.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-5 py-3 font-bold">Bill # & Table</th>
                      <th className="px-5 py-3 font-bold">Status</th>
                      <th className="px-5 py-3 font-bold">Grand Total</th>
                      <th className="px-5 py-3 font-bold">Outstanding</th>
                      <th className="px-5 py-3 font-bold">Issued</th>
                      <th className="px-5 py-3 font-bold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {slice.map((bill) => (
                      <tr key={bill.id} className="hover:bg-stone-50/70">
                        <td className="px-5 py-3.5">
                          <div className="font-mono font-bold text-slate-900">{bill.billNumber}</div>
                          <div className="text-[11px] text-stone-500">Table {bill.tableNumber}</div>
                        </td>
                        <td className="px-5 py-3.5 font-bold">{bill.status}</td>
                        <td className="px-5 py-3.5 font-semibold">{bill.currencyCode} {bill.grandTotal.toFixed(2)}</td>
                        <td className="px-5 py-3.5 font-bold text-amber-700">{bill.currencyCode} {bill.outstandingBalance.toFixed(2)}</td>
                        <td className="px-5 py-3.5 text-stone-500">{new Date(bill.issuedAt).toLocaleString()}</td>
                        <td className="px-5 py-3.5 text-right space-x-2">
                          <Button variant="outline" size="sm" onClick={() => setSelectedBill(bill)} className="text-xs inline-flex items-center gap-1">
                            <Eye className="w-3.5 h-3.5" /> Details
                          </Button>
                          {canCollectCash && bill.outstandingBalance > 0 && bill.status !== 'VOIDED' && (
                            <Button size="sm" onClick={() => setCashModalBill(bill)} className="bg-amber-600 hover:bg-amber-700 text-white text-xs inline-flex items-center gap-1">
                              <Banknote className="w-3.5 h-3.5" /> Collect Cash
                            </Button>
                          )}
                          {canManageInvoices && bill.status !== 'VOIDED' && (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={generateInvoiceMutation.isPending}
                              onClick={() => generateInvoiceMutation.mutate(bill.id)}
                              className="text-xs inline-flex items-center gap-1"
                            >
                              <FileText className="w-3.5 h-3.5" /> Invoice
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {totalPages > 1 && (
              <div className="px-5 py-3 border-t border-stone-200 flex items-center justify-between text-xs text-stone-600">
                <span>Page {safePage} of {totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={safePage <= 1} onClick={() => setPage(safePage - 1)}><ChevronLeft className="w-4 h-4" /></Button>
                  <Button variant="outline" size="sm" disabled={safePage >= totalPages} onClick={() => setPage(safePage + 1)}><ChevronRight className="w-4 h-4" /></Button>
                </div>
              </div>
            )}
          </Card>
        );
      })()}

      {/* TAB 3: INVOICES */}
      {activeTab === 'INVOICES' && (() => {
        if (invoicesQuery.isLoading) return <Card className="p-12 flex justify-center text-stone-500"><Loader2 className="w-5 h-5 animate-spin" /></Card>;
        if (invoicesQuery.isError) return <Card className="p-8 bg-rose-50 border border-rose-200 text-rose-700 text-xs">{(invoicesQuery.error as Error)?.message}</Card>;
        const { slice, totalPages, safePage } = paginate(invoicesQuery.data ?? []);
        return (
          <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
            {slice.length === 0 ? (
              <div className="p-10 text-center text-xs text-stone-500">No invoices found.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-5 py-3 font-bold">Invoice #</th>
                      <th className="px-5 py-3 font-bold">Status</th>
                      <th className="px-5 py-3 font-bold">Grand Total</th>
                      <th className="px-5 py-3 font-bold">Issued At</th>
                      <th className="px-5 py-3 font-bold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {slice.map((inv) => (
                      <tr key={inv.id} className="hover:bg-stone-50/70">
                        <td className="px-5 py-3.5 font-mono font-bold text-slate-900">{inv.invoiceNumber}</td>
                        <td className="px-5 py-3.5">
                          <span className={`px-2 py-0.5 rounded-full font-bold ${inv.status === 'ISSUED' ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}`}>
                            {inv.status}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 font-semibold">{inv.currencyCode} {inv.grandTotal.toFixed(2)}</td>
                        <td className="px-5 py-3.5 text-stone-500">{new Date(inv.issuedAt).toLocaleString()}</td>
                        <td className="px-5 py-3.5 text-right space-x-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => invoiceClient.downloadPdf(inv.id, inv.invoiceNumber)}
                            className="text-xs inline-flex items-center gap-1"
                          >
                            <Download className="w-3.5 h-3.5" /> PDF
                          </Button>
                          {canManageInvoices && inv.status === 'ISSUED' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setCancelTargetInvoice(inv)}
                              className="text-xs text-rose-600 border-rose-200 hover:bg-rose-50 inline-flex items-center gap-1"
                            >
                              <Ban className="w-3.5 h-3.5" /> Cancel
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {totalPages > 1 && (
              <div className="px-5 py-3 border-t border-stone-200 flex items-center justify-between text-xs text-stone-600">
                <span>Page {safePage} of {totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={safePage <= 1} onClick={() => setPage(safePage - 1)}><ChevronLeft className="w-4 h-4" /></Button>
                  <Button variant="outline" size="sm" disabled={safePage >= totalPages} onClick={() => setPage(safePage + 1)}><ChevronRight className="w-4 h-4" /></Button>
                </div>
              </div>
            )}
          </Card>
        );
      })()}

      {/* TAB 4: PAYMENTS */}
      {activeTab === 'PAYMENTS' && (() => {
        if (paymentsQuery.isLoading) return <Card className="p-12 flex justify-center text-stone-500"><Loader2 className="w-5 h-5 animate-spin" /></Card>;
        if (paymentsQuery.isError) return <Card className="p-8 bg-rose-50 border border-rose-200 text-rose-700 text-xs">{(paymentsQuery.error as Error)?.message}</Card>;
        const { slice, totalPages, safePage } = paginate(paymentsQuery.data ?? []);
        return (
          <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
            {slice.length === 0 ? (
              <div className="p-10 text-center text-xs text-stone-500">No payment transactions match the current filter.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-5 py-3 font-bold">Reference & Provider</th>
                      <th className="px-5 py-3 font-bold">Method</th>
                      <th className="px-5 py-3 font-bold">Amount Paid</th>
                      <th className="px-5 py-3 font-bold">Refundable</th>
                      <th className="px-5 py-3 font-bold">Status</th>
                      <th className="px-5 py-3 font-bold text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {slice.map((p) => {
                      const remainingRefundable = Math.max(0, p.paidAmount - p.refundedAmount);
                      return (
                        <tr key={p.id} className="hover:bg-stone-50/70">
                          <td className="px-5 py-3.5">
                            <div className="font-mono font-bold text-slate-900">{p.paymentReference}</div>
                            <div className="text-[11px] text-stone-500">{p.paymentProvider} {p.providerPaymentId ? `• ${p.providerPaymentId}` : ''}</div>
                          </td>
                          <td className="px-5 py-3.5 font-semibold">{p.paymentMethod}</td>
                          <td className="px-5 py-3.5 font-bold">{p.currencyCode} {p.paidAmount.toFixed(2)}</td>
                          <td className="px-5 py-3.5 text-emerald-700 font-semibold">{p.currencyCode} {remainingRefundable.toFixed(2)}</td>
                          <td className="px-5 py-3.5">{renderPaymentStatusBadge(p.paymentStatus)}</td>
                          <td className="px-5 py-3.5 text-right">
                            {p.paymentStatus === 'SUCCEEDED' && remainingRefundable > 0 && (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => {
                                  setRefundTargetPayment(p);
                                  setRefundAmount(remainingRefundable.toFixed(2));
                                }}
                                className="text-xs inline-flex items-center gap-1"
                              >
                                <RotateCcw className="w-3.5 h-3.5" /> Request Refund
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {totalPages > 1 && (
              <div className="px-5 py-3 border-t border-stone-200 flex items-center justify-between text-xs text-stone-600">
                <span>Page {safePage} of {totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={safePage <= 1} onClick={() => setPage(safePage - 1)}><ChevronLeft className="w-4 h-4" /></Button>
                  <Button variant="outline" size="sm" disabled={safePage >= totalPages} onClick={() => setPage(safePage + 1)}><ChevronRight className="w-4 h-4" /></Button>
                </div>
              </div>
            )}
          </Card>
        );
      })()}

      {/* TAB 5: REFUNDS */}
      {activeTab === 'REFUNDS' && (() => {
        if (refundsQuery.isLoading) return <Card className="p-12 flex justify-center text-stone-500"><Loader2 className="w-5 h-5 animate-spin" /></Card>;
        if (refundsQuery.isError) return <Card className="p-8 bg-rose-50 border border-rose-200 text-rose-700 text-xs">{(refundsQuery.error as Error)?.message}</Card>;
        const { slice } = paginate(refundsQuery.data ?? []);
        return (
          <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
            {slice.length === 0 ? (
              <div className="p-10 text-center text-xs text-stone-500">No refund records found.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-5 py-3 font-bold">Refund Ref</th>
                      <th className="px-5 py-3 font-bold">Method</th>
                      <th className="px-5 py-3 font-bold">Amount</th>
                      <th className="px-5 py-3 font-bold">Reason</th>
                      <th className="px-5 py-3 font-bold">Status</th>
                      <th className="px-5 py-3 font-bold text-right">Workflow Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {slice.map((r: RefundResponse) => (
                      <tr key={r.id} className="hover:bg-stone-50/70">
                        <td className="px-5 py-3.5 font-mono font-bold text-slate-900">{r.refundReference}</td>
                        <td className="px-5 py-3.5">{r.refundMethod}</td>
                        <td className="px-5 py-3.5 font-bold text-rose-700">{r.currencyCode} {r.refundAmount.toFixed(2)}</td>
                        <td className="px-5 py-3.5 text-stone-600 max-w-xs truncate">{r.reason}</td>
                        <td className="px-5 py-3.5 font-bold">{r.refundStatus}</td>
                        <td className="px-5 py-3.5 text-right space-x-2">
                          {canApproveRefunds && r.refundStatus === 'PENDING_APPROVAL' && (
                            <Button
                              size="sm"
                              disabled={approveRefundMutation.isPending}
                              onClick={() => approveRefundMutation.mutate(r.id)}
                              className="bg-amber-600 hover:bg-amber-700 text-white text-xs"
                            >
                              Approve
                            </Button>
                          )}
                          {canExecuteRefunds && r.refundStatus === 'APPROVED' && (
                            <Button
                              size="sm"
                              disabled={executeRefundMutation.isPending}
                              onClick={() => executeRefundMutation.mutate(r.id)}
                              className="bg-emerald-600 hover:bg-emerald-700 text-white text-xs"
                            >
                              Execute Refund
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        );
      })()}

      {/* Reused Bill Detail Modal */}
      {selectedBill && (
        <BillDetailModal
          bill={selectedBill}
          isOpen={Boolean(selectedBill)}
          onClose={() => setSelectedBill(null)}
          onBillUpdated={() => queryClient.invalidateQueries({ queryKey: ['finance-admin-bills'] })}
        />
      )}

      {/* Reused Staff Cash Collection Modal */}
      {cashModalBill && (
        <StaffPaymentModal
          bill={cashModalBill}
          isOpen={Boolean(cashModalBill)}
          onClose={() => setCashModalBill(null)}
          onPaymentRecorded={() => {
            queryClient.invalidateQueries({ queryKey: ['finance-admin-bills'] });
            queryClient.invalidateQueries({ queryKey: ['finance-admin-payments'] });
          }}
        />
      )}

      {/* Request Refund Confirmation Modal */}
      {refundTargetPayment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-stone-200 p-6 space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Request Refund</h3>
            <p className="text-xs text-stone-600">
              Payment <span className="font-mono font-bold">{refundTargetPayment.paymentReference}</span> • Remaining refundable:{' '}
              <strong>
                {refundTargetPayment.currencyCode} {(refundTargetPayment.paidAmount - refundTargetPayment.refundedAmount).toFixed(2)}
              </strong>
            </p>
            <div>
              <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Refund Amount</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                max={refundTargetPayment.paidAmount - refundTargetPayment.refundedAmount}
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Mandatory Reason</label>
              <input
                type="text"
                value={refundReason}
                onChange={(e) => setRefundReason(e.target.value)}
                placeholder="e.g. Cancelled item or billing discrepancy"
                className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => setRefundTargetPayment(null)}>Cancel</Button>
              <Button
                disabled={!refundReason.trim() || !(parseFloat(refundAmount) > 0) || requestRefundMutation.isPending}
                onClick={() => requestRefundMutation.mutate()}
                className="bg-rose-600 hover:bg-rose-700 text-white"
              >
                Submit Refund Request
              </Button>
            </div>
          </Card>
        </div>
      )}

      {/* Cancel Invoice Confirmation Modal */}
      {cancelTargetInvoice && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-stone-200 p-6 space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Cancel Tax Invoice</h3>
            <p className="text-xs text-stone-600">
              Confirm cancellation of invoice <span className="font-mono font-bold">{cancelTargetInvoice.invoiceNumber}</span>.
            </p>
            <input
              type="text"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Mandatory cancellation reason"
              className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
            />
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCancelTargetInvoice(null)}>Close</Button>
              <Button
                disabled={!cancelReason.trim() || cancelInvoiceMutation.isPending}
                onClick={() => cancelInvoiceMutation.mutate({ id: cancelTargetInvoice.id, reason: cancelReason.trim() })}
                className="bg-rose-600 hover:bg-rose-700 text-white"
              >
                Confirm Cancellation
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
