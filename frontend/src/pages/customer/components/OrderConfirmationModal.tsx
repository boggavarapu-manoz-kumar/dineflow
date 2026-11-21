import React, { useState } from 'react';
import { CustomerCart } from '@/lib/cartClient';
import { Button } from '@/components/ui/Button';
import { X, ArrowRight, ShieldCheck, AlertCircle } from 'lucide-react';

interface OrderConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  cart: CustomerCart | null;
  isSubmitting: boolean;
  errorMessage?: string | null;
  onConfirmOrder: (specialNotes?: string) => void;
}

export const OrderConfirmationModal: React.FC<OrderConfirmationModalProps> = ({
  isOpen,
  onClose,
  cart,
  isSubmitting,
  errorMessage,
  onConfirmOrder,
}) => {
  const [specialNotes, setSpecialNotes] = useState('');

  if (!isOpen || !cart) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="relative w-full max-w-lg bg-white rounded-3xl shadow-2xl border border-stone-200 overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="p-5 border-b border-stone-100 flex items-center justify-between bg-stone-50/70">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-emerald-600 flex items-center justify-center text-white shadow-md shadow-emerald-600/20">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-slate-900 text-lg">Authoritative Order Confirmation</h3>
              <p className="text-xs text-stone-500 font-medium">Table #{cart.tableNumber} • Pricing Verified</p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="p-2 text-stone-400 hover:text-slate-700 rounded-full hover:bg-stone-200/50 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5 max-h-[70vh] overflow-y-auto">
          {errorMessage && (
            <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2.5">
              <AlertCircle className="w-5 h-5 shrink-0 mt-0.5 text-rose-600" />
              <div>
                <p className="font-bold">Order Placement Alert</p>
                <p className="mt-0.5">{errorMessage}</p>
              </div>
            </div>
          )}

          {/* Itemized Snapshot */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold text-stone-500 uppercase tracking-wider">Order Items Snapshot</h4>
            <div className="divide-y divide-stone-100 border border-stone-200 rounded-2xl p-4 bg-stone-50/50">
              {cart.items.map((item) => (
                <div key={item.cartItemId} className="py-2.5 first:pt-0 last:pb-0">
                  <div className="flex justify-between items-start text-sm">
                    <span className="font-semibold text-slate-800">
                      <span className="text-emerald-700 font-bold mr-1.5">{item.quantity}x</span>
                      {item.itemName}
                      {item.variantName && <span className="text-xs text-stone-500 ml-1">({item.variantName})</span>}
                    </span>
                    <span className="font-bold text-slate-900">₹{item.lineTotal.toFixed(2)}</span>
                  </div>
                  {item.selectedOptions.length > 0 && (
                    <div className="pl-6 text-[11px] text-stone-500 mt-0.5">
                      + {item.selectedOptions.map((o) => o.optionName).join(', ')}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Table Instructions */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5 uppercase tracking-wider">
              Special Table Instructions (Optional)
            </label>
            <textarea
              rows={2}
              value={specialNotes}
              onChange={(e) => setSpecialNotes(e.target.value)}
              placeholder="e.g. Please bring extra napkins or allergy information"
              className="w-full text-xs p-3 rounded-xl border border-stone-200 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-600"
              maxLength={512}
            />
          </div>

          {/* Authoritative Financial Breakdown */}
          <div className="p-4 rounded-2xl bg-slate-900 text-white space-y-2">
            <div className="flex justify-between text-xs text-stone-300">
              <span>Items Subtotal</span>
              <span className="font-mono">₹{cart.subtotal.toFixed(2)}</span>
            </div>
            {cart.taxAmount > 0 && (
              <div className="flex justify-between text-xs text-stone-300">
                <span>Taxes & GST ({cart.taxPercentage}%)</span>
                <span className="font-mono">₹{cart.taxAmount.toFixed(2)}</span>
              </div>
            )}
            {cart.serviceChargeAmount > 0 && (
              <div className="flex justify-between text-xs text-stone-300">
                <span>Service Charge ({cart.serviceChargePercentage}%)</span>
                <span className="font-mono">₹{cart.serviceChargeAmount.toFixed(2)}</span>
              </div>
            )}
            <div className="flex justify-between text-base font-extrabold text-emerald-400 pt-2 border-t border-slate-800">
              <span>Authoritative Payable Total</span>
              <span className="font-mono text-lg">₹{cart.grandTotal.toFixed(2)}</span>
            </div>
          </div>
        </div>

        {/* Action Footer */}
        <div className="p-5 border-t border-stone-100 bg-stone-50 flex items-center justify-end gap-3">
          <Button variant="outline" size="sm" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="emerald"
            size="md"
            className="font-bold flex items-center gap-2 px-6"
            disabled={isSubmitting}
            onClick={() => onConfirmOrder(specialNotes.trim() || undefined)}
          >
            {isSubmitting ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent" />
                <span>Submitting Order...</span>
              </>
            ) : (
              <>
                <span>Confirm & Place Order</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </Button>
        </div>
      </div>
    </div>
  );
};
