import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { kitchenClient, KitchenOrder } from '../../lib/kitchenClient';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';

export const KitchenDisplayPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'ALL' | 'ACCEPTED' | 'PREPARING' | 'READY'>('ALL');

  // Authenticated polling: refreshes kitchen orders every 5 seconds
  const { data: orders = [], isLoading, isError, error, dataUpdatedAt } = useQuery({
    queryKey: ['kitchen-queue'],
    queryFn: () => kitchenClient.getQueue(),
    refetchInterval: 5000,
  });

  const prepareMutation = useMutation({
    mutationFn: (orderId: string) => kitchenClient.startPreparation(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const readyMutation = useMutation({
    mutationFn: (orderId: string) => kitchenClient.markReady(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] });
    },
  });

  const filteredOrders = orders.filter((o) => {
    if (filter === 'ALL') return true;
    return o.status === filter;
  });

  const getStatusBadge = (status: KitchenOrder['status']) => {
    switch (status) {
      case 'ACCEPTED':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-blue-100 text-blue-800 border border-blue-300 animate-pulse">
            Queued
          </span>
        );
      case 'PREPARING':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-amber-100 text-amber-800 border border-amber-300">
            Cooking
          </span>
        );
      case 'READY':
        return (
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-emerald-100 text-emerald-800 border border-emerald-300">
            Ready
          </span>
        );
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-6">
      {/* Top Bar for Kitchen Display */}
      <div className="flex flex-col md:flex-row md:items-center justify-between pb-6 mb-6 border-b border-slate-800 gap-4">
        <div className="flex items-center gap-3">
          <div className="w-4 h-4 rounded-full bg-emerald-500 animate-ping"></div>
          <h1 className="text-3xl font-extrabold tracking-tight text-white">DineFlow KDS</h1>
          <span className="text-xs uppercase font-mono px-2.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
            Station Live
          </span>
        </div>

        <div className="flex items-center gap-4">
          <div className="text-xs text-slate-400 font-mono">
            Last sync: {dataUpdatedAt ? new Date(dataUpdatedAt).toLocaleTimeString() : 'connecting...'}
          </div>

          {/* Status Filter Tabs */}
          <div className="flex rounded-lg bg-slate-900 p-1 border border-slate-800">
            {(['ALL', 'ACCEPTED', 'PREPARING', 'READY'] as const).map((s) => (
              <button
                key={s}
                onClick={() => setFilter(s)}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                  filter === s ? 'bg-amber-500 text-slate-950' : 'text-slate-400 hover:text-white'
                }`}
              >
                {s === 'ALL' ? 'All Active' : s}
              </button>
            ))}
          </div>

          <Button
            variant="outline"
            size="sm"
            className="border-slate-700 text-slate-300 hover:bg-slate-800"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] })}
          >
            Refresh
          </Button>
        </div>
      </div>

      {/* Main KDS Grid */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-24">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-500 mb-4"></div>
          <p className="text-slate-400 font-mono text-sm">Loading live preparation tickets...</p>
        </div>
      ) : isError ? (
        <Card className="p-8 text-center bg-rose-950 border-rose-800 text-rose-200 max-w-xl mx-auto my-12">
          <h2 className="text-xl font-bold mb-2">Kitchen Network Connection Error</h2>
          <p className="text-sm text-rose-300 mb-4">
            {error instanceof Error ? error.message : 'Could not synchronize with restaurant order server.'}
          </p>
          <Button
            className="bg-rose-600 hover:bg-rose-700 text-white"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] })}
          >
            Retry Connection
          </Button>
        </Card>
      ) : filteredOrders.length === 0 ? (
        <div className="text-center py-28 border-2 border-dashed border-slate-800 rounded-2xl max-w-2xl mx-auto">
          <p className="text-4xl mb-3">🍳</p>
          <h3 className="text-xl font-bold text-slate-200">Kitchen Queue Clear</h3>
          <p className="text-sm text-slate-500 mt-1">
            All customer dining orders have been cooked and dispatched. New incoming orders will appear automatically.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {filteredOrders.map((order) => {
            const isCooking = order.status === 'PREPARING';
            const isReady = order.status === 'READY';

            return (
              <div
                key={order.orderId}
                className={`flex flex-col justify-between rounded-xl border bg-slate-900 overflow-hidden shadow-lg transition-all ${
                  isReady
                    ? 'border-emerald-600 ring-2 ring-emerald-600/20'
                    : isCooking
                    ? 'border-amber-500 ring-2 ring-amber-500/20'
                    : 'border-slate-800 hover:border-slate-700'
                }`}
              >
                {/* Order Header */}
                <div className="p-4 bg-slate-950/70 border-b border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-xs font-semibold text-amber-400 bg-amber-950/50 px-2 py-0.5 rounded border border-amber-800/50">
                      {order.orderNumber}
                    </span>
                    {getStatusBadge(order.status)}
                  </div>
                  <div className="flex items-baseline justify-between">
                    <h2 className="text-2xl font-black text-white">Table #{order.tableNumber}</h2>
                    <span className="text-xs text-slate-400 font-mono">
                      {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                </div>

                {/* Items & Add-ons List */}
                <div className="p-4 space-y-4 flex-1">
                  {order.items.map((item) => (
                    <div key={item.itemId} className="border-b border-slate-800/80 pb-3 last:border-b-0 last:pb-0">
                      <div className="flex items-baseline gap-2">
                        <span className="text-lg font-black text-amber-400 bg-slate-800 px-2 py-0.5 rounded">
                          {item.quantity}x
                        </span>
                        <span className="text-base font-bold text-white leading-tight">
                          {item.itemName}
                          {item.variantName && (
                            <span className="text-sm font-medium text-slate-300 ml-1.5">
                              [{item.variantName}]
                            </span>
                          )}
                        </span>
                      </div>

                      {item.options && item.options.length > 0 && (
                        <div className="pl-9 mt-1 text-xs font-medium text-emerald-400">
                          + {item.options.join(', ')}
                        </div>
                      )}

                      {item.specialInstructions && (
                        <div className="pl-9 mt-1 text-xs font-semibold italic text-amber-300">
                          ⚠️ "{item.specialInstructions}"
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

                {/* Status Advancement Action Footer */}
                <div className="p-4 bg-slate-950/80 border-t border-slate-800">
                  {order.status === 'ACCEPTED' && (
                    <Button
                      size="sm"
                      className="w-full bg-amber-500 hover:bg-amber-600 text-slate-950 font-bold"
                      disabled={prepareMutation.isPending}
                      onClick={() => prepareMutation.mutate(order.orderId)}
                    >
                      Start Cooking
                    </Button>
                  )}
                  {order.status === 'PREPARING' && (
                    <Button
                      size="sm"
                      className="w-full bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold"
                      disabled={readyMutation.isPending}
                      onClick={() => readyMutation.mutate(order.orderId)}
                    >
                      Mark as Ready
                    </Button>
                  )}
                  {order.status === 'READY' && (
                    <div className="text-center py-1 text-xs font-bold text-emerald-400 tracking-wide uppercase">
                      ✓ Ready for Waiter Pickup
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
