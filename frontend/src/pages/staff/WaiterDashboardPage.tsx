import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { waiterClient, OrderDetail } from '../../lib/waiterClient';
import { useAuth } from '../../context/AuthContext';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';

export const WaiterDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'ALL' | 'PLACED' | 'ACCEPTED' | 'READY' | 'SERVED'>('ALL');

  // Fetch assigned orders for calling waiter
  const { data: orders = [], isLoading, error } = useQuery({
    queryKey: ['waiter-assigned-orders'],
    queryFn: () => waiterClient.getMyAssignedOrders(),
    refetchInterval: 10000,
  });

  // Transition mutation
  const transitionMutation = useMutation({
    mutationFn: ({ orderId, targetStatus }: { orderId: string; targetStatus: string }) =>
      waiterClient.transitionStatus(orderId, targetStatus),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] });
    },
  });

  const filteredOrders = orders.filter((order) => {
    if (filter === 'ALL') return true;
    return order.status === filter;
  });

  const getStatusBadge = (status: OrderDetail['status']) => {
    const map: Record<string, string> = {
      PLACED: 'bg-amber-50 text-amber-700 border-amber-200',
      ACCEPTED: 'bg-blue-50 text-blue-700 border-blue-200',
      PREPARING: 'bg-purple-50 text-purple-700 border-purple-200',
      READY: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      SERVED: 'bg-slate-100 text-slate-700 border-slate-200',
      PAID: 'bg-green-50 text-green-700 border-green-200',
      CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200',
    };
    return (
      <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${map[status] || 'bg-slate-50 text-slate-700 border-slate-200'}`}>
        {status}
      </span>
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="md:flex md:items-center md:justify-between mb-8 pb-5 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Waitstaff Active Dispatch</h1>
          <p className="mt-1 text-sm text-slate-500">
            Welcome back, <span className="font-semibold text-slate-700">{user?.fullName}</span>. Manage table orders assigned to your station.
          </p>
        </div>
        <div className="mt-4 md:mt-0 flex items-center gap-3">
          <span className="inline-flex items-center px-3 py-1 rounded-md text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
            ● Active On Duty
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['waiter-assigned-orders'] })}
          >
            Refresh
          </Button>
        </div>
      </div>

      {/* Status Filters */}
      <div className="flex flex-wrap gap-2 mb-6">
        {(['ALL', 'PLACED', 'ACCEPTED', 'READY', 'SERVED'] as const).map((s) => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              filter === s
                ? 'bg-slate-900 text-white shadow-sm'
                : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            {s === 'ALL' ? 'All Assigned' : s}
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="text-center py-16">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-slate-900 mx-auto"></div>
          <p className="mt-3 text-sm text-slate-500">Loading assigned table orders...</p>
        </div>
      ) : error ? (
        <Card className="p-6 text-center text-rose-600 bg-rose-50 border-rose-200">
          Failed to load assigned orders. Please verify your staff session.
        </Card>
      ) : filteredOrders.length === 0 ? (
        <Card className="p-12 text-center text-slate-500 bg-white">
          <p className="text-base font-medium text-slate-700">No orders currently assigned</p>
          <p className="mt-1 text-sm text-slate-400">
            Orders dispatched to your station by floor managers will appear here in real time.
          </p>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredOrders.map((order) => (
            <Card key={order.orderId} className="flex flex-col justify-between overflow-hidden shadow-sm hover:shadow-md transition-shadow">
              <div className="p-5">
                <div className="flex items-center justify-between mb-3">
                  <div>
                    <span className="text-xs font-mono font-medium text-slate-500">{order.orderNumber}</span>
                    <h3 className="text-lg font-bold text-slate-900">Table #{order.tableNumber}</h3>
                  </div>
                  {getStatusBadge(order.status)}
                </div>

                <div className="text-xs text-slate-500 mb-4 pb-3 border-b border-slate-100 flex justify-between">
                  <span>Placed {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                  <span className="font-semibold text-slate-700">
                    {order.currencyCode} {order.grandTotal.toFixed(2)}
                  </span>
                </div>

                {/* Items list */}
                <div className="space-y-2 mb-4">
                  {order.items.map((item) => (
                    <div key={item.itemId} className="text-sm">
                      <div className="flex justify-between items-start">
                        <span className="font-medium text-slate-800">
                          <span className="text-slate-400 mr-1.5">{item.quantity}x</span>
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
                        <div className="pl-5 text-xs italic text-amber-700">
                          Note: "{item.specialInstructions}"
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {order.specialNotes && (
                  <div className="p-2.5 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 mb-3">
                    <span className="font-semibold">Table Note:</span> {order.specialNotes}
                  </div>
                )}
              </div>

              {/* Actions Footer */}
              <div className="p-4 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-2">
                {order.status === 'PLACED' && (
                  <Button
                    size="sm"
                    className="w-full bg-blue-600 hover:bg-blue-700 text-white"
                    disabled={transitionMutation.isPending}
                    onClick={() => transitionMutation.mutate({ orderId: order.orderId, targetStatus: 'ACCEPTED' })}
                  >
                    Accept Order
                  </Button>
                )}
                {order.status === 'READY' && (
                  <Button
                    size="sm"
                    className="w-full bg-emerald-600 hover:bg-emerald-700 text-white"
                    disabled={transitionMutation.isPending}
                    onClick={() => transitionMutation.mutate({ orderId: order.orderId, targetStatus: 'SERVED' })}
                  >
                    Mark as Served
                  </Button>
                )}
                {order.status === 'ACCEPTED' && (
                  <span className="text-xs text-purple-600 font-medium py-1">In kitchen queue</span>
                )}
                {order.status === 'PREPARING' && (
                  <span className="text-xs text-purple-600 font-medium py-1">Kitchen cooking</span>
                )}
                {order.status === 'SERVED' && (
                  <span className="text-xs text-slate-500 py-1">Awaiting cashier billing</span>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
