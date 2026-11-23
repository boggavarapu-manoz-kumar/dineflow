import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { paymentClient } from '@/lib/paymentClient';
import { BillResponse } from '@/lib/billClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  Banknote,
  CheckCircle2,
  AlertCircle,
  Loader2,
  DollarSign,
  ArrowRight,
  ShieldAlert
} from 'lucide-react';

interface StaffPaymentModalProps {
  bill: BillResponse;
  isOpen: boolean;
  onClose: () => void;
  onPaymentRecorded?: () => void;
}

export const StaffPaymentModal: React.FC<StaffPaymentModalProps> = ({
  bill,
  isOpen,
  onClose,
  onPaymentRecorded,
}) => {
  const queryClient = useQueryClient();
  const [amountReceived, setAmountReceived] = useState<string>(bill.outstandingBalance.toFixed(2));
  const [notes, setNotes] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const cashMutation = useMutation({
    mutationFn: () =>
      paymentClient.recordCash({
        billId: bill.id,
        amountReceived: parseFloat(amountReceived),
        notes: notes.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff-bills'] });
      queryClient.invalidateQueries({ queryKey: ['bill-detail', bill.id] });
      onPaymentRecorded?.();
      onClose();
    },
    onError: (err: any) => {
      setShowConfirm(false);
      setErrorMessage(err?.message || 'Failed to record payment');
    },
  });

  if (!isOpen) return null;

  const parsedAmount = parseFloat(amountReceived) || 0;
  const isPartial = parsedAmount < bill.outstandingBalance && parsedAmount > 0;
  const isOverpayment = parsedAmount > bill.outstandingBalance;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <Card className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-100 overflow-hidden">
        {/* Header */}
        <div className="bg-amber-600 px-6 py-5 text-white flex justify-between items-center">
          <div>
            <h2 className="text-xl font-bold tracking-tight">Collect Cash / Pay-at-Counter</h2>
            <p className="text-xs text-amber-100 mt-0.5">
              Bill #{bill.billNumber} • Table {bill.tableNumber}
            </p>
          </div>
          <div className="w-10 h-10 rounded-full bg-amber-500/50 flex items-center justify-center text-white">
            <Banknote className="w-6 h-6" />
          </div>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {errorMessage && (
            <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Balance Overview */}
          <div className="grid grid-cols-2 gap-3 bg-stone-50 p-4 rounded-xl border border-stone-200">
            <div>
              <span className="text-xs uppercase text-stone-500 font-bold">Grand Total</span>
              <div className="text-lg font-bold text-stone-900 mt-0.5">
                {bill.currencyCode} {bill.grandTotal.toFixed(2)}
              </div>
            </div>
            <div>
              <span className="text-xs uppercase text-amber-600 font-bold">Outstanding Balance</span>
              <div className="text-lg font-black text-amber-700 mt-0.5">
                {bill.currencyCode} {bill.outstandingBalance.toFixed(2)}
              </div>
            </div>
          </div>

          {!showConfirm ? (
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-gray-700 mb-1.5">
                  Amount Received from Customer ({bill.currencyCode})
                </label>
                <div className="relative">
                  <span className="absolute left-3.5 top-3 text-gray-400 font-bold">
                    <DollarSign className="w-4 h-4" />
                  </span>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={bill.outstandingBalance}
                    value={amountReceived}
                    onChange={(e) => {
                      setAmountReceived(e.target.value);
                      setErrorMessage(null);
                    }}
                    className="w-full pl-9 pr-4 py-2.5 rounded-xl border border-gray-300 focus:ring-2 focus:ring-amber-500 focus:border-amber-500 font-mono text-lg font-bold text-gray-900"
                    placeholder="0.00"
                  />
                </div>
                {isPartial && (
                  <p className="text-xs text-amber-600 mt-1 font-medium">
                    ⚠️ Partial payment: Remaining balance will be {bill.currencyCode}{' '}
                    {(bill.outstandingBalance - parsedAmount).toFixed(2)}
                  </p>
                )}
                {isOverpayment && (
                  <p className="text-xs text-rose-600 mt-1 font-medium">
                    ❌ Amount cannot exceed outstanding balance ({bill.currencyCode} {bill.outstandingBalance.toFixed(2)})
                  </p>
                )}
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-gray-700 mb-1.5">
                  Staff Notes / Register Ref (Optional)
                </label>
                <input
                  type="text"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="e.g. Received at cashier counter #1"
                  className="w-full px-3.5 py-2 rounded-xl border border-gray-300 focus:ring-2 focus:ring-amber-500 text-sm"
                />
              </div>

              <div className="pt-2 flex gap-3">
                <Button variant="outline" onClick={onClose} className="flex-1 py-2.5">
                  Cancel
                </Button>
                <Button
                  onClick={() => setShowConfirm(true)}
                  disabled={parsedAmount <= 0 || isOverpayment}
                  className="flex-1 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-semibold flex items-center justify-center gap-1.5"
                >
                  Verify Receipt <ArrowRight className="w-4 h-4" />
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl space-y-2">
                <div className="flex items-center gap-2 text-amber-900 font-bold text-sm">
                  <ShieldAlert className="w-5 h-5 text-amber-600 shrink-0" />
                  Confirm Cash Collection
                </div>
                <p className="text-xs text-amber-800 leading-relaxed">
                  Have you physically collected and counted{' '}
                  <span className="font-bold underline">
                    {bill.currencyCode} {parsedAmount.toFixed(2)}
                  </span>{' '}
                  in cash from the customer for Bill #{bill.billNumber}?
                </p>
              </div>

              <div className="flex gap-3 pt-2">
                <Button
                  variant="outline"
                  onClick={() => setShowConfirm(false)}
                  disabled={cashMutation.isPending}
                  className="flex-1 py-2.5"
                >
                  Back
                </Button>
                <Button
                  onClick={() => cashMutation.mutate()}
                  disabled={cashMutation.isPending}
                  className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold flex items-center justify-center gap-2"
                >
                  {cashMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      <CheckCircle2 className="w-4 h-4" />
                      Confirm Cash Received
                    </>
                  )}
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};
