import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { paymentClient, PaymentResponse } from '@/lib/paymentClient';
import { promotionClient, CouponApplicationResponse } from '@/lib/promotionClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  CreditCard,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Lock,
  ArrowRight,
  Tag,
  X,
} from 'lucide-react';

interface CheckoutModalProps {
  billId: string;
  billNumber: string;
  orderNumber?: string;
  amountDue: number;
  currency: string;
  isOpen: boolean;
  onClose: () => void;
  onPaymentSuccess?: (payment: PaymentResponse) => void;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  billId,
  billNumber,
  orderNumber,
  amountDue,
  currency,
  isOpen,
  onClose,
  onPaymentSuccess,
}) => {
  const [selectedMethod, setSelectedMethod] = useState<'ONLINE_PAYMENT' | 'PAY_AT_COUNTER'>('ONLINE_PAYMENT');
  const [checkoutStep, setCheckoutStep] = useState<'SELECT' | 'PROCESSING' | 'SUCCESS' | 'ERROR'>('SELECT');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [activePayment, setActivePayment] = useState<PaymentResponse | null>(null);

  // Coupon state — always driven by backend-calculated values
  const [couponInput, setCouponInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<CouponApplicationResponse | null>(null);
  const [couponFeedback, setCouponFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const effectiveAmountDue = appliedCoupon ? appliedCoupon.outstandingBalance : amountDue;

  const applyCouponMutation = useMutation({
    mutationFn: (code: string) => promotionClient.applyCoupon(billId, code),
    onSuccess: (res) => {
      setAppliedCoupon(res);
      setCouponInput('');
      setCouponFeedback({
        type: 'success',
        text: `${res.couponCode} applied! You saved ${res.currencyCode} ${res.discountAmount.toFixed(2)}.`,
      });
    },
    onError: (err: any) => {
      setCouponFeedback({
        type: 'error',
        text: err?.message || 'Invalid or ineligible coupon code.',
      });
    },
  });

  const removeCouponMutation = useMutation({
    mutationFn: () => promotionClient.removeCoupon(billId, appliedCoupon?.couponCode),
    onSuccess: () => {
      setAppliedCoupon(null);
      setCouponFeedback({ type: 'success', text: 'Coupon removed. Original bill total restored.' });
    },
    onError: (err: any) => {
      setCouponFeedback({ type: 'error', text: err?.message || 'Unable to remove coupon.' });
    },
  });

  // 1. Mutation to initiate payment session
  const initiateMutation = useMutation({
    mutationFn: () =>
      paymentClient.initiate({
        billId,
        paymentMethod: selectedMethod,
        providerType: selectedMethod === 'ONLINE_PAYMENT' ? 'RAZORPAY' : 'CASH_REGISTER',
        amount: effectiveAmountDue,
        notes: `Customer checkout for bill ${billNumber}`,
      }),
    onSuccess: (payment) => {
      setActivePayment(payment);

      if (selectedMethod === 'PAY_AT_COUNTER') {
        setCheckoutStep('SUCCESS');
        onPaymentSuccess?.(payment);
        return;
      }

      handleOnlineGatewayFlow(payment);
    },
    onError: (err: any) => {
      setCheckoutStep('ERROR');
      setErrorMessage(err?.response?.data?.message || err.message || 'Payment initiation failed.');
    },
  });

  // 2. Mutation to settle / verify payment with server
  const settleMutation = useMutation({
    mutationFn: ({ paymentId, txnId, orderId, signature }: { paymentId: string; txnId: string; orderId?: string; signature?: string }) =>
      paymentClient.settle(paymentId, {
        providerTransactionId: txnId,
        providerOrderId: orderId,
        signature: signature || 'mock_verified_sig',
      }),
    onSuccess: (payment) => {
      setActivePayment(payment);
      setCheckoutStep('SUCCESS');
      onPaymentSuccess?.(payment);
    },
    onError: (err: any) => {
      setCheckoutStep('ERROR');
      setErrorMessage(err?.response?.data?.message || 'Server verification failed.');
    },
  });

  const handleOnlineGatewayFlow = (payment: PaymentResponse) => {
    setCheckoutStep('PROCESSING');

    const razorpayWindow = window as any;
    if (razorpayWindow.Razorpay && payment.clientSecretOrKeyId && payment.providerOrderId) {
      const options = {
        key: payment.clientSecretOrKeyId,
        amount: Math.round(payment.payableAmount * 100),
        currency: payment.currencyCode,
        name: 'DineFlow Restaurant',
        description: `Bill ${billNumber}`,
        order_id: payment.providerOrderId,
        handler: function (response: any) {
          settleMutation.mutate({
            paymentId: payment.id,
            txnId: response.razorpay_payment_id,
            orderId: response.razorpay_order_id,
            signature: response.razorpay_signature,
          });
        },
        modal: {
          ondismiss: function () {
            setCheckoutStep('SELECT');
          },
        },
        theme: {
          color: '#10b981',
        },
      };

      const rzp = new razorpayWindow.Razorpay(options);
      rzp.on('payment.failed', function (resp: any) {
        setCheckoutStep('ERROR');
        setErrorMessage(resp.error?.description || 'Gateway transaction declined');
      });
      rzp.open();
    } else if (import.meta.env.DEV && payment.paymentProvider === 'MOCK') {
      // Explicitly gated to DEV + MOCK provider only
      setTimeout(() => {
        settleMutation.mutate({
          paymentId: payment.id,
          txnId: 'mock_pay_' + Date.now(),
          orderId: payment.providerOrderId,
          signature: 'mock_verified_sig',
        });
      }, 1500);
    } else {
      setCheckoutStep('ERROR');
      setErrorMessage('Payment gateway checkout failed to initialize. Please retry or choose Pay at Counter.');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <Card className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-100 overflow-hidden">
        {/* Header */}
        <div className="bg-emerald-600 px-6 py-5 text-white flex justify-between items-center">
          <div>
            <h2 className="text-xl font-bold tracking-tight">Secure Checkout</h2>
            <p className="text-xs text-emerald-100 mt-0.5">
              Bill #{billNumber} {orderNumber ? `• Order #${orderNumber}` : ''}
            </p>
          </div>
          <div className="flex items-center gap-1.5 text-xs bg-emerald-700/60 px-2.5 py-1 rounded-full font-medium">
            <Lock className="w-3.5 h-3.5" />
            256-bit Encrypted
          </div>
        </div>

        {/* Content Body */}
        <div className="p-6">
          {checkoutStep === 'SELECT' && (
            <div className="space-y-5">
              {/* Amount Display */}
              <div className="bg-gray-50 border border-gray-200/80 rounded-xl p-4 space-y-2">
                <div className="flex justify-between items-center">
                  <div>
                    <span className="text-xs uppercase tracking-wider text-gray-500 font-semibold">Total Payable</span>
                    <div className="text-2xl font-black text-gray-900 mt-0.5">
                      {currency} {effectiveAmountDue.toFixed(2)}
                    </div>
                  </div>
                  <div className="w-10 h-10 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center">
                    <CreditCard className="w-5 h-5" />
                  </div>
                </div>
                {appliedCoupon && appliedCoupon.discountAmount > 0 && (
                  <div className="pt-2 border-t border-gray-200 flex items-center justify-between text-xs text-emerald-700 font-semibold">
                    <span className="flex items-center gap-1.5">
                      <Tag className="w-3.5 h-3.5" />
                      Discount ({appliedCoupon.couponCode})
                    </span>
                    <div className="flex items-center gap-2">
                      <span>-{currency} {appliedCoupon.discountAmount.toFixed(2)}</span>
                      <button
                        type="button"
                        onClick={() => removeCouponMutation.mutate()}
                        disabled={removeCouponMutation.isPending}
                        className="text-rose-600 hover:text-rose-800 p-0.5 rounded"
                        title="Remove coupon"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Coupon Code Input */}
              {!appliedCoupon && (
                <div className="space-y-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-gray-500">Promo / Coupon Code</label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={couponInput}
                      onChange={(e) => {
                        setCouponInput(e.target.value.toUpperCase());
                        setCouponFeedback(null);
                      }}
                      placeholder="Enter coupon code"
                      className="flex-1 px-3 py-2 rounded-lg border border-gray-300 text-sm font-mono uppercase focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      disabled={!couponInput.trim() || applyCouponMutation.isPending}
                      onClick={() => applyCouponMutation.mutate(couponInput.trim())}
                      className="px-4 text-xs font-bold"
                    >
                      {applyCouponMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : 'Apply'}
                    </Button>
                  </div>
                </div>
              )}

              {couponFeedback && (
                <div
                  className={`text-xs px-3 py-2 rounded-lg font-medium ${
                    couponFeedback.type === 'success'
                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      : 'bg-rose-50 text-rose-700 border border-rose-200'
                  }`}
                >
                  {couponFeedback.text}
                </div>
              )}

              {/* Payment Methods */}
              <div className="space-y-3">
                <label className="text-xs font-bold uppercase tracking-wider text-gray-500">Select Payment Method</label>

                <div
                  onClick={() => setSelectedMethod('ONLINE_PAYMENT')}
                  className={`flex items-center justify-between p-4 rounded-xl border-2 cursor-pointer transition-all ${
                    selectedMethod === 'ONLINE_PAYMENT'
                      ? 'border-emerald-600 bg-emerald-50/40 text-emerald-950 font-medium'
                      : 'border-gray-200 hover:border-gray-300 text-gray-700'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-lg bg-emerald-600 text-white flex items-center justify-center font-bold text-sm">
                      UPI
                    </div>
                    <div>
                      <div className="font-semibold text-sm">Instant Online Payment</div>
                      <div className="text-xs text-gray-500">UPI, Cards, NetBanking, Wallets</div>
                    </div>
                  </div>
                  <input
                    type="radio"
                    name="method"
                    checked={selectedMethod === 'ONLINE_PAYMENT'}
                    onChange={() => setSelectedMethod('ONLINE_PAYMENT')}
                    className="accent-emerald-600 w-4 h-4"
                  />
                </div>

                <div
                  onClick={() => setSelectedMethod('PAY_AT_COUNTER')}
                  className={`flex items-center justify-between p-4 rounded-xl border-2 cursor-pointer transition-all ${
                    selectedMethod === 'PAY_AT_COUNTER'
                      ? 'border-emerald-600 bg-emerald-50/40 text-emerald-950 font-medium'
                      : 'border-gray-200 hover:border-gray-300 text-gray-700'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-lg bg-gray-700 text-white flex items-center justify-center font-bold text-sm">
                      💵
                    </div>
                    <div>
                      <div className="font-semibold text-sm">Pay at Counter / Cash</div>
                      <div className="text-xs text-gray-500">Settle with staff after your meal</div>
                    </div>
                  </div>
                  <input
                    type="radio"
                    name="method"
                    checked={selectedMethod === 'PAY_AT_COUNTER'}
                    onChange={() => setSelectedMethod('PAY_AT_COUNTER')}
                    className="accent-emerald-600 w-4 h-4"
                  />
                </div>
              </div>

              {/* Actions */}
              <div className="pt-2 flex gap-3">
                <Button variant="outline" onClick={onClose} className="flex-1 py-2.5">
                  Cancel
                </Button>
                <Button
                  onClick={() => initiateMutation.mutate()}
                  disabled={initiateMutation.isPending}
                  className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold flex items-center justify-center gap-2"
                >
                  {initiateMutation.isPending ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      Pay {currency} {effectiveAmountDue.toFixed(2)}
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </Button>
              </div>
            </div>
          )}

          {checkoutStep === 'PROCESSING' && (
            <div className="py-8 flex flex-col items-center justify-center text-center space-y-4">
              <div className="relative">
                <Loader2 className="w-14 h-14 text-emerald-600 animate-spin" />
                <ShieldCheck className="w-6 h-6 text-emerald-700 absolute inset-0 m-auto" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-gray-900">Contacting Payment Gateway...</h3>
                <p className="text-xs text-gray-500 mt-1 max-w-xs">
                  Please do not refresh or close this window while we securely confirm your transaction.
                </p>
              </div>
            </div>
          )}

          {checkoutStep === 'SUCCESS' && (
            <div className="py-6 flex flex-col items-center justify-center text-center space-y-4">
              <div className="w-16 h-16 bg-emerald-100 rounded-full flex items-center justify-center text-emerald-600">
                <CheckCircle2 className="w-10 h-10" />
              </div>
              <div>
                <h3 className="text-xl font-bold text-gray-900">Payment Succeeded!</h3>
                <p className="text-sm text-gray-600 mt-1">
                  Transaction ref: <span className="font-mono font-medium">{activePayment?.paymentReference}</span>
                </p>
                <p className="text-xs text-emerald-600 font-semibold mt-2">
                  Bill #{billNumber} is verified and settled.
                </p>
              </div>
              <Button
                onClick={onClose}
                className="w-full mt-4 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
              >
                Done
              </Button>
            </div>
          )}

          {checkoutStep === 'ERROR' && (
            <div className="py-6 flex flex-col items-center justify-center text-center space-y-4">
              <div className="w-16 h-16 bg-rose-100 rounded-full flex items-center justify-center text-rose-600">
                <AlertCircle className="w-10 h-10" />
              </div>
              <div>
                <h3 className="text-xl font-bold text-gray-900">Payment Failed</h3>
                <p className="text-xs text-rose-600 font-medium mt-1 max-w-xs">
                  {errorMessage || 'Unable to complete payment. Please try again or pay at the counter.'}
                </p>
              </div>
              <div className="flex gap-3 w-full pt-2">
                <Button variant="outline" onClick={onClose} className="flex-1 py-2.5">
                  Close
                </Button>
                <Button
                  onClick={() => setCheckoutStep('SELECT')}
                  className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
                >
                  Try Again
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};
