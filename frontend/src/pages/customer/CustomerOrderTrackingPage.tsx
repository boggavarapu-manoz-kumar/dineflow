import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customerOrderClient, CustomerOrder } from '@/lib/customerOrderClient';
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
} from 'lucide-react';

export const CustomerOrderTrackingPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [cancelReason, setCancelReason] = useState('');
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);

  // Poll order state every 4 seconds to observe kitchen/waiter updates in real-time
  const { data: order, isLoading, isError, error } = useQuery({
    queryKey: ['customer-order', orderId],
    queryFn: () => customerOrderClient.getOrder(orderId!),
    enabled: Boolean(orderId),
    refetchInterval: (query) => {
      const current = query.state.data;
      if (current?.status === 'PAID' || current?.status === 'CANCELLED') {
        return false; // Terminal states, stop polling
      }
      return 4000;
    },
  });

  // Query order history
  const { data: history = [] } = useQuery({
    queryKey: ['customer-order-history', orderId],
    queryFn: () => customerOrderClient.getOrderHistory(orderId!),
    enabled: Boolean(orderId),
    refetchInterval: 5000,
  });

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
    {
      status: 'PLACED',
      label: 'Order Placed',
      description: 'Received by restaurant system',
      icon: Clock,
    },
    {
      status: 'ACCEPTED',
      label: 'Order Accepted',
      description: 'Acknowledged by service team',
      icon: CheckCircle2,
    },
    {
      status: 'PREPARING',
      label: 'Kitchen Cooking',
      description: 'Dishes are being prepared',
      icon: ChefHat,
    },
    {
      status: 'READY',
      label: 'Food Ready',
      description: 'Plated & awaiting table runner',
      icon: BellRing,
    },
    {
      status: 'SERVED',
      label: 'Served to Table',
      description: 'Enjoy your fresh meal',
      icon: UtensilsCrossed,
    },
  ];

  const getStageIndex = (status?: CustomerOrder['status']) => {
    switch (status) {
      case 'PLACED':
        return 0;
      case 'ACCEPTED':
        return 1;
      case 'PREPARING':
        return 2;
      case 'READY':
        return 3;
      case 'SERVED':
      case 'PAID':
        return 4;
      case 'CANCELLED':
        return -1;
      default:
        return 0;
    }
  };

  const currentStageIndex = getStageIndex(order?.status);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-stone-50 flex items-center justify-center p-4">
        <div className="text-center space-y-3">
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
            <h3 className="text-lg font-bold text-slate-900">Order Not Accessible</h3>
            <p className="text-xs text-stone-500 mt-1">
              {error instanceof Error ? error.message : 'You may only view orders belonging to your active dining session.'}
            </p>
          </div>
          <Button variant="emerald" size="md" className="w-full" onClick={() => navigate('/customer/menu')}>
            Return to Digital Menu
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-stone-50 text-slate-900 pb-16">
      {/* Top Banner */}
      <div className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-stone-200 px-4 py-3 flex items-center justify-between shadow-2xs">
        <Link
          to="/customer/menu"
          className="flex items-center gap-1.5 text-xs font-bold text-stone-600 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Menu</span>
        </Link>
        <div className="text-center">
          <span className="text-[10px] font-mono font-bold text-stone-400 uppercase tracking-widest block">
            {order.orderNumber}
          </span>
          <span className="text-xs font-black text-slate-900">Table #{order.tableNumber}</span>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['customer-order', orderId] })}
          className="p-1.5 text-stone-400 hover:text-slate-700 rounded-lg hover:bg-stone-100 transition-colors cursor-pointer"
          title="Refresh"
        >
          <RefreshCw className="w-3.5 h-3.5" />
        </button>
      </div>

      <div className="max-w-xl mx-auto px-4 py-6 space-y-6">
        {/* Order Status Hero Card */}
        <Card className="p-6 text-center bg-white border border-stone-200 shadow-sm relative overflow-hidden">
          {order.status === 'CANCELLED' ? (
            <div className="space-y-3">
              <div className="w-14 h-14 bg-rose-100 text-rose-600 rounded-2xl flex items-center justify-center mx-auto">
                <XCircle className="w-7 h-7" />
              </div>
              <h2 className="text-xl font-extrabold text-slate-900">Order Cancelled</h2>
              <p className="text-xs text-stone-500 max-w-sm mx-auto">
                This table order was cancelled. Please speak with floor staff or place a new order from the digital menu.
              </p>
            </div>
          ) : (
            <div className="space-y-6">
              <div>
                <span className="inline-block px-3 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 mb-2 uppercase tracking-wider">
                  ● Live Kitchen Tracker
                </span>
                <h2 className="text-2xl font-black text-slate-900">
                  {order.status === 'PLACED' && 'Order Received'}
                  {order.status === 'ACCEPTED' && 'Accepted by Service'}
                  {order.status === 'PREPARING' && 'Chef is Preparing'}
                  {order.status === 'READY' && 'Plated & Ready'}
                  {order.status === 'SERVED' && 'Enjoy Your Meal!'}
                  {order.status === 'PAID' && 'Bill Paid'}
                </h2>
                <p className="text-xs text-stone-500 mt-1">
                  Placed at {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} • Synchronized
                </p>
              </div>

              {/* Progress Stepper */}
              <div className="space-y-4 text-left">
                {stages.map((stage, idx) => {
                  const Icon = stage.icon;
                  const isDone = currentStageIndex >= idx;
                  const isCurrent = currentStageIndex === idx;

                  return (
                    <div key={stage.status} className="flex items-start gap-3.5 relative">
                      {idx < stages.length - 1 && (
                        <div
                          className={`absolute left-4 top-8 -bottom-4 w-0.5 ${
                            isDone && currentStageIndex > idx ? 'bg-emerald-600' : 'bg-stone-200'
                          }`}
                        />
                      )}
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10 transition-all ${
                          isDone
                            ? 'bg-emerald-600 text-white shadow-sm shadow-emerald-600/30 ring-4 ring-emerald-50'
                            : 'bg-stone-100 text-stone-400'
                        }`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="pt-0.5">
                        <p className={`text-xs font-bold ${isCurrent ? 'text-emerald-700 font-extrabold' : isDone ? 'text-slate-900' : 'text-stone-400'}`}>
                          {stage.label}
                        </p>
                        <p className="text-[11px] text-stone-500">{stage.description}</p>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Customer Cancellation Allowed Only While PLACED */}
              {order.status === 'PLACED' && (
                <div className="pt-4 border-t border-stone-100">
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-rose-600 border-rose-200 hover:bg-rose-50 text-xs"
                    onClick={() => setIsCancelModalOpen(true)}
                  >
                    Cancel Order
                  </Button>
                </div>
              )}
            </div>
          )}
        </Card>

        {/* Order Details Accordion / Breakdown */}
        <Card className="p-5 bg-white border border-stone-200 space-y-4">
          <h3 className="font-bold text-sm text-slate-900 border-b border-stone-100 pb-2">Dishes in this Order</h3>
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
                  <div className="pl-6 text-[11px] text-amber-700 italic mt-0.5">
                    "{item.specialInstructions}"
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Pricing Totals */}
          <div className="pt-3 border-t border-stone-100 space-y-1.5 text-xs text-stone-600">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="font-mono">{order.currencyCode} {order.subtotal.toFixed(2)}</span>
            </div>
            {order.taxAmount > 0 && (
              <div className="flex justify-between">
                <span>Taxes & GST ({order.taxPercentage}%)</span>
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
              <span>Authoritative Total</span>
              <span className="text-emerald-700 font-mono text-base">{order.currencyCode} {order.grandTotal.toFixed(2)}</span>
            </div>
          </div>
        </Card>

        {/* Audit Log / Status History */}
        {history.length > 0 && (
          <Card className="p-5 bg-white border border-stone-200 space-y-3">
            <h4 className="text-xs font-bold text-stone-500 uppercase tracking-wider">Service Timeline</h4>
            <div className="space-y-2 text-xs">
              {history.map((h) => (
                <div key={h.id} className="flex justify-between items-center text-stone-600 border-b border-stone-50 pb-1.5 last:border-b-0">
                  <div>
                    <span className="font-bold text-slate-800">{h.newStatus}</span>
                    {h.reason && <span className="text-[11px] text-stone-400 block">{h.reason}</span>}
                  </div>
                  <span className="text-[10px] text-stone-400 font-mono">
                    {new Date(h.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                  </span>
                </div>
              ))}
            </div>
          </Card>
        )}
      </div>

      {/* Cancellation Modal */}
      {isCancelModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4">
          <Card className="max-w-md w-full p-6 space-y-4 bg-white border border-stone-200 animate-in fade-in zoom-in-95">
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
              className="w-full text-xs p-3 rounded-xl border border-stone-200 focus:outline-hidden focus:ring-2 focus:ring-rose-500/20"
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
