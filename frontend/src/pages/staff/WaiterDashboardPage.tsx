import React, { useState, useMemo, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bell, RefreshCw, Wifi, WifiOff, AlertTriangle, CheckCircle2, CreditCard } from 'lucide-react';
import { waiterClient, OrderDetail } from '@/lib/waiterClient';
import { useAuth } from '@/context/AuthContext';
import { useRealtimeChannel } from '@/hooks/useRealtimeChannel';
import { RealtimeEventEnvelope } from '@/lib/realtimeClient';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const WaiterDashboardPage: React.FC = () => {
  const { user, hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'ALL' | 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'SERVED'>('ALL');
  const [liveAlert, setLiveAlert] = useState<string | null>(null);
  const [actionFeedback, setActionFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [cancelConfirmOrder, setCancelConfirmOrder] = useState<OrderDetail | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const role = user?.role?.toUpperCase() || '';
  const isManager = ['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER'].includes(role);
  const canAcceptOrder = isManager || hasPermission('ORDER_ACCEPT') || hasPermission('ORDER_MANAGE');
  const canServeOrder = isManager || hasPermission('ORDER_SERVE') || hasPermission('WAITER_VIEW') || hasPermission('ORDER_MANAGE');
  const canCancelOrder = isManager || hasPermission('ORDER_CANCEL') || hasPermission('ORDER_MANAGE');
  const canCollectCash = isManager || hasPermission('BILL_COLLECT_PAYMENT') || hasPermission('PAYMENT_PROCESS');

  // Authoritative REST query — polling slows down when WebSocket is CONNECTED and speeds up as fallback
  const [wsConnected, setWsConnected] = useState(false);

  const {
    data: orders = [],
    isLoading,
    error,
    refetch,
    dataUpdatedAt,
  } = useQuery({
    queryKey: ['waiter-assigned-orders'],
    queryFn: () => waiterClient.getMyAssignedOrders(),
    refetchInterval: wsConnected ? 30000 : 10000,
  });

  // Resolve tenant & branch for STOMP subscription strictly from authenticated user/server order data
  const activeBranchId = user?.branchId || orders.find((o) => o.branchId)?.branchId || null;
  const destinations = useMemo(() => {
    if (!user?.activeTenantId || !activeBranchId) return [];
    const list = [`/topic/tenant/${user.activeTenantId}/branch/${activeBranchId}/waiter`];
    if (canCollectCash) {
      list.push(`/topic/tenant/${user.activeTenantId}/branch/${activeBranchId}/payments`);
    }
    return list;
  }, [user?.activeTenantId, activeBranchId, canCollectCash]);

  const handleRealtimeEvent = useCallback(
    (event: RealtimeEventEnvelope) => {
      const payload = event.payload || {};
      const tableNum = typeof payload.tableNumber === 'string' ? payload.tableNumber : '';
      const orderNum = typeof payload.orderNumber === 'string' ? payload.orderNumber : '';

      if (event.eventType === 'ORDER_PLACED') {
        setLiveAlert(`New order ${orderNum}${tableNum ? ` at Table #${tableNum}` : ''} received.`);
      } else if (event.eventType === 'ORDER_READY') {
        setLiveAlert(`Order ${orderNum}${tableNum ? ` (Table #${tableNum})` : ''} is READY for pickup!`);
      } else if (event.eventType === 'PAYMENT_SETTLED') {
        setLiveAlert(`Payment confirmed for bill ${String(payload.billNumber || '')}.`);
      }

      // Always reconcile with authoritative REST state
      queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] });
    },
    [queryClient]
  );

  const handleReconnect = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] });
  }, [queryClient]);

  const { connectionState } = useRealtimeChannel({
    destinations,
    enabled: Boolean(user?.activeTenantId && activeBranchId),
    onEvent: handleRealtimeEvent,
    onReconnect: handleReconnect,
  });

  React.useEffect(() => {
    setWsConnected(connectionState === 'CONNECTED');
  }, [connectionState]);

  const transitionMutation = useMutation({
    mutationFn: ({ orderId, targetStatus, reason }: { orderId: string; targetStatus: string; reason?: string }) =>
      waiterClient.transitionStatus(orderId, targetStatus, reason),
    onSuccess: (_data, vars) => {
      setActionFeedback({ type: 'success', message: `Order updated to ${vars.targetStatus}.` });
      setCancelConfirmOrder(null);
      setCancelReason('');
      queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] });
    },
    onError: (err) => {
      setActionFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Failed to update order status. Refreshing latest state...',
      });
      queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] });
    },
  });

  // Deduplicated assigned tables summary
  const assignedTables = useMemo(() => {
    const tables = new Set<string>();
    orders.forEach((o) => {
      if (o.status !== 'PAID' && o.status !== 'CANCELLED') {
        tables.add(o.tableNumber);
      }
    });
    return Array.from(tables).sort();
  }, [orders]);

  const attentionCount = useMemo(
    () => orders.filter((o) => o.status === 'PLACED' || o.status === 'READY').length,
    [orders]
  );

  const filteredOrders = useMemo(() => {
    const uniqueById = new Map<string, OrderDetail>();
    for (const order of orders) {
      uniqueById.set(order.orderId, order);
    }
    return Array.from(uniqueById.values()).filter((order) => (filter === 'ALL' ? true : order.status === filter));
  }, [orders, filter]);

  const getStatusBadge = (status: OrderDetail['status']) => {
    const map: Record<string, string> = {
      PLACED: 'bg-amber-50 text-amber-800 border-amber-300',
      ACCEPTED: 'bg-blue-50 text-blue-800 border-blue-300',
      PREPARING: 'bg-purple-50 text-purple-800 border-purple-300',
      READY: 'bg-emerald-50 text-emerald-800 border-emerald-300',
      SERVED: 'bg-slate-100 text-slate-700 border-slate-300',
      PAID: 'bg-green-50 text-green-800 border-green-300',
      CANCELLED: 'bg-rose-50 text-rose-800 border-rose-300',
    };
    return (
      <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${map[status] || 'bg-slate-50 text-slate-700 border-slate-200'}`}>
        {status}
      </span>
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-6 pb-5 border-b border-slate-200 gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Waitstaff Live Dispatch</h1>
          <p className="mt-1 text-sm text-slate-500">
            Active station for <span className="font-semibold text-slate-700">{user?.fullName || 'Staff'}</span>
            {dataUpdatedAt ? ` · Synced ${new Date(dataUpdatedAt).toLocaleTimeString()}` : ''}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Connection Status Indicator */}
          <span
            role="status"
            aria-live="polite"
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border ${
              connectionState === 'CONNECTED'
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : connectionState === 'RECONNECTING' || connectionState === 'CONNECTING'
                ? 'bg-amber-50 text-amber-700 border-amber-200'
                : 'bg-slate-100 text-slate-600 border-slate-200'
            }`}
          >
            {connectionState === 'CONNECTED' ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
            {connectionState === 'CONNECTED'
              ? 'Live Connected'
              : connectionState === 'RECONNECTING'
              ? 'Reconnecting (Fallback Sync)'
              : 'Fallback Polling'}
          </span>

          {attentionCount > 0 && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-amber-100 text-amber-900 border border-amber-300">
              <Bell className="w-3.5 h-3.5" />
              {attentionCount} Needs Attention
            </span>
          )}

          <Button variant="outline" size="sm" onClick={() => refetch()} aria-label="Refresh assigned orders">
            <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
            Refresh
          </Button>
        </div>
      </div>

      {/* Live WebSocket Alert Banner */}
      {liveAlert && (
        <div
          role="alert"
          className="mb-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between gap-3 text-sm text-emerald-900"
        >
          <div className="flex items-center gap-2 font-medium">
            <Bell className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>{liveAlert}</span>
          </div>
          <button
            type="button"
            onClick={() => setLiveAlert(null)}
            className="text-xs font-semibold text-emerald-700 hover:text-emerald-900 px-2 py-1"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Mutation Feedback Banner */}
      {actionFeedback && (
        <div
          role="status"
          className={`mb-4 p-3.5 rounded-xl border flex items-center justify-between gap-3 text-sm ${
            actionFeedback.type === 'success'
              ? 'bg-blue-50 border-blue-200 text-blue-900'
              : 'bg-rose-50 border-rose-200 text-rose-900'
          }`}
        >
          <div className="flex items-center gap-2 font-medium">
            {actionFeedback.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-blue-600 shrink-0" />
            ) : (
              <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
            )}
            <span>{actionFeedback.message}</span>
          </div>
          <button
            type="button"
            onClick={() => setActionFeedback(null)}
            className="text-xs font-semibold underline px-2 py-1"
          >
            Close
          </button>
        </div>
      )}

      {/* Assigned Tables Summary Strip */}
      {assignedTables.length > 0 && (
        <div className="mb-5 flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-500 mr-1">Assigned Tables:</span>
          {assignedTables.map((t) => (
            <span key={t} className="px-2.5 py-1 rounded-md text-xs font-bold bg-slate-900 text-white">
              Table #{t}
            </span>
          ))}
        </div>
      )}

      {/* Status Filter Tabs (large touch targets) */}
      <div className="flex flex-wrap gap-2 mb-6" role="tablist" aria-label="Order status filter">
        {(['ALL', 'PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED'] as const).map((s) => (
          <button
            key={s}
            role="tab"
            aria-selected={filter === s}
            onClick={() => setFilter(s)}
            className={`min-h-[40px] px-4 py-2 rounded-lg text-xs font-semibold transition-colors ${
              filter === s
                ? 'bg-slate-900 text-white shadow-sm'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            {s === 'ALL' ? `All Assigned (${orders.length})` : s}
          </button>
        ))}
      </div>

      {/* Main Orders Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" aria-label="Loading orders">
          {[1, 2, 3].map((n) => (
            <Card key={n} className="p-5 animate-pulse space-y-4">
              <div className="h-5 w-1/3 bg-slate-200 rounded" />
              <div className="h-4 w-2/3 bg-slate-100 rounded" />
              <div className="h-20 w-full bg-slate-100 rounded" />
              <div className="h-10 w-full bg-slate-200 rounded" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="p-6 text-center text-rose-700 bg-rose-50 border-rose-200">
          <p className="font-semibold">Failed to load assigned orders.</p>
          <p className="text-xs text-rose-600 mt-1">{error instanceof Error ? error.message : 'Verify your staff session and retry.'}</p>
          <Button size="sm" className="mt-4 bg-rose-600 hover:bg-rose-700 text-white" onClick={() => refetch()}>
            Retry Now
          </Button>
        </Card>
      ) : filteredOrders.length === 0 ? (
        <Card className="p-12 text-center text-slate-500 bg-white">
          <p className="text-base font-semibold text-slate-700">No orders currently assigned</p>
          <p className="mt-1 text-sm text-slate-400">
            New table orders dispatched to your station will appear here automatically.
          </p>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredOrders.map((order) => {
            const needsAttention = order.status === 'PLACED' || order.status === 'READY';
            return (
              <Card
                key={order.orderId}
                className={`flex flex-col justify-between overflow-hidden shadow-sm ${
                  needsAttention ? 'border-amber-400 ring-1 ring-amber-300' : ''
                }`}
              >
                <div className="p-5">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <span className="text-xs font-mono font-medium text-slate-500">{order.orderNumber}</span>
                      <h2 className="text-lg font-bold text-slate-900">Table #{order.tableNumber}</h2>
                    </div>
                    {getStatusBadge(order.status)}
                  </div>

                  <div className="text-xs text-slate-500 mb-4 pb-3 border-b border-slate-100 flex justify-between">
                    <span>Placed {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    <span className="font-semibold text-slate-700">
                      {order.currencyCode} {order.grandTotal.toFixed(2)}
                    </span>
                  </div>

                  {/* Items List */}
                  <div className="space-y-2.5 mb-4">
                    {order.items.map((item) => (
                      <div key={item.itemId} className="text-sm">
                        <div className="flex justify-between items-start">
                          <span className="font-medium text-slate-800">
                            <span className="text-slate-500 font-bold mr-1.5">{item.quantity}x</span>
                            {item.itemName}
                            {item.variantName && <span className="text-xs text-slate-500 ml-1">({item.variantName})</span>}
                          </span>
                        </div>
                        {item.options && item.options.length > 0 && (
                          <div className="pl-5 text-xs text-slate-500">
                            + {item.options.map((o) => o.optionName).join(', ')}
                          </div>
                        )}
                        {item.specialInstructions && (
                          <div className="pl-5 text-xs font-medium text-amber-800">
                            Note: &ldquo;{item.specialInstructions}&rdquo;
                          </div>
                        )}
                      </div>
                    ))}
                  </div>

                  {order.specialNotes && (
                    <div className="p-2.5 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-900 mb-3">
                      <span className="font-semibold">Table Note:</span> {order.specialNotes}
                    </div>
                  )}
                </div>

                {/* Permission-Gated Actions Footer */}
                <div className="p-4 bg-slate-50 border-t border-slate-100 flex flex-col gap-2">
                  {order.status === 'PLACED' && canAcceptOrder && (
                    <Button
                      size="sm"
                      className="w-full min-h-[44px] bg-blue-600 hover:bg-blue-700 text-white font-semibold"
                      disabled={transitionMutation.isPending}
                      onClick={() => transitionMutation.mutate({ orderId: order.orderId, targetStatus: 'ACCEPTED' })}
                    >
                      Accept Order
                    </Button>
                  )}

                  {order.status === 'READY' && canServeOrder && (
                    <Button
                      size="sm"
                      className="w-full min-h-[44px] bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
                      disabled={transitionMutation.isPending}
                      onClick={() => transitionMutation.mutate({ orderId: order.orderId, targetStatus: 'SERVED' })}
                    >
                      Mark as Served
                    </Button>
                  )}

                  {order.status === 'ACCEPTED' && (
                    <span className="text-xs text-center text-blue-700 font-medium py-1">Queued in kitchen</span>
                  )}
                  {order.status === 'PREPARING' && (
                    <span className="text-xs text-center text-purple-700 font-medium py-1">Kitchen preparing</span>
                  )}

                  {order.status === 'SERVED' && (
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs text-slate-600 font-medium">Awaiting payment settlement</span>
                      {canCollectCash && (
                        <Link to="/admin/finance">
                          <Button variant="outline" size="sm">
                            <CreditCard className="w-3.5 h-3.5 mr-1.5" />
                            Cash / Billing
                          </Button>
                        </Link>
                      )}
                    </div>
                  )}

                  {(order.status === 'PLACED' || order.status === 'ACCEPTED') && canCancelOrder && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full text-rose-700 border-rose-200 hover:bg-rose-50"
                      disabled={transitionMutation.isPending}
                      onClick={() => setCancelConfirmOrder(order)}
                    >
                      Cancel Order
                    </Button>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Confirmation Modal for Irreversible Cancellation */}
      {cancelConfirmOrder && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-order-title"
          className="fixed inset-0 z-50 bg-slate-900/50 flex items-center justify-center p-4"
        >
          <Card className="max-w-md w-full p-6 bg-white space-y-4">
            <h3 id="cancel-order-title" className="text-lg font-bold text-slate-900">
              Confirm Order Cancellation
            </h3>
            <p className="text-sm text-slate-600">
              Cancel <span className="font-mono font-semibold">{cancelConfirmOrder.orderNumber}</span> (Table #{cancelConfirmOrder.tableNumber})? This action cannot be undone.
            </p>
            <div>
              <label htmlFor="cancel-reason" className="block text-xs font-semibold text-slate-700 mb-1">
                Cancellation Reason (Required)
              </label>
              <input
                id="cancel-reason"
                type="text"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="e.g., Guest changed mind or item unavailable"
                className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-900"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={() => setCancelConfirmOrder(null)}>
                Keep Order
              </Button>
              <Button
                size="sm"
                className="bg-rose-600 hover:bg-rose-700 text-white"
                disabled={!cancelReason.trim() || transitionMutation.isPending}
                onClick={() =>
                  transitionMutation.mutate({
                    orderId: cancelConfirmOrder.orderId,
                    targetStatus: 'CANCELLED',
                    reason: cancelReason.trim(),
                  })
                }
              >
                Confirm Cancel
              </Button>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
