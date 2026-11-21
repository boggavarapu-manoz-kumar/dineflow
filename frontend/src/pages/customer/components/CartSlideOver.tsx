import React from 'react';
import { CustomerCart } from '@/lib/cartClient';
import { X, Trash2, Plus, Minus, ShoppingBag, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface CartSlideOverProps {
  isOpen: boolean;
  onClose: () => void;
  cart: CustomerCart | null;
  isLoading: boolean;
  onUpdateQuantity: (cartItemId: string, newQuantity: number) => void;
  onRemoveItem: (cartItemId: string) => void;
  onClearCart: () => void;
  onProceedToCheckout?: () => void;
}

export const CartSlideOver: React.FC<CartSlideOverProps> = ({
  isOpen,
  onClose,
  cart,
  isLoading,
  onUpdateQuantity,
  onRemoveItem,
  onClearCart,
  onProceedToCheckout,
}) => {
  if (!isOpen) return null;

  const items = cart?.items || [];
  const subtotal = cart?.subtotal || 0;
  const taxAmount = cart?.taxAmount || 0;
  const serviceChargeAmount = cart?.serviceChargeAmount || 0;
  const grandTotal = cart?.grandTotal || 0;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden animate-in fade-in duration-200">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-950/50 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-white shadow-2xl flex flex-col justify-between">
          {/* Header */}
          <div className="p-4 sm:p-5 border-b border-stone-200 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-slate-900 flex items-center justify-center text-emerald-400">
                <ShoppingBag className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-extrabold text-slate-900">Your Dining Table Cart</h2>
                <p className="text-[11px] text-stone-500 font-medium">
                  {cart?.tableNumber ? `Table #${cart.tableNumber}` : 'Current Guest Session'}
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              className="p-2 text-stone-400 hover:text-slate-700 rounded-full hover:bg-stone-100 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Cart Item List */}
          <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3">
            {isLoading && items.length === 0 ? (
              <div className="py-20 text-center">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-600 mx-auto mb-3" />
                <p className="text-xs text-stone-500">Loading your cart...</p>
              </div>
            ) : items.length === 0 ? (
              <div className="py-16 text-center space-y-3">
                <div className="w-14 h-14 bg-stone-100 rounded-2xl flex items-center justify-center mx-auto text-stone-400">
                  <ShoppingBag className="w-7 h-7" />
                </div>
                <h3 className="font-bold text-slate-800 text-sm">Your order cart is empty</h3>
                <p className="text-xs text-stone-500 max-w-xs mx-auto">
                  Browse through today's fresh culinary courses and add dishes to begin building your meal.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center justify-between pb-2 border-b border-stone-100">
                  <span className="text-xs font-bold text-stone-600 uppercase tracking-wider">
                    {cart?.totalItemCount} Items Selected
                  </span>
                  <button
                    onClick={onClearCart}
                    className="text-xs text-rose-600 hover:text-rose-700 font-semibold cursor-pointer"
                  >
                    Clear All
                  </button>
                </div>

                {items.map((item) => (
                  <div
                    key={item.cartItemId}
                    className="p-3.5 rounded-2xl border border-stone-200/80 bg-stone-50/60 space-y-2 text-left"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h4 className="font-bold text-slate-900 text-sm">{item.itemName}</h4>
                        {item.variantName && (
                          <span className="inline-block px-2 py-0.5 mt-0.5 rounded-md bg-stone-200/70 text-slate-700 text-[10px] font-semibold">
                            {item.variantName}
                          </span>
                        )}
                      </div>

                      <span className="font-extrabold text-slate-900 text-sm">
                        ₹{item.lineTotal.toFixed(2)}
                      </span>
                    </div>

                    {/* Options list */}
                    {item.selectedOptions.length > 0 && (
                      <div className="text-[11px] text-stone-600 space-y-0.5 pl-1 border-l-2 border-emerald-500/40">
                        {item.selectedOptions.map((opt) => (
                          <div key={opt.optionId} className="flex justify-between">
                            <span>+ {opt.optionName}</span>
                            <span className="text-stone-500 font-medium">₹{opt.additionalPrice.toFixed(2)}</span>
                          </div>
                        ))}
                      </div>
                    )}

                    {item.specialInstructions && (
                      <p className="text-[11px] text-stone-500 italic bg-white p-1.5 rounded-lg border border-stone-200/60">
                        Note: {item.specialInstructions}
                      </p>
                    )}

                    {/* Quantity Modifier Controls */}
                    <div className="flex items-center justify-between pt-1">
                      <div className="flex items-center gap-2 bg-white rounded-xl border border-stone-200 p-1 shadow-2xs">
                        <button
                          onClick={() => {
                            if (item.quantity > 1) {
                              onUpdateQuantity(item.cartItemId, item.quantity - 1);
                            } else {
                              onRemoveItem(item.cartItemId);
                            }
                          }}
                          className="p-1 rounded-md text-stone-600 hover:bg-stone-100 cursor-pointer"
                        >
                          <Minus className="w-3.5 h-3.5" />
                        </button>
                        <span className="font-bold text-xs w-5 text-center text-slate-900">
                          {item.quantity}
                        </span>
                        <button
                          onClick={() => onUpdateQuantity(item.cartItemId, item.quantity + 1)}
                          disabled={item.quantity >= 50}
                          className="p-1 rounded-md text-stone-600 hover:bg-stone-100 cursor-pointer disabled:opacity-40"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      <button
                        onClick={() => onRemoveItem(item.cartItemId)}
                        className="text-stone-400 hover:text-rose-600 p-1.5 rounded-lg transition-colors cursor-pointer"
                        title="Remove dish"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Footer Financial Summary */}
          {items.length > 0 && (
            <div className="p-4 sm:p-5 border-t border-stone-200 bg-stone-50 space-y-3">
              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between text-stone-600">
                  <span>Items Subtotal</span>
                  <span className="font-semibold text-slate-800">₹{subtotal.toFixed(2)}</span>
                </div>
                {taxAmount > 0 && (
                  <div className="flex justify-between text-stone-600">
                    <span>GST / Taxes ({cart?.taxPercentage}%)</span>
                    <span className="font-semibold text-slate-800">₹{taxAmount.toFixed(2)}</span>
                  </div>
                )}
                {serviceChargeAmount > 0 && (
                  <div className="flex justify-between text-stone-600">
                    <span>Service Charge ({cart?.serviceChargePercentage}%)</span>
                    <span className="font-semibold text-slate-800">₹{serviceChargeAmount.toFixed(2)}</span>
                  </div>
                )}
                <div className="flex justify-between text-base font-black text-slate-900 pt-2 border-t border-stone-200">
                  <span>Provisional Total</span>
                  <span className="text-emerald-700">₹{grandTotal.toFixed(2)}</span>
                </div>
              </div>

              <div className="pt-1">
                <Button
                  variant="emerald"
                  size="md"
                  className="w-full font-bold flex items-center justify-center gap-2 py-3 shadow-md"
                  disabled={items.length === 0}
                  onClick={() => {
                    if (onProceedToCheckout) {
                      onProceedToCheckout();
                    }
                  }}
                >
                  <span>Review & Send Order to Kitchen</span>
                  <ArrowRight className="w-4 h-4" />
                </Button>
                <p className="text-[10px] text-stone-500 text-center mt-2">
                  Pricing verified authoritatively by restaurant server.
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
