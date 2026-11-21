import React from 'react';
import { CustomerMenuItem } from '@/lib/menuClient';
import { ChevronRight, Image as ImageIcon } from 'lucide-react';

interface MenuItemCardProps {
  item: CustomerMenuItem;
  onOpenDetails: (item: CustomerMenuItem) => void;
}

export const MenuItemCard: React.FC<MenuItemCardProps> = ({ item, onOpenDetails }) => {
  const isVeg = item.dietaryClassification === 'VEGETARIAN' || item.dietaryClassification === 'VEGAN';

  return (
    <div
      onClick={() => onOpenDetails(item)}
      className={`group bg-white rounded-2xl p-4 border border-stone-200/80 shadow-xs hover:border-emerald-500/40 hover:shadow-md transition-all cursor-pointer flex gap-4 ${
        !item.isAvailable ? 'opacity-70 bg-stone-50/60' : ''
      }`}
    >
      {/* Thumbnail */}
      <div className="w-24 h-24 sm:w-28 sm:h-28 rounded-xl bg-stone-100 overflow-hidden shrink-0 relative border border-stone-100">
        {item.primaryImageUrl ? (
          <img
            src={item.primaryImageUrl}
            alt={item.name}
            loading="lazy"
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-stone-300">
            <ImageIcon className="w-8 h-8" />
          </div>
        )}

        {!item.isAvailable && (
          <div className="absolute inset-0 bg-stone-900/60 backdrop-blur-[1px] flex items-center justify-center p-1 text-center">
            <span className="text-[10px] font-bold text-white uppercase tracking-wider">Sold Out</span>
          </div>
        )}
      </div>

      {/* Info */}
      <div className="flex-1 flex flex-col justify-between min-w-0">
        <div>
          <div className="flex items-center gap-1.5 mb-1">
            {/* Veg / Non-Veg Indicator Icon */}
            <span
              className={`w-3.5 h-3.5 rounded-sm border flex items-center justify-center ${
                isVeg ? 'border-emerald-600' : 'border-rose-600'
              }`}
            >
              <span
                className={`w-1.5 h-1.5 rounded-full ${isVeg ? 'bg-emerald-600' : 'bg-rose-600'}`}
              />
            </span>
            <h3 className="font-bold text-slate-900 text-sm sm:text-base truncate group-hover:text-emerald-700 transition-colors">
              {item.name}
            </h3>
          </div>

          <p className="text-xs text-stone-500 line-clamp-2 leading-relaxed">
            {item.shortDescription || item.detailedDescription || 'Chef prepared specialty dish.'}
          </p>
        </div>

        <div className="flex items-center justify-between pt-2 mt-2 border-t border-stone-100">
          <div className="flex items-baseline gap-1.5">
            <span className="text-sm sm:text-base font-extrabold text-slate-900">
              ₹{item.effectivePrice.toFixed(2)}
            </span>
            {item.variants.length > 0 && (
              <span className="text-[11px] text-stone-400 font-medium">starting</span>
            )}
          </div>

          <div className="flex items-center gap-1.5 text-xs text-stone-400 font-medium group-hover:text-emerald-600 transition-colors">
            <span>Details</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </div>
        </div>
      </div>
    </div>
  );
};
