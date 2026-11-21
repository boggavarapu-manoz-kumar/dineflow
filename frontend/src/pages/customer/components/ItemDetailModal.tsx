import React, { useState } from 'react';
import { CustomerMenuItem } from '@/lib/menuClient';
import { X, Plus, Minus, ShoppingBag, Check } from 'lucide-react';
import { Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

interface ItemDetailModalProps {
  item: CustomerMenuItem | null;
  onClose: () => void;
  onAddToCart: (payload: {
    menuItemId: string;
    variantId?: string;
    optionIds: string[];
    quantity: number;
    specialInstructions?: string;
  }) => void;
  isAdding?: boolean;
}

export const ItemDetailModal: React.FC<ItemDetailModalProps> = ({
  item,
  onClose,
  onAddToCart,
  isAdding = false,
}) => {
  const [selectedVariantId, setSelectedVariantId] = useState<string | undefined>(
    item && item.variants.length > 0 ? item.variants[0].id : undefined
  );
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [quantity, setQuantity] = useState<number>(1);
  const [specialInstructions, setSpecialInstructions] = useState<string>('');

  if (!item) return null;

  // Toggle option selection
  const handleToggleOption = (groupId: string, optionId: string, maxSelections: number) => {
    const isSelected = selectedOptionIds.includes(optionId);
    if (isSelected) {
      setSelectedOptionIds(selectedOptionIds.filter((id) => id !== optionId));
    } else {
      // Find options in this group that are currently selected
      const group = item.optionGroups.find((g) => g.id === groupId);
      const groupOptIds = group ? group.options.map((o) => o.id) : [];
      const currentSelectedInGroup = selectedOptionIds.filter((id) => groupOptIds.includes(id));

      if (maxSelections === 1) {
        // Radio behavior: replace existing selection in group
        const withoutGroup = selectedOptionIds.filter((id) => !groupOptIds.includes(id));
        setSelectedOptionIds([...withoutGroup, optionId]);
      } else if (currentSelectedInGroup.length < maxSelections) {
        setSelectedOptionIds([...selectedOptionIds, optionId]);
      }
    }
  };

  // Compute calculated live preview price
  const activeVariant = item.variants.find((v) => v.id === selectedVariantId);
  const basePrice = activeVariant ? activeVariant.price : item.effectivePrice;

  let optionsTotal = 0;
  for (const group of item.optionGroups) {
    for (const opt of group.options) {
      if (selectedOptionIds.includes(opt.id)) {
        optionsTotal += opt.additionalPrice;
      }
    }
  }

  const unitTotal = basePrice + optionsTotal;
  const lineTotal = unitTotal * quantity;

  const handleConfirmAdd = () => {
    onAddToCart({
      menuItemId: item.id,
      variantId: selectedVariantId,
      optionIds: selectedOptionIds,
      quantity,
      specialInstructions: specialInstructions.trim() || undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/60 backdrop-blur-xs animate-in fade-in duration-200">
      <div
        className="bg-white w-full max-w-lg rounded-3xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-200"
        role="dialog"
      >
        {/* Modal Header & Hero Image */}
        <div className="relative">
          {item.primaryImageUrl ? (
            <div className="h-48 w-full overflow-hidden bg-stone-100">
              <img
                src={item.primaryImageUrl}
                alt={item.name}
                className="w-full h-full object-cover"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-slate-950/20 to-transparent" />
            </div>
          ) : (
            <div className="h-24 bg-stone-100 border-b border-stone-200" />
          )}

          <button
            onClick={onClose}
            className="absolute top-3 right-3 p-2 bg-black/40 hover:bg-black/60 text-white rounded-full transition-colors cursor-pointer"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="absolute bottom-3 left-4 right-4 flex items-end justify-between">
            <Badge variant={item.isAvailable ? 'emerald' : 'warning'}>
              {item.isAvailable ? 'In Kitchen Stock' : 'Sold Out for the Day'}
            </Badge>
          </div>
        </div>

        {/* Scrollable Body Content */}
        <div className="p-5 overflow-y-auto space-y-4 text-left flex-1">
          <div>
            <div className="flex items-center gap-2">
              <span
                className={`w-2.5 h-2.5 rounded-full inline-block ${
                  item.dietaryClassification === 'NON_VEGETARIAN'
                    ? 'bg-rose-500'
                    : 'bg-emerald-500'
                }`}
              />
              <span className="text-[11px] font-bold tracking-wider uppercase text-stone-500">
                {item.dietaryClassification.replace('_', ' ')}
              </span>
            </div>
            <h3 className="text-xl font-black text-slate-900 mt-1">{item.name}</h3>
            {item.shortDescription && (
              <p className="text-xs text-stone-500 mt-1 leading-relaxed">
                {item.shortDescription}
              </p>
            )}
          </div>

          {/* Sizes & Variants List */}
          {item.variants.length > 0 && (
            <div className="space-y-2 pt-3 border-t border-stone-100">
              <h4 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                Select Portion Size / Variant
              </h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {item.variants.map((v) => {
                  const isSelected = selectedVariantId === v.id;
                  return (
                    <button
                      key={v.id}
                      type="button"
                      onClick={() => setSelectedVariantId(v.id)}
                      className={`flex items-center justify-between p-3 rounded-2xl border text-xs text-left transition-all cursor-pointer ${
                        isSelected
                          ? 'border-emerald-600 bg-emerald-50/50 shadow-xs ring-1 ring-emerald-600'
                          : 'border-stone-200 bg-stone-50 hover:bg-stone-100'
                      }`}
                    >
                      <div>
                        <div className="font-bold text-slate-900">{v.name}</div>
                        {v.description && (
                          <div className="text-[11px] text-stone-500 mt-0.5">{v.description}</div>
                        )}
                      </div>
                      <div className="font-black text-slate-900 ml-2">₹{v.price.toFixed(2)}</div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Option Groups & Add-ons */}
          {item.optionGroups.length > 0 && (
            <div className="space-y-4 pt-3 border-t border-stone-100">
              <h4 className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                Customizers & Add-ons
              </h4>
              {item.optionGroups.map((group) => (
                <div key={group.id} className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-800">{group.name}</span>
                    <span className="text-[11px] text-stone-500">
                      {group.maxSelections === 1 ? 'Choose up to 1' : `Max ${group.maxSelections}`}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {group.options.map((opt) => {
                      const isSelected = selectedOptionIds.includes(opt.id);
                      return (
                        <button
                          key={opt.id}
                          type="button"
                          onClick={() => handleToggleOption(group.id, opt.id, group.maxSelections)}
                          className={`flex items-center justify-between p-2.5 rounded-xl border text-xs transition-all cursor-pointer ${
                            isSelected
                              ? 'border-emerald-600 bg-emerald-50/60 font-semibold ring-1 ring-emerald-600'
                              : 'border-stone-200 bg-white hover:bg-stone-50'
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <div
                              className={`w-4 h-4 rounded-md border flex items-center justify-center text-[10px] ${
                                isSelected
                                  ? 'bg-emerald-600 border-emerald-600 text-white'
                                  : 'border-stone-300 bg-white'
                              }`}
                            >
                              {isSelected && <Check className="w-3 h-3 stroke-3" />}
                            </div>
                            <span className="text-slate-800">{opt.name}</span>
                          </div>
                          <span className="font-semibold text-emerald-800">
                            {opt.additionalPrice > 0 ? `+₹${opt.additionalPrice.toFixed(2)}` : 'Free'}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Special Cooking Instructions */}
          <div className="pt-3 border-t border-stone-100 space-y-1.5">
            <label className="text-xs font-bold text-slate-900 uppercase tracking-wider block">
              Special Instructions
            </label>
            <input
              type="text"
              value={specialInstructions}
              onChange={(e) => setSpecialInstructions(e.target.value)}
              placeholder="e.g. Extra spicy, dressing on the side..."
              maxLength={255}
              className="w-full text-xs p-2.5 rounded-xl border border-stone-200 focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>
        </div>

        {/* Modal Interactive Footer */}
        <div className="p-4 bg-stone-50 border-t border-stone-200 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 bg-white px-3 py-1.5 rounded-2xl border border-stone-200 shadow-xs">
            <button
              type="button"
              onClick={() => setQuantity(Math.max(1, quantity - 1))}
              disabled={quantity <= 1}
              className="p-1 rounded-lg text-slate-700 hover:bg-stone-100 disabled:opacity-40 cursor-pointer"
            >
              <Minus className="w-4 h-4" />
            </button>
            <span className="font-black text-sm text-slate-900 w-5 text-center">{quantity}</span>
            <button
              type="button"
              onClick={() => setQuantity(Math.min(50, quantity + 1))}
              disabled={quantity >= 50}
              className="p-1 rounded-lg text-slate-700 hover:bg-stone-100 disabled:opacity-40 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
            </button>
          </div>

          <Button
            variant="emerald"
            size="md"
            className="flex-1 font-bold flex items-center justify-between px-4 py-2.5"
            onClick={handleConfirmAdd}
            disabled={!item.isAvailable || isAdding}
          >
            <span className="flex items-center gap-2">
              <ShoppingBag className="w-4 h-4" />
              {isAdding ? 'Adding...' : 'Add to Order Cart'}
            </span>
            <span>₹{lineTotal.toFixed(2)}</span>
          </Button>
        </div>
      </div>
    </div>
  );
};
