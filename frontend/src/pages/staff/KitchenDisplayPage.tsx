import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Clock, RefreshCw, Wifi, WifiOff, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { kitchenClient, KitchenOrder } from '@/lib/kitchenClient';
import { useAuth } from '@/context/AuthContext';
import { useRealtimeChannel } from '@/hooks/useRealtimeChannel';
import { RealtimeEventEnvelope } from '@/lib/realtimeClient';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const KitchenDisplayPage: React.FC = () => {
  const { user, hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'ALL' | 'ACCEPTED' | 'PREPARING' | 'READY'>('ALL');
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [wsConnected, setWsConnected] = useState(false);
  const [liveNotice, setLiveNotice] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const role = user?.role?.toUpperCase() || '';
  const canUpdateKitchen =
    ['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER', 'KITCHEN_STAFF'].includes(role) ||
    hasPermission('KITCHEN_UPDATE') ||
    hasPermission('ORDER_MANAGE');

  // Tick elapsed minutes every 15s for live kitchen SLA visibility
  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), 15000);
    return () => clearInterval(id);
  }, []);

  const {
    data: orders = [],
    isLoading,
    isError,
    error,
    dataUpdatedAt,
    refetch,
  } = useQuery({
    queryKey: ['kitchen-queue'],
    queryFn: () => kitchenClient.getQueue(),
    refetchInterval: wsConnected ? 20000 : 5000,
  });

  const activeBranchId = user?.branchId || orders.find((o) => o.branchId)?.branchId || null;
  const destinations = useMemo(() => {
    if (!user?.activeTenantId || !activeBranchId) return [];
    return [`/topic/tenant/${user.activeTenantId}/branch/${activeBranchId}/kitchen`];
  }, [user?.activeTenantId, activeBranchId]);

  const handleRealtimeEvent = useCallback(
    (event: RealtimeEventEnvelope) => {
      const payload = event.payload || {};
      const orderNum = typeof payload.orderNumber === 'string' ? payload.orderNumber : '';
      const tableNum = typeof payload.tableNumber === 'string' ? payload.tableNumber : '';

      if (event.eventType === 'ORDER_ACCEPTED') {
        setLiveNotice(`New kitchen ticket ${orderNum}${tableNum ? ` (Table #${tableNum})` : ''}`);
      } else if (event.eventType === 'ORDER_CANCELLED') {
        setLiveNotice(`Ticket ${orderNum} was cancelled.`);
      }
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
    [queryClient]
  );

  const handleReconnect = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
  }, [queryClient]);

  const { connectionState } = useRealtimeChannel({
    destinations,
    enabled: Boolean(user?.activeTenantId && activeBranchId),
    onEvent: handleRealtimeEvent,
    onReconnect: handleReconnect,
  });

  useEffect(() => {
    setWsConnected(connectionState === 'CONNECTED');
  }, [connectionState]);

  const prepareMutation = useMutation({
    mutationFn: (orderId: string) => kitchenClient.startPreparation(orderId),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
    onError: (err) => {
      setMutationError(err instanceof Error ? err.message : 'Failed to start preparation. Refreshing queue...');
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const readyMutation = useMutation({
    mutationFn: (orderId: string) => kitchenClient.markReady(orderId),
    onSuccess: () => {
      setMutationError(null);
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
    onError: (err) => {
      setMutationError(err instanceof Error ? err.message : 'Failed to mark order ready. Refreshing queue...');
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const filteredOrders = useMemo(() => {
    const uniqueById = new Map<string, KitchenOrder>();
    for (const order of orders) {
      uniqueById.set(order.orderId, order);
    }
    return Array.from(uniqueById.values()).filter((o) => (filter === 'ALL' ? true : o.status === filter));
  }, [orders, filter]);

  const getElapsedMinutes = (isoTime: string) => {
    const diffMs = Math.max(0, nowMs - new Date(isoTime).getTime());
    return Math.floor(diffMs / 60000);
  };

  const getStatusBadge = (status: KitchenOrder['status']) => {
    switch (status) {
      case 'ACCEPTED':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-blue-100 text-blue-900 border border-blue-300">
            Queued
          </span>
        );
      case 'PREPARING':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-amber-100 text-amber-900 border border-amber-300">
            Cooking
          </span>
        );
      case 'READY':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-emerald-100 text-emerald-900 border border-emerald-300">
            Ready
          </span>
        );
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4 sm:p-6">
      {/* Top Bar for Kitchen Display */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between pb-5 mb-5 border-b border-slate-800 gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">Kitchen Display System</h1>
          <span
            role="status"
            aria-live="polite"
            className={`inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-lg border ${
              connectionState === 'CONNECTED'
                ? 'bg-emerald-950 text-emerald-300 border-emerald-700'
                : 'bg-amber-950 text-amber-300 border-amber-700'
            }`}
          >
            {connectionState === 'CONNECTED' ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
            {connectionState === 'CONNECTED' ? 'Live Connected' : 'Fallback Polling'}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="text-xs text-slate-400 font-mono">
            Last sync: {dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString() : 'syncing...'}
          </div>

          <div className="flex rounded-lg bg-slate-900 p-1 border border-slate-800" role="tablist" aria-label="Kitchen status filter">
            {(['ALL', 'ACCEPTED', 'PREPARING', 'READY'] as const).map((s) => (
              <button
                key={s}
                role="tab"
                aria-selected={filter === s}
                onClick={() => setFilter(s)}
                className={`min-h-[36px] px-3 py-1.5 rounded-md text-xs font-semibold transition-colors ${
                  filter === s ? 'bg-amber-500 text-slate-950' : 'text-slate-300 hover:text-white'
                }`}
              >
                {s === 'ALL' ? `All (${orders.length})` : s}
              </button>
            ))}
          </div>

          <Button
            variant="outline"
            size="sm"
            className="border-slate-700 text-slate-200 hover:bg-slate-800"
            onClick={() => refetch()}
            aria-label="Refresh kitchen queue"
          >
            <RefreshCw className="w-3.5 h-3.5 mr-1.5" />
            Refresh
          </Button>
        </div>
      </div>

      {/* Live Notice & Mutation Error Banners */}
      {liveNotice && (
        <div role="alert" className="mb-4 p-3 rounded-xl bg-amber-950/70 border border-amber-700 text-amber-200 flex items-center justify-between text-sm">
          <span className="font-semibold">{liveNotice}</span>
          <button type="button" onClick={() => setLiveNotice(null)} className="text-xs font-semibold underline px-2">
            Dismiss
          </button>
        </div>
      )}

      {mutationError && (
        <div role="alert" className="mb-4 p-3 rounded-xl bg-rose-950 border border-rose-700 text-rose-200 flex items-center justify-between text-sm">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
            <span>{mutationError}</span>
          </div>
          <button type="button" onClick={() => setMutationError(null)} className="text-xs font-semibold underline px-2">
            Dismiss
          </button>
        </div>
      )}

      {/* Main KDS Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5" aria-label="Loading kitchen tickets">
          {[1, 2, 3, 4].map((n) => (
            <div key={n} className="rounded-xl border border-slate-800 bg-slate-900 p-5 animate-pulse space-y-4">
              <div className="h-5 w-1/2 bg-slate-800 rounded" />
              <div className="h-24 w-full bg-slate-800 rounded" />
              <div className="h-11 w-full bg-slate-800 rounded" />
            </div>
          ))}
        </div>
      ) : isError ? (
        <Card className="p-8 text-center bg-rose-950 border-rose-800 text-rose-200 max-w-xl mx-auto my-12">
          <h2 className="text-xl font-bold mb-2">Kitchen Connection Error</h2>
          <p className="text-sm text-rose-300 mb-4">
            {error instanceof Error ? error.message : 'Could not synchronize with restaurant order server.'}
          </p>
          <Button className="bg-rose-600 hover:bg-rose-700 text-white" onClick={() => refetch()}>
            Retry Connection
          </Button>
        </Card>
      ) : filteredOrders.length === 0 ? (
        <div className="text-center py-24 border-2 border-dashed border-slate-800 rounded-2xl max-w-2xl mx-auto">
          <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto mb-3" />
          <h2 className="text-xl font-bold text-slate-200">Kitchen Queue Clear</h2>
          <p className="text-sm text-slate-400 mt-1">
            All active tickets have been prepared. New accepted orders will appear automatically.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {filteredOrders.map((order) => {
            const elapsedMin = getElapsedMinutes(order.acceptedAt || order.createdAt);
            const isOverdue = order.status !== 'READY' && elapsedMin >= 20;
            const isAttention = order.status !== 'READY' && elapsedMin >= 10 && elapsedMin < 20;
            const isCooking = order.status === 'PREPARING';
            const isReady = order.status === 'READY';

            return (
              <div
                key={order.orderId}
                className={`flex flex-col justify-between rounded-xl border bg-slate-900 overflow-hidden shadow-lg ${
                  isOverdue
                    ? 'border-rose-500 ring-2 ring-rose-500/30'
                    : isReady
                    ? 'border-emerald-600 ring-1 ring-emerald-600/30'
                    : isCooking || isAttention
                    ? 'border-amber-500 ring-1 ring-amber-500/20'
                    : 'border-slate-800'
                }`}
              >
                {/* Ticket Header */}
                <div className="p-4 bg-slate-950/70 border-b border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-xs font-semibold text-amber-400 bg-amber-950/50 px-2 py-0.5 rounded border border-amber-800/50">
                      {order.orderNumber}
                    </span>
                    {getStatusBadge(order.status)}
                  </div>
                  <div className="flex items-baseline justify-between">
                    <h2 className="text-2xl font-black text-white">Table #{order.tableNumber}</h2>
                    <span
                      className={`inline-flex items-center gap-1 text-xs font-mono font-bold px-2 py-0.5 rounded ${
                        isOverdue
                          ? 'bg-rose-900 text-rose-200'
                          : isAttention
                          ? 'bg-amber-900 text-amber-200'
                          : 'text-slate-400'
                      }`}
                    >
                      <Clock className="w-3 h-3" />
                      {elapsedMin}m
                    </span>
                  </div>
                  <div className="mt-1 text-[11px] text-slate-400 font-mono">
                    Received {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    {isOverdue ? ' · PRIORITY OVERDUE' : ''}
                  </div>
                </div>

                {/* Items & Add-ons */}
                <div className="p-4 space-y-3.5 flex-1">
                  {order.items.map((item) => (
                    <div key={item.itemId} className="border-b border-slate-800/80 pb-3 last:border-b-0 last:pb-0">
                      <div className="flex items-baseline gap-2">
                        <span className="text-lg font-black text-amber-400 bg-slate-800 px-2 py-0.5 rounded">
                          {item.quantity}x
                        </span>
                        <span className="text-base font-bold text-white leading-tight">
                          {item.itemName}
                          {item.variantName && (
                            <span className="text-sm font-medium text-slate-300 ml-1.5">[{item.variantName}]</span>
                          )}
                        </span>
                      </div>

                      {item.options && item.options.length > 0 && (
                        <div className="pl-9 mt-1 text-xs font-medium text-emerald-400">
                          + {item.options.join(', ')}
                        </div>
                      )}

                      {item.specialInstructions && (
                        <div className="pl-9 mt-1 text-xs font-semibold text-amber-300">
                          Instruction: &ldquo;{item.specialInstructions}&rdquo;
                        </div>
                      )}
                    </div>
                  ))}

                  {order.specialNotes && (
                    <div className="mt-3 p-2.5 rounded bg-amber-950/40 border border-amber-800/50 text-xs text-amber-300">
                      <span className="font-bold">Order Note:</span> {order.specialNotes}
                    </div>
                  )}
                </div>

                {/* Action Footer (Large touch targets, strictly permission-gated) */}
                <div className="p-4 bg-slate-950/80 border-t border-slate-800">
                  {order.status === 'ACCEPTED' && canUpdateKitchen && (
                    <Button
                      size="sm"
                      className="w-full min-h-[48px] bg-amber-500 hover:bg-amber-600 text-slate-950 font-bold text-sm"
                      disabled={prepareMutation.isPending}
                      onClick={() => prepareMutation.mutate(order.orderId)}
                    >
                      Start Preparation
                    </Button>
                  )}
                  {order.status === 'PREPARING' && canUpdateKitchen && (
                    <Button
                      size="sm"
                      className="w-full min-h-[48px] bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold text-sm"
                      disabled={readyMutation.isPending}
                      onClick={() => readyMutation.mutate(order.orderId)}
                    >
                      Mark Ready
                    </Button>
                  )}
                  {order.status === 'READY' && (
                    <div className="text-center py-2 text-xs font-bold text-emerald-400 tracking-wide uppercase">
                      Ready for Waiter Pickup
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
