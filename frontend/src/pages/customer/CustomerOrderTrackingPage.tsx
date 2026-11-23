import React, { useState, useMemo, useCallback, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customerOrderClient, CustomerOrder } from '@/lib/customerOrderClient';
import { billClient } from '@/lib/billClient';
import { useRealtimeChannel } from '@/hooks/useRealtimeChannel';
import { RealtimeEventEnvelope } from '@/lib/realtimeClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  CheckCircle2,
  Clock,
  ChefHat,
  BellRing,
  UtensilsCrossed,
  XCircle,
  ArrowLeft,
  RefreshCw,
  AlertTriangle,
  Wifi,
  WifiOff,
  Receipt,
  RotateCcw,
} from 'lucide-react';

export const CustomerOrderTrackingPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [cancelReason, setCancelReason] = useState('');
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);
  const [liveStatusNotice, setLiveStatusNotice] = useState<string | null>(null);
  const [refundNotice, setRefundNotice] = useState<string | null>(null);

  // Authoritative order query (slows polling when WebSocket is connected; fast fallback when disconnected)
  const {
    data: order,
    isLoading,
    isError,
    error,
    refetch: refetchOrder,
  } = useQuery({
    queryKey: ['customer-order', orderId],
    queryFn: () => customerOrderClient.getOrder(orderId!),
    enabled: Boolean(orderId),
    refetchInterval: (query) => {
      const current = query.state.data;
      if (current?.status === 'CANCELLED') {
        return false;
      }
      return wsConnected ? 20000 : 5000;
    },
  });

  // Authoritative order timeline history
  const { data: history = [] } = useQuery({
    queryKey: ['customer-order-history', orderId],
    queryFn: () => customerOrderClient.getOrderHistory(orderId!),
    enabled: Boolean(orderId && order),
    refetchInterval: wsConnected ? 30000 : 10000,
  });

  // Authoritative bill & payment status (kept strictly separate from order preparation lifecycle)
  const {
    data: currentBill,
    refetch: refetchBill,
  } = useQuery({
    queryKey: ['customer-current-bill'],
    queryFn: () => billClient.getCustomerCurrentBill(),
    enabled: Boolean(order),
    retry: false,
    refetchInterval: wsConnected ? 25000 : 8000,
  });

  // Real-time STOMP subscriptions for own order & own guest session
  const destinations = useMemo(() => {
    if (!order?.tenantId || !order?.orderId) return [];
    const topics = [`/topic/tenant/${order.tenantId}/orders/${order.orderId}`];
    if (currentBill?.customerSessionId) {
      topics.push(`/topic/tenant/${order.tenantId}/sessions/${currentBill.customerSessionId}`);
    }
    return topics;
  }, [order?.tenantId, order?.orderId, currentBill?.customerSessionId]);

  const handleRealtimeEvent = useCallback(
    (event: RealtimeEventEnvelope) => {
      if (event.eventType === 'PAYMENT_SETTLED') {
        setLiveStatusNotice('Payment confirmed by restaurant billing system.');
        queryClient.invalidateQueries({ queryKey: ['customer-current-bill'] });
        queryClient.invalidateQueries({ queryKey: ['customer-order', orderId] });
      } else if (event.eventType === 'REFUND_EXECUTED') {
        const amt = event.payload?.refundAmount;
        const curr = event.payload?.currencyCode || currentBill?.currencyCode || order?.currencyCode || '';
        setRefundNotice(
          amt != null
            ? `Refund of ${curr} ${Number(amt).toFixed(2)} processed. Order status remains separate from billing refunds.`
            : 'A refund update has been processed for your bill.'
        );
        queryClient.invalidateQueries({ queryKey: ['customer-current-bill'] });
      } else if (event.eventType.startsWith('ORDER_')) {
        const nextStatus = typeof event.payload?.status === 'string' ? event.payload.status : event.eventType.replace('ORDER_', '');
        setLiveStatusNotice(`Order status updated to ${nextStatus}.`);
        queryClient.invalidateQueries({ queryKey: ['customer-order', orderId] });
        queryClient.invalidateQueries({ queryKey: ['customer-order-history', orderId] });
      }
    },
    [queryClient, orderId, currentBill?.currencyCode, order?.currencyCode]
  );

  const handleReconnect = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['customer-order', orderId] });
    queryClient.invalidateQueries({ queryKey: ['customer-order-history', orderId] });
    queryClient.invalidateQueries({ queryKey: ['customer-current-bill'] });
  }, [queryClient, orderId]);

  const { connectionState } = useRealtimeChannel({
    destinations,
    enabled: destinations.length > 0,
    onEvent: handleRealtimeEvent,
    onReconnect: handleReconnect,
  });

  useEffect(() => {
    setWsConnected(connectionState === 'CONNECTED');
  }, [connectionState]);

  const cancelMutation = useMutation({
    mutationFn: (reason?: string) => customerOrderClient.cancelOrder(orderId!, reason),
    onSuccess: () => {
      setIsCancelModalOpen(false);
      queryClient.invalidateQueries({ queryKey: ['customer-order', orderId] });
      queryClient.invalidateQueries({ queryKey: ['customer-order-history', orderId] });
    },
  });

  const stages: Array<{
    status: CustomerOrder['status'];
    label: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
  }> = [
    { status: 'PLACED', label: 'Order Placed', description: 'Received by restaurant', icon: Clock },
    { status: 'ACCEPTED', label: 'Order Accepted', description: 'Confirmed by service team', icon: CheckCircle2 },
    { status: 'PREPARING', label: 'Preparing', description: 'Kitchen is preparing your dishes', icon: ChefHat },
    { status: 'READY', label: 'Ready', description: 'Ready for table delivery', icon: BellRing },
    { status: 'SERVED', label: 'Served', description: 'Delivered to your table', icon: UtensilsCrossed },
  ];

  const getStageIndex = (status?: CustomerOrder['status']) => {
    switch (status) {
      case 'PLACED': return 0;
      case 'ACCEPTED': return 1;
      case 'PREPARING': return 2;
      case 'READY': return 3;
      case 'SERVED':
      case 'PAID': return 4;
      case 'CANCELLED': return -1;
      default: return 0;
    }
  };

  const currentStageIndex = getStageIndex(order?.status);

  const errObj = error as (Error & { status?: number; errorCode?: string }) | null;
  const isSessionExpired =
    errObj?.status === 401 ||
    errObj?.errorCode === 'SESSION_EXPIRED' ||
    errObj?.errorCode === 'SESSION_INVALID';

  const getPaymentBadge = (status?: string) => {
    switch (status) {
      case 'PAID':
        return { label: 'Paid', className: 'bg-emerald-100 text-emerald-800 border-emerald-300' };
      case 'PARTIALLY_PAID':
        return { label: 'Partially Paid', className: 'bg-amber-100 text-amber-800 border-amber-300' };
      case 'VOIDED':
        return { label: 'Voided', className: 'bg-rose-100 text-rose-800 border-rose-300' };
      default:
        return { label: 'Payment Pending', className: 'bg-slate-100 text-slate-700 border-slate-300' };
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center p-4">
        <div className="text-center space-y-3" role="status" aria-live="polite">
          <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-emerald-600 mx-auto" />
          <p className="text-sm font-semibold text-slate-700">Loading your dining order...</p>
        </div>
      </div>
    );
  }

  if (isError || !order) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center p-4">
        <Card className="max-w-md w-full p-6 text-center space-y-4 bg-white border border-stone-200">
          <div className="w-12 h-12 bg-rose-50 text-rose-600 rounded-full flex items-center justify-center mx-auto">
            <XCircle className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              {isSessionExpired ? 'Dining Session Expired' : 'Order Not Accessible'}
            </h2>
            <p className="text-xs text-stone-600 mt-1">
              {isSessionExpired
                ? 'Your table dining session has expired. Please re-scan the QR code at your table to continue.'
                : error instanceof Error
                ? error.message
                : 'You may only view orders belonging to your active dining session.'}
            </p>
          </div>
          <div className="flex flex-col gap-2">
            {isSessionExpired ? (
              <Button variant="emerald" size="md" className="w-full" onClick={() => navigate('/customer/entry')}>
                Re-scan Table QR
              </Button>
            ) : (
              <Button variant="emerald" size="md" className="w-full" onClick={() => navigate('/customer/menu')}>
                Return to Digital Menu
              </Button>
            )}
            <Button variant="outline" size="sm" className="w-full" onClick={() => refetchOrder()}>
              Retry
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  const paymentBadge = getPaymentBadge(currentBill?.status);
  const currency = currentBill?.currencyCode || order.currencyCode;

  return (
    <div className="min-h-screen bg-stone-50 text-slate-900 pb-16">
      {/* Sticky Top Header */}
      <div className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-stone-200 px-4 py-3 flex items-center justify-between shadow-2xs">
        <Link
          to="/customer/menu"
          className="flex items-center gap-1.5 text-xs font-bold text-stone-600 hover:text-slate-900 min-h-[36px] px-1"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Menu</span>
        </Link>
        <div className="text-center">
          <span className="text-[10px] font-mono font-bold text-stone-500 uppercase tracking-widest block">
            {order.orderNumber}
          </span>
          <span className="text-xs font-black text-slate-900">
            {currentBill?.restaurantName ? `${currentBill.restaurantName} · ` : ''}Table #{order.tableNumber}
          </span>
          {currentBill?.branchName && (
            <span className="text-[10px] text-stone-500 block">{currentBill.branchName}</span>
          )}
        </div>
        <button
          type="button"
          onClick={() => {
            refetchOrder();
            refetchBill();
          }}
          className="p-2 text-stone-500 hover:text-slate-800 rounded-lg hover:bg-stone-100 transition-colors cursor-pointer"
          aria-label="Refresh order and payment status"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      <div className="max-w-xl mx-auto px-4 py-5 space-y-5">
        {/* Live Connection Indicator */}
        <div className="flex items-center justify-between text-xs">
          <span
            role="status"
            aria-live="polite"
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full font-semibold border ${
              connectionState === 'CONNECTED'
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : 'bg-amber-50 text-amber-800 border-amber-200'
            }`}
          >
            {connectionState === 'CONNECTED' ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
            {connectionState === 'CONNECTED'
              ? 'Live Updates Active'
              : connectionState === 'RECONNECTING'
              ? 'Reconnecting...'
              : 'Auto-Refreshing'}
          </span>
          <span className="text-stone-500 font-mono">
            Placed {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>

        {/* Live Order / Payment Update Banner */}
        {liveStatusNotice && (
          <div
            role="status"
            aria-live="polite"
            className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs font-semibold flex items-center justify-between gap-2"
          >
            <span>{liveStatusNotice}</span>
            <button type="button" onClick={() => setLiveStatusNotice(null)} className="underline">
              Dismiss
            </button>
          </div>
        )}

        {/* Refund Update Banner (explicitly separate from order cancellation) */}
        {refundNotice && (
          <div
            role="status"
            aria-live="polite"
            className="p-3 rounded-xl bg-blue-50 border border-blue-200 text-blue-900 text-xs font-semibold flex items-center justify-between gap-2"
          >
            <div className="flex items-center gap-2">
              <RotateCcw className="w-4 h-4 text-blue-600 shrink-0" />
              <span>{refundNotice}</span>
            </div>
            <button type="button" onClick={() => setRefundNotice(null)} className="underline">
              Dismiss
            </button>
          </div>
        )}

        {/* 1. ORDER LIFECYCLE CARD (strictly separate from payment status) */}
        <Card className="p-5 sm:p-6 bg-white border border-stone-200 shadow-sm">
          {order.status === 'CANCELLED' ? (
            <div className="text-center space-y-3">
              <div className="w-12 h-12 bg-rose-100 text-rose-600 rounded-2xl flex items-center justify-center mx-auto">
                <XCircle className="w-6 h-6" />
              </div>
              <h2 className="text-xl font-extrabold text-slate-900">Order Cancelled</h2>
              <p className="text-xs text-stone-600 max-w-sm mx-auto">
                This table order has been cancelled. If a payment was previously settled, refund status is tracked separately below.
              </p>
            </div>
          ) : (
            <div className="space-y-5">
              <div className="text-center">
                <p className="text-[11px] font-bold uppercase tracking-wider text-stone-500">Order Preparation Status</p>
                <h2 className="text-2xl font-black text-slate-900 mt-1">
                  {order.status === 'PLACED' && 'Order Received'}
                  {order.status === 'ACCEPTED' && 'Order Accepted'}
                  {order.status === 'PREPARING' && 'Kitchen Preparing'}
                  {order.status === 'READY' && 'Ready for Table'}
                  {(order.status === 'SERVED' || order.status === 'PAID') && 'Served to Table'}
                </h2>
              </div>

              {/* Accessible Order Stepper */}
              <ol className="space-y-4 text-left" aria-label="Order preparation progress">
                {stages.map((stage, idx) => {
                  const Icon = stage.icon;
                  const isDone = currentStageIndex >= idx;
                  const isCurrent = currentStageIndex === idx;

                  return (
                    <li key={stage.status} className="flex items-start gap-3.5 relative">
                      {idx < stages.length - 1 && (
                        <div
                          className={`absolute left-4 top-8 -bottom-4 w-0.5 ${
                            isDone && currentStageIndex > idx ? 'bg-emerald-600' : 'bg-stone-200'
                          }`}
                        />
                      )}
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10 ${
                          isDone ? 'bg-emerald-600 text-white ring-4 ring-emerald-50' : 'bg-stone-100 text-stone-400'
                        }`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="pt-0.5">
                        <p className={`text-xs font-bold ${isCurrent ? 'text-emerald-700' : isDone ? 'text-slate-900' : 'text-stone-400'}`}>
                          {stage.label}
                        </p>
                        <p className="text-[11px] text-stone-500">{stage.description}</p>
                      </div>
                    </li>
                  );
                })}
              </ol>

              {/* Customer Cancellation Allowed Only While PLACED */}
              {order.status === 'PLACED' && (
                <div className="pt-4 border-t border-stone-100 text-center">
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-rose-600 border-rose-200 hover:bg-rose-50 text-xs min-h-[40px]"
                    onClick={() => setIsCancelModalOpen(true)}
                  >
                    Cancel Order
                  </Button>
                </div>
              )}
            </div>
          )}
        </Card>

        {/* 2. SEPARATE BILL & PAYMENT LIFECYCLE CARD */}
        <Card className="p-5 bg-white border border-stone-200 space-y-3" aria-label="Bill and payment status">
          <div className="flex items-center justify-between border-b border-stone-100 pb-2.5">
            <div className="flex items-center gap-2">
              <Receipt className="w-4 h-4 text-slate-700" />
              <h3 className="font-bold text-sm text-slate-900">Payment &amp; Bill Status</h3>
            </div>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${paymentBadge.className}`}>
              {paymentBadge.label}
            </span>
          </div>

          {currentBill ? (
            <div className="space-y-2 text-xs text-stone-600">
              <div className="flex justify-between">
                <span>Bill Reference</span>
                <span className="font-mono font-semibold text-slate-800">{currentBill.billNumber}</span>
              </div>
              <div className="flex justify-between">
                <span>Bill Total</span>
                <span className="font-mono font-semibold text-slate-800">
                  {currency} {currentBill.grandTotal.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Verified Paid</span>
                <span className="font-mono font-semibold text-emerald-700">
                  {currency} {currentBill.paidAmount.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between pt-2 border-t border-stone-100 text-sm font-bold text-slate-900">
                <span>Outstanding Balance</span>
                <span className={`font-mono ${currentBill.outstandingBalance > 0 ? 'text-amber-700' : 'text-emerald-700'}`}>
                  {currency} {currentBill.outstandingBalance.toFixed(2)}
                </span>
              </div>
              {currentBill.paidAt && (
                <p className="text-[11px] text-emerald-700 pt-1">
                  Settled at {new Date(currentBill.paidAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </p>
              )}
            </div>
          ) : (
            <p className="text-xs text-stone-500">
              Your bill has not been finalized yet. Payment status updates automatically once confirmed by the restaurant.
            </p>
          )}
        </Card>

        {/* 3. ORDERED ITEMS BREAKDOWN */}
        <Card className="p-5 bg-white border border-stone-200 space-y-4">
          <h3 className="font-bold text-sm text-slate-900 border-b border-stone-100 pb-2">Ordered Items</h3>
          <div className="divide-y divide-stone-100">
            {order.items.map((item) => (
              <div key={item.itemId} className="py-3 first:pt-0 last:pb-0">
                <div className="flex justify-between items-start text-sm">
                  <span className="font-semibold text-slate-800">
                    <span className="text-emerald-700 font-bold mr-1.5">{item.quantity}x</span>
                    {item.itemName}
                    {item.variantName && <span className="text-xs text-stone-500 ml-1">({item.variantName})</span>}
                  </span>
                  <span className="font-bold text-slate-900">
                    {order.currencyCode} {item.lineTotal.toFixed(2)}
                  </span>
                </div>
                {item.options.length > 0 && (
                  <div className="pl-6 text-[11px] text-stone-500 mt-0.5">
                    + {item.options.map((o) => o.optionName).join(', ')}
                  </div>
                )}
                {item.specialInstructions && (
                  <div className="pl-6 text-[11px] text-amber-800 italic mt-0.5">
                    &ldquo;{item.specialInstructions}&rdquo;
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Authoritative Order Totals */}
          <div className="pt-3 border-t border-stone-100 space-y-1.5 text-xs text-stone-600">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="font-mono">{order.currencyCode} {order.subtotal.toFixed(2)}</span>
            </div>
            {order.taxAmount > 0 && (
              <div className="flex justify-between">
                <span>Taxes ({order.taxPercentage}%)</span>
                <span className="font-mono">{order.currencyCode} {order.taxAmount.toFixed(2)}</span>
              </div>
            )}
            {order.serviceChargeAmount > 0 && (
              <div className="flex justify-between">
                <span>Service Charge ({order.serviceChargePercentage}%)</span>
                <span className="font-mono">{order.currencyCode} {order.serviceChargeAmount.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-sm font-extrabold text-slate-900 pt-2 border-t border-stone-100">
              <span>Order Total</span>
              <span className="text-emerald-700 font-mono text-base">{order.currencyCode} {order.grandTotal.toFixed(2)}</span>
            </div>
          </div>
        </Card>

        {/* 4. CUSTOMER-SAFE TIMELINE (no internal staff IDs or internal notes exposed) */}
        {history.length > 0 && (
          <Card className="p-5 bg-white border border-stone-200 space-y-3">
            <h4 className="text-xs font-bold text-stone-500 uppercase tracking-wider">Order Updates</h4>
            <div className="space-y-2 text-xs">
              {history.map((h) => (
                <div key={h.id} className="flex justify-between items-center text-stone-600 border-b border-stone-50 pb-1.5 last:border-b-0">
                  <span className="font-bold text-slate-800">{h.newStatus}</span>
                  <span className="text-[10px] text-stone-400 font-mono">
                    {new Date(h.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              ))}
            </div>
          </Card>
        )}
      </div>

      {/* Cancellation Confirmation Modal */}
      {isCancelModalOpen && (
        <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 bg-slate-950/60 flex items-center justify-center p-4">
          <Card className="max-w-md w-full p-6 space-y-4 bg-white border border-stone-200">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-slate-900 text-base">Cancel Dining Order</h3>
                <p className="text-xs text-stone-500">Only permitted before kitchen accepts the ticket.</p>
              </div>
            </div>

            <textarea
              rows={2}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Reason for cancellation (optional)"
              className="w-full text-xs p-3 rounded-xl border border-stone-200 focus:outline-none focus:ring-2 focus:ring-rose-500/20"
            />

            {cancelMutation.isError && (
              <p className="text-xs text-rose-600">
                {cancelMutation.error instanceof Error ? cancelMutation.error.message : 'Failed to cancel order.'}
              </p>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={() => setIsCancelModalOpen(false)}>
                Back
              </Button>
              <Button
                variant="primary"
                size="sm"
                className="bg-rose-600 hover:bg-rose-700 text-white"
                disabled={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate(cancelReason.trim() || undefined)}
              >
                {cancelMutation.isPending ? 'Cancelling...' : 'Confirm Cancellation'}
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
