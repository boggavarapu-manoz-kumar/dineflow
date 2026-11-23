import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  promotionClient,
  PromotionResponse,
  CreatePromotionRequest,
  DiscountType,
  PromotionScope,
} from '@/lib/promotionClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  Tag,
  Plus,
  CheckCircle2,
  XCircle,
  Clock,
  TrendingUp,
  Percent,
  Ticket,
  Eye,
  Power,
  Loader2,
  AlertCircle,
} from 'lucide-react';

export const PromotionManagementPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedPromoForRedemptions, setSelectedPromoForRedemptions] = useState<PromotionResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const [formState, setFormState] = useState<{
    name: string;
    description: string;
    couponCode: string;
    discountType: DiscountType;
    discountValue: string;
    scope: PromotionScope;
    minOrderValue: string;
    maxDiscountAmount: string;
    startsAt: string;
    endsAt: string;
    usageLimit: string;
    perCustomerLimit: string;
    applyBeforeTax: boolean;
    combinable: boolean;
  }>({
    name: '',
    description: '',
    couponCode: '',
    discountType: 'PERCENTAGE',
    discountValue: '15',
    scope: 'ORDER_LEVEL',
    minOrderValue: '500',
    maxDiscountAmount: '250',
    startsAt: new Date().toISOString().slice(0, 16),
    endsAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16),
    usageLimit: '100',
    perCustomerLimit: '1',
    applyBeforeTax: true,
    combinable: false,
  });

  const { data: promotions = [], isLoading } = useQuery({
    queryKey: ['admin-promotions'],
    queryFn: () => promotionClient.list(),
  });

  const { data: analytics } = useQuery({
    queryKey: ['admin-promotions-analytics'],
    queryFn: () => promotionClient.getAnalytics(),
  });

  const { data: redemptions = [], isLoading: loadingRedemptions } = useQuery({
    queryKey: ['promotion-redemptions', selectedPromoForRedemptions?.id],
    queryFn: () => promotionClient.getRedemptions(selectedPromoForRedemptions!.id),
    enabled: Boolean(selectedPromoForRedemptions),
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreatePromotionRequest) => promotionClient.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-promotions'] });
      queryClient.invalidateQueries({ queryKey: ['admin-promotions-analytics'] });
      setShowCreateModal(false);
      setFormError(null);
    },
    onError: (err: any) => {
      setFormError(err?.message || 'Failed to create promotion');
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => promotionClient.toggleStatus(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-promotions'] });
      queryClient.invalidateQueries({ queryKey: ['admin-promotions-analytics'] });
    },
  });

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!formState.name.trim()) {
      setFormError('Promotion name is required');
      return;
    }
    createMutation.mutate({
      name: formState.name.trim(),
      description: formState.description.trim() || undefined,
      couponCode: formState.couponCode.trim() ? formState.couponCode.trim().toUpperCase() : undefined,
      discountType: formState.discountType,
      discountValue: parseFloat(formState.discountValue),
      scope: formState.scope,
      minOrderValue: formState.minOrderValue ? parseFloat(formState.minOrderValue) : 0,
      maxDiscountAmount: formState.maxDiscountAmount ? parseFloat(formState.maxDiscountAmount) : undefined,
      startsAt: new Date(formState.startsAt).toISOString(),
      endsAt: new Date(formState.endsAt).toISOString(),
      usageLimit: formState.usageLimit ? parseInt(formState.usageLimit, 10) : undefined,
      perCustomerLimit: formState.perCustomerLimit ? parseInt(formState.perCustomerLimit, 10) : undefined,
      applyBeforeTax: formState.applyBeforeTax,
      combinable: formState.combinable,
      active: true,
    });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center gap-3">
            <span className="p-2.5 rounded-xl bg-emerald-600 text-white shadow-md shadow-emerald-600/20">
              <Tag className="w-6 h-6" />
            </span>
            Promotions & Coupon Engine
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Configure multi-tenant discounts, coupon codes, usage limits, and track real-time redemptions.
          </p>
        </div>
        <Button
          onClick={() => setShowCreateModal(true)}
          className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          New Promotion
        </Button>
      </div>

      {/* Performance KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
          <div className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-stone-500">
            <span>Total Promotions</span>
            <Ticket className="w-4 h-4 text-slate-400" />
          </div>
          <div className="text-3xl font-black text-slate-900 mt-2">{analytics?.totalPromotions ?? promotions.length}</div>
        </Card>
        <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
          <div className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-emerald-600">
            <span>Active Campaigns</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <div className="text-3xl font-black text-emerald-700 mt-2">{analytics?.activePromotions ?? 0}</div>
        </Card>
        <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
          <div className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-amber-600">
            <span>Total Redemptions</span>
            <TrendingUp className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-3xl font-black text-amber-700 mt-2">{analytics?.totalRedemptions ?? 0}</div>
        </Card>
        <Card className="p-5 bg-white border border-stone-200 rounded-2xl">
          <div className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-indigo-600">
            <span>Discount Granted</span>
            <Percent className="w-4 h-4 text-indigo-500" />
          </div>
          <div className="text-3xl font-black text-indigo-700 mt-2">
            {analytics?.currencyCode ?? 'INR'} {(analytics?.totalDiscountGranted ?? 0).toFixed(2)}
          </div>
        </Card>
      </div>

      {/* Promotions Table */}
      <Card className="bg-white border border-stone-200 rounded-2xl overflow-hidden">
        <div className="px-6 py-4 border-b border-stone-200 flex items-center justify-between">
          <h2 className="text-base font-bold text-slate-900">Configured Promotions</h2>
          <span className="text-xs text-stone-500">All timestamps stored in UTC</span>
        </div>

        {isLoading ? (
          <div className="p-12 flex items-center justify-center text-stone-500 gap-2">
            <Loader2 className="w-5 h-5 animate-spin" /> Loading promotions...
          </div>
        ) : promotions.length === 0 ? (
          <div className="p-12 text-center text-stone-500">
            <Tag className="w-10 h-10 mx-auto text-stone-300 mb-3" />
            <p className="font-semibold text-slate-700">No promotions created yet</p>
            <p className="text-xs mt-1">Create a percentage or fixed-amount coupon code to attract guests.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-stone-50 text-xs uppercase text-stone-500 border-b border-stone-200">
                <tr>
                  <th className="px-6 py-3 font-bold">Promotion & Code</th>
                  <th className="px-6 py-3 font-bold">Discount Rule</th>
                  <th className="px-6 py-3 font-bold">Usage</th>
                  <th className="px-6 py-3 font-bold">Validity Window</th>
                  <th className="px-6 py-3 font-bold">Status</th>
                  <th className="px-6 py-3 font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {promotions.map((promo) => (
                  <tr key={promo.id} className="hover:bg-stone-50/70 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-bold text-slate-900">{promo.name}</div>
                      {promo.couponCode && (
                        <span className="inline-block mt-1 font-mono text-xs font-bold px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                          {promo.couponCode}
                        </span>
                      )}
                      {promo.description && (
                        <div className="text-xs text-stone-500 mt-1 line-clamp-1">{promo.description}</div>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-800">
                        {promo.discountType === 'PERCENTAGE'
                          ? `${promo.discountValue}% OFF`
                          : `INR ${promo.discountValue.toFixed(2)} OFF`}
                      </div>
                      <div className="text-xs text-stone-500 mt-0.5">
                        Min spend: INR {promo.minOrderValue.toFixed(2)}
                        {promo.maxDiscountAmount ? ` • Max: INR ${promo.maxDiscountAmount.toFixed(2)}` : ''}
                      </div>
                      <div className="text-[11px] text-stone-400">
                        {promo.scope.replace('_', ' ')} • {promo.applyBeforeTax ? 'Pre-Tax' : 'Post-Tax'}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-mono font-bold text-slate-800">
                        {promo.currentUsageCount} / {promo.usageLimit ?? '∞'}
                      </div>
                      {promo.perCustomerLimit && (
                        <div className="text-xs text-stone-500">Max {promo.perCustomerLimit}/guest</div>
                      )}
                    </td>
                    <td className="px-6 py-4 text-xs text-stone-600">
                      <div>Start: {new Date(promo.startsAt).toLocaleDateString()}</div>
                      <div>End: {new Date(promo.endsAt).toLocaleDateString()}</div>
                    </td>
                    <td className="px-6 py-4">
                      {promo.expired ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-stone-100 text-stone-600">
                          <Clock className="w-3 h-3" /> Expired
                        </span>
                      ) : promo.active ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                          <CheckCircle2 className="w-3 h-3" /> Active
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-700">
                          <XCircle className="w-3 h-3" /> Inactive
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedPromoForRedemptions(promo)}
                        className="inline-flex items-center gap-1 text-xs"
                      >
                        <Eye className="w-3.5 h-3.5" /> Redemptions
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={toggleMutation.isPending}
                        onClick={() => toggleMutation.mutate({ id: promo.id, active: !promo.active })}
                        className="inline-flex items-center gap-1 text-xs"
                      >
                        <Power className="w-3.5 h-3.5" /> {promo.active ? 'Disable' : 'Enable'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Create Promotion Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 overflow-y-auto">
          <Card className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-stone-200 overflow-hidden my-8">
            <div className="bg-slate-900 px-6 py-4 text-white flex justify-between items-center">
              <h3 className="text-lg font-bold">Create Promotion / Coupon</h3>
              <button onClick={() => setShowCreateModal(false)} className="text-stone-400 hover:text-white text-xl">
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateSubmit} className="p-6 space-y-4">
              {formError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Promotion Name *</label>
                  <input
                    type="text"
                    required
                    value={formState.name}
                    onChange={(e) => setFormState({ ...formState, name: e.target.value })}
                    placeholder="Weekend Feast 15%"
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Coupon Code</label>
                  <input
                    type="text"
                    value={formState.couponCode}
                    onChange={(e) => setFormState({ ...formState, couponCode: e.target.value.toUpperCase() })}
                    placeholder="WELCOME15"
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm font-mono uppercase"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Discount Type</label>
                  <select
                    value={formState.discountType}
                    onChange={(e) => setFormState({ ...formState, discountType: e.target.value as DiscountType })}
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  >
                    <option value="PERCENTAGE">Percentage (%)</option>
                    <option value="FIXED_AMOUNT">Fixed Amount (INR)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Discount Value *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    value={formState.discountValue}
                    onChange={(e) => setFormState({ ...formState, discountValue: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Min Order Value</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formState.minOrderValue}
                    onChange={(e) => setFormState({ ...formState, minOrderValue: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Max Discount Cap</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formState.maxDiscountAmount}
                    onChange={(e) => setFormState({ ...formState, maxDiscountAmount: e.target.value })}
                    placeholder="Optional"
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Starts At *</label>
                  <input
                    type="datetime-local"
                    required
                    value={formState.startsAt}
                    onChange={(e) => setFormState({ ...formState, startsAt: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Ends At *</label>
                  <input
                    type="datetime-local"
                    required
                    value={formState.endsAt}
                    onChange={(e) => setFormState({ ...formState, endsAt: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Total Usage Limit</label>
                  <input
                    type="number"
                    min="1"
                    value={formState.usageLimit}
                    onChange={(e) => setFormState({ ...formState, usageLimit: e.target.value })}
                    placeholder="Unlimited"
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase text-stone-600 mb-1">Per-Customer Limit</label>
                  <input
                    type="number"
                    min="1"
                    value={formState.perCustomerLimit}
                    onChange={(e) => setFormState({ ...formState, perCustomerLimit: e.target.value })}
                    placeholder="Unlimited"
                    className="w-full px-3 py-2 rounded-lg border border-stone-300 text-sm"
                  />
                </div>
              </div>

              <div className="flex items-center gap-6 pt-1 text-xs font-medium text-stone-700">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formState.applyBeforeTax}
                    onChange={(e) => setFormState({ ...formState, applyBeforeTax: e.target.checked })}
                    className="accent-emerald-600"
                  />
                  Apply discount before tax
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formState.combinable}
                    onChange={(e) => setFormState({ ...formState, combinable: e.target.checked })}
                    className="accent-emerald-600"
                  />
                  Combinable with other offers
                </label>
              </div>

              <div className="pt-3 flex justify-end gap-3 border-t border-stone-200">
                <Button type="button" variant="outline" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
                >
                  {createMutation.isPending ? 'Creating...' : 'Create Promotion'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Redemptions History Modal */}
      {selectedPromoForRedemptions && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <Card className="w-full max-w-2xl bg-white rounded-2xl shadow-2xl border border-stone-200 overflow-hidden">
            <div className="bg-slate-900 px-6 py-4 text-white flex justify-between items-center">
              <div>
                <h3 className="text-lg font-bold">Redemption History</h3>
                <p className="text-xs text-stone-400">
                  {selectedPromoForRedemptions.name} ({selectedPromoForRedemptions.couponCode || 'Auto'})
                </p>
              </div>
              <button
                onClick={() => setSelectedPromoForRedemptions(null)}
                className="text-stone-400 hover:text-white text-xl"
              >
                &times;
              </button>
            </div>
            <div className="p-6 max-h-[65vh] overflow-y-auto">
              {loadingRedemptions ? (
                <div className="py-8 flex justify-center text-stone-500">
                  <Loader2 className="w-5 h-5 animate-spin" />
                </div>
              ) : redemptions.length === 0 ? (
                <p className="text-sm text-stone-500 text-center py-8">No redemptions recorded for this promotion yet.</p>
              ) : (
                <table className="w-full text-left text-xs">
                  <thead className="bg-stone-50 text-stone-500 uppercase border-b border-stone-200">
                    <tr>
                      <th className="px-4 py-2">Bill #</th>
                      <th className="px-4 py-2">Discount</th>
                      <th className="px-4 py-2">Actor</th>
                      <th className="px-4 py-2">Status</th>
                      <th className="px-4 py-2">Timestamp</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-stone-100">
                    {redemptions.map((r) => (
                      <tr key={r.id}>
                        <td className="px-4 py-2.5 font-mono font-bold text-slate-800">{r.billNumber}</td>
                        <td className="px-4 py-2.5 font-semibold text-emerald-700">
                          -{r.currencyCode} {r.discountAmount.toFixed(2)}
                        </td>
                        <td className="px-4 py-2.5 text-stone-600">{r.actorType}</td>
                        <td className="px-4 py-2.5">
                          <span
                            className={`px-2 py-0.5 rounded-full font-semibold ${
                              r.status === 'APPLIED'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-stone-100 text-stone-600'
                            }`}
                          >
                            {r.status}
                          </span>
                        </td>
                        <td className="px-4 py-2.5 text-stone-500">{new Date(r.redeemedAt).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
};
