import React, { useState } from 'react';
import { BillResponse, billClient } from '@/lib/billClient';
import { 
  Receipt, 
  Clock, 
  MapPin, 
  Building2, 
  CheckCircle2, 
  Ban, 
  AlertCircle, 
  Printer
} from 'lucide-react';

interface BillDetailModalProps {
  bill: BillResponse;
  isOpen: boolean;
  onClose: () => void;
  onBillUpdated?: (updated: BillResponse) => void;
}

export const BillDetailModal: React.FC<BillDetailModalProps> = ({
  bill,
  isOpen,
  onClose,
  onBillUpdated,
}) => {
  const [isVoiding, setIsVoiding] = useState(false);
  const [voidReason, setVoidReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleVoid = async () => {
    if (!voidReason.trim()) {
      setError('Please provide a mandatory reason for voiding this bill.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const updated = await billClient.voidBill(bill.id, voidReason);
      if (onBillUpdated) onBillUpdated(updated);
      setIsVoiding(false);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to void bill');
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status: BillResponse['status']) => {
    switch (status) {
      case 'PAID':
        return <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200"><CheckCircle2 className="w-3.5 h-3.5" /> PAID</span>;
      case 'PARTIALLY_PAID':
        return <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200"><Clock className="w-3.5 h-3.5" /> PARTIALLY PAID</span>;
      case 'VOIDED':
        return <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 border border-rose-200"><Ban className="w-3.5 h-3.5" /> VOIDED</span>;
      default:
        return <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200"><Receipt className="w-3.5 h-3.5" /> ISSUED</span>;
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-2xl w-full shadow-2xl border border-stone-200 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-5 bg-stone-900 text-white flex items-center justify-between border-b border-stone-800">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Receipt className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-bold font-mono tracking-tight">{bill.billNumber}</h2>
                {getStatusBadge(bill.status)}
              </div>
              <p className="text-xs text-stone-400 mt-0.5">
                Table <span className="text-amber-400 font-semibold">{bill.tableNumber}</span> • Issued {new Date(bill.issuedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-stone-400 hover:text-white p-2 rounded-lg hover:bg-stone-800 transition"
          >
            ✕
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-slate-800 text-sm">
          {error && (
            <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-3 text-sm">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Restaurant & Branch Snapshot */}
          <div className="p-4 rounded-xl bg-stone-50 border border-stone-200 flex flex-col sm:flex-row justify-between gap-4">
            <div>
              <div className="font-bold text-slate-900 flex items-center gap-1.5">
                <Building2 className="w-4 h-4 text-stone-500" />
                {bill.restaurantName}
              </div>
              {bill.branchName && (
                <div className="text-xs text-stone-500 mt-1 flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-stone-400" />
                  {bill.branchName} {bill.branchAddress ? `• ${bill.branchAddress}` : ''}
                </div>
              )}
            </div>
            <div className="text-left sm:text-right">
              <div className="text-xs text-stone-400 font-medium">Billed Orders</div>
              <div className="text-xs font-mono font-semibold text-stone-700 mt-0.5">
                {bill.orderNumbers.join(', ')}
              </div>
            </div>
          </div>

          {/* Line Items Table */}
          <div>
            <h3 className="font-semibold text-slate-900 mb-3 text-xs uppercase tracking-wider text-stone-400">
              Itemised Breakdown
            </h3>
            <div className="border border-stone-200 rounded-xl overflow-hidden divide-y divide-stone-100">
              {bill.items.map((item) => (
                <div key={item.id} className="p-3.5 flex justify-between items-start hover:bg-stone-50/50 transition">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-slate-900">
                        {item.quantity}× {item.itemName}
                      </span>
                      {item.variantName && (
                        <span className="text-xs px-2 py-0.5 rounded-md bg-stone-100 text-stone-600 font-medium">
                          {item.variantName}
                        </span>
                      )}
                    </div>
                    {item.options.length > 0 && (
                      <div className="text-xs text-stone-500 pl-4 space-y-0.5">
                        {item.options.map((opt) => (
                          <div key={opt.id}>
                            + {opt.optionName} ({bill.currencyCode} {opt.priceContribution.toFixed(2)})
                          </div>
                        ))}
                      </div>
                    )}
                    {item.specialInstructions && (
                      <p className="text-xs italic text-amber-700 pl-4">Note: "{item.specialInstructions}"</p>
                    )}
                  </div>
                  <div className="font-mono font-semibold text-slate-900 text-right">
                    {bill.currencyCode} {item.lineTotal.toFixed(2)}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Financial Calculation Breakdown */}
          <div className="p-4 rounded-xl bg-stone-50 border border-stone-200 space-y-2 font-mono text-xs">
            <div className="flex justify-between text-stone-600">
              <span>Items Subtotal:</span>
              <span>{bill.currencyCode} {bill.subtotal.toFixed(2)}</span>
            </div>
            {bill.discountAmount > 0 && (
              <div className="flex justify-between text-emerald-600 font-semibold">
                <span>Discount / Rebate:</span>
                <span>-{bill.currencyCode} {bill.discountAmount.toFixed(2)}</span>
              </div>
            )}
            {bill.serviceChargePercentage > 0 && (
              <div className="flex justify-between text-stone-600">
                <span>Service Charge ({bill.serviceChargePercentage}%):</span>
                <span>+{bill.currencyCode} {bill.serviceChargeAmount.toFixed(2)}</span>
              </div>
            )}
            {bill.taxPercentage > 0 && (
              <div className="flex justify-between text-stone-600">
                <span>Configured Tax ({bill.taxPercentage}%):</span>
                <span>+{bill.currencyCode} {bill.taxAmount.toFixed(2)}</span>
              </div>
            )}
            <div className="pt-2 border-t border-stone-200 flex justify-between text-sm font-bold text-slate-900">
              <span className="font-sans">Grand Total:</span>
              <span>{bill.currencyCode} {bill.grandTotal.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-xs text-emerald-700 font-medium">
              <span>Amount Paid:</span>
              <span>{bill.currencyCode} {bill.paidAmount.toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-sm font-bold text-amber-700 pt-1 border-t border-dashed border-stone-200">
              <span className="font-sans">Outstanding Balance:</span>
              <span>{bill.currencyCode} {bill.outstandingBalance.toFixed(2)}</span>
            </div>
          </div>

          {/* Void section if voiding */}
          {isVoiding && (
            <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 space-y-3">
              <h4 className="font-bold text-rose-900 text-xs uppercase tracking-wider flex items-center gap-1.5">
                <Ban className="w-4 h-4 text-rose-600" /> Void Bill Confirmation
              </h4>
              <p className="text-xs text-rose-700">
                Voiding marks this bill invalid and unlocks the associated order(s) for split or re-issuance.
              </p>
              <textarea
                value={voidReason}
                onChange={(e) => setVoidReason(e.target.value)}
                placeholder="State mandatory reason for voiding this bill..."
                className="w-full text-xs p-2.5 rounded-lg border border-rose-300 focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white"
                rows={2}
              />
              <div className="flex justify-end gap-2">
                <button
                  onClick={() => setIsVoiding(false)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold text-stone-600 hover:bg-stone-200 transition"
                >
                  Cancel
                </button>
                <button
                  onClick={handleVoid}
                  disabled={loading}
                  className="px-4 py-1.5 rounded-lg text-xs font-semibold bg-rose-600 text-white hover:bg-rose-700 transition disabled:opacity-50"
                >
                  {loading ? 'Voiding...' : 'Confirm Void'}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer Actions */}
        <div className="px-6 py-4 bg-stone-100 border-t border-stone-200 flex items-center justify-between">
          <button
            onClick={() => window.print()}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-stone-700 hover:bg-stone-200 transition"
          >
            <Printer className="w-4 h-4" /> Print Bill
          </button>

          <div className="flex items-center gap-3">
            {bill.status === 'ISSUED' && !isVoiding && (
              <button
                onClick={() => setIsVoiding(true)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-rose-700 hover:bg-rose-100 transition"
              >
                Void Bill
              </button>
            )}
            <button
              onClick={onClose}
              className="px-5 py-2 rounded-xl text-xs font-semibold bg-stone-900 text-white hover:bg-stone-800 transition"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
