import React, { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getCustomerMenu, CustomerMenuItem } from '@/lib/menuClient';
import {
  getCart,
  addCartItem,
  updateCartItemQuantity,
  removeCartItem,
  clearCart,
  AddCartItemPayload,
} from '@/lib/cartClient';
import { MenuItemCard } from './components/MenuItemCard';
import { ItemDetailModal } from './components/ItemDetailModal';
import { CartSlideOver } from './components/CartSlideOver';
import { OrderConfirmationModal } from './components/OrderConfirmationModal';
import { customerOrderClient } from '@/lib/customerOrderClient';
import { useNavigate } from 'react-router-dom';
import { UtensilsCrossed, Search, AlertCircle, RefreshCw, ShoppingBag, Clock } from 'lucide-react';
import { Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const CustomerMenuPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [dietaryFilter, setDietaryFilter] = useState<'ALL' | 'VEG' | 'NON_VEG'>('ALL');
  const [activeItem, setActiveItem] = useState<CustomerMenuItem | null>(null);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [orderError, setOrderError] = useState<string | null>(null);

  // Fetch Menu
  const { data: menuData, isLoading, error, refetch } = useQuery({
    queryKey: ['customerMenu'],
    queryFn: () => getCustomerMenu(),
  });

  // Fetch Customer Cart
  const { data: cartData, isLoading: isCartLoading } = useQuery({
    queryKey: ['customerCart'],
    queryFn: () => getCart(),
  });

  // Fetch existing orders for this customer session to show order history button
  const { data: myOrders = [] } = useQuery({
    queryKey: ['my-customer-orders'],
    queryFn: () => customerOrderClient.getMyOrders(),
    refetchInterval: 10000,
  });

  // Submit Order Mutation
  const submitOrderMutation = useMutation({
    mutationFn: (specialNotes?: string) => {
      const idempotencyKey = `idemp-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      return customerOrderClient.submitOrder(
        {
          specialNotes,
          expectedGrandTotal: cartData?.grandTotal,
        },
        idempotencyKey
      );
    },
    onSuccess: (order) => {
      setIsConfirmModalOpen(false);
      setIsCartOpen(false);
      queryClient.invalidateQueries({ queryKey: ['customerCart'] });
      queryClient.invalidateQueries({ queryKey: ['my-customer-orders'] });
      navigate(`/customer/orders/${order.orderId}`);
    },
    onError: (err: unknown) => {
      setOrderError(err instanceof Error ? err.message : 'Failed to submit order');
    },
  });

  // Mutations
  const addItemMutation = useMutation({
    mutationFn: (payload: AddCartItemPayload) => addCartItem(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customerCart'] });
      setActiveItem(null);
      setIsCartOpen(true);
    },
  });

  const updateQuantityMutation = useMutation({
    mutationFn: ({ cartItemId, quantity }: { cartItemId: string; quantity: number }) =>
      updateCartItemQuantity(cartItemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customerCart'] });
    },
  });

  const removeItemMutation = useMutation({
    mutationFn: (cartItemId: string) => removeCartItem(cartItemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customerCart'] });
    },
  });

  const clearCartMutation = useMutation({
    mutationFn: () => clearCart(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customerCart'] });
    },
  });

  const categories = menuData?.categories || [];

  // Filter items
  const filteredCategories = useMemo(() => {
    return categories
      .filter((cat) => selectedCategoryId === 'ALL' || cat.id === selectedCategoryId)
      .map((cat) => {
        const items = cat.items.filter((item) => {
          const matchesSearch =
            item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            (item.shortDescription &&
              item.shortDescription.toLowerCase().includes(searchQuery.toLowerCase()));

          const matchesDiet =
            dietaryFilter === 'ALL' ||
            (dietaryFilter === 'VEG' &&
              (item.dietaryClassification === 'VEGETARIAN' ||
                item.dietaryClassification === 'VEGAN')) ||
            (dietaryFilter === 'NON_VEG' && item.dietaryClassification === 'NON_VEGETARIAN');

          return matchesSearch && matchesDiet;
        });

        return { ...cat, items };
      })
      .filter((cat) => cat.items.length > 0);
  }, [categories, selectedCategoryId, searchQuery, dietaryFilter]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-stone-50 flex flex-col items-center justify-center p-4">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-emerald-600 mb-4" />
        <p className="text-sm font-semibold text-slate-800">Preparing today's fresh menu...</p>
      </div>
    );
  }

  if (error || !menuData) {
    return (
      <div className="min-h-screen bg-[#FDFBF7] flex flex-col items-center justify-center p-6 text-center">
        <div className="w-14 h-14 bg-rose-100 text-rose-600 rounded-2xl flex items-center justify-center mx-auto mb-4">
          <AlertCircle className="w-7 h-7" />
        </div>
        <h2 className="text-xl font-bold text-slate-900">Unable to Load Digital Menu</h2>
        <p className="text-xs text-stone-500 mt-2 max-w-sm">
          Your dining session may have expired or network connection interrupted. Please rescan
          your table QR code.
        </p>
        <Button variant="emerald" size="sm" className="mt-6" onClick={() => refetch()}>
          <RefreshCw className="w-4 h-4 mr-1.5" />
          Retry Menu Load
        </Button>
      </div>
    );
  }

  const cartTotalItems = cartData?.totalItemCount || 0;
  const cartGrandTotal = cartData?.grandTotal || 0;

  return (
    <div className="min-h-screen bg-[#FDFBF7] text-slate-900 pb-28">
      {/* Restaurant Header Banner */}
      <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-stone-200/80 shadow-xs">
        <div className="max-w-2xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-slate-900 flex items-center justify-center text-emerald-400 shrink-0">
              <UtensilsCrossed className="w-5 h-5" />
            </div>
            <div>
              <h1 className="font-extrabold text-slate-900 text-base leading-tight">
                {menuData.restaurantName}
              </h1>
              <p className="text-[11px] text-stone-500 font-medium">Digital Table Menu</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {myOrders.length > 0 && (
              <button
                onClick={() => navigate(`/customer/orders/${myOrders[0].orderId}`)}
                className="px-2.5 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-bold border border-emerald-200 transition-colors flex items-center gap-1.5 cursor-pointer"
              >
                <Clock className="w-3.5 h-3.5 text-emerald-600" />
                <span>Track Order ({myOrders.length})</span>
              </button>
            )}

            <Badge variant="emerald" className="text-xs px-2.5 py-0.5">
              Table #{menuData.tableNumber}
            </Badge>

            <button
              onClick={() => setIsCartOpen(true)}
              className="relative p-2 rounded-xl bg-stone-100 hover:bg-stone-200 text-slate-900 transition-colors cursor-pointer"
              aria-label="Open Cart"
            >
              <ShoppingBag className="w-5 h-5" />
              {cartTotalItems > 0 && (
                <span className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-emerald-600 text-white font-bold text-[10px] flex items-center justify-center">
                  {cartTotalItems}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Search & Dietary Filters Bar */}
        <div className="max-w-2xl mx-auto px-4 pb-3 space-y-2.5">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-stone-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search dishes, ingredients..."
              className="w-full pl-9 pr-4 py-2 text-xs sm:text-sm rounded-xl border border-stone-200 bg-stone-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500 transition-all"
            />
          </div>

          <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
            <button
              onClick={() => setDietaryFilter('ALL')}
              className={`px-3 py-1 rounded-full text-xs font-semibold shrink-0 transition-colors cursor-pointer ${
                dietaryFilter === 'ALL'
                  ? 'bg-slate-900 text-white'
                  : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
              }`}
            >
              All Dishes
            </button>
            <button
              onClick={() => setDietaryFilter('VEG')}
              className={`px-3 py-1 rounded-full text-xs font-semibold shrink-0 transition-colors cursor-pointer flex items-center gap-1.5 ${
                dietaryFilter === 'VEG'
                  ? 'bg-emerald-600 text-white'
                  : 'bg-stone-100 text-emerald-800 hover:bg-stone-200'
              }`}
            >
              <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
              Veg Only
            </button>
            <button
              onClick={() => setDietaryFilter('NON_VEG')}
              className={`px-3 py-1 rounded-full text-xs font-semibold shrink-0 transition-colors cursor-pointer flex items-center gap-1.5 ${
                dietaryFilter === 'NON_VEG'
                  ? 'bg-rose-600 text-white'
                  : 'bg-stone-100 text-rose-800 hover:bg-stone-200'
              }`}
            >
              <span className="w-2 h-2 rounded-full bg-rose-400 inline-block" />
              Non-Veg Only
            </button>
          </div>
        </div>

        {/* Category Horizontal Navigation */}
        <div className="max-w-2xl mx-auto px-4 pb-2 flex items-center gap-2 overflow-x-auto border-t border-stone-100 pt-2 no-scrollbar">
          <button
            onClick={() => setSelectedCategoryId('ALL')}
            className={`px-3 py-1.5 text-xs font-bold rounded-xl shrink-0 transition-colors cursor-pointer ${
              selectedCategoryId === 'ALL'
                ? 'bg-emerald-50 text-emerald-800 border border-emerald-300'
                : 'text-stone-500 hover:text-slate-900'
            }`}
          >
            Full Menu
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              onClick={() => setSelectedCategoryId(c.id)}
              className={`px-3 py-1.5 text-xs font-bold rounded-xl shrink-0 transition-colors cursor-pointer ${
                selectedCategoryId === c.id
                  ? 'bg-emerald-50 text-emerald-800 border border-emerald-300'
                  : 'text-stone-500 hover:text-slate-900'
              }`}
            >
              {c.name} ({c.items.length})
            </button>
          ))}
        </div>
      </header>

      {/* Main Menu Body */}
      <main className="max-w-2xl mx-auto px-4 pt-6 space-y-8">
        {filteredCategories.length === 0 ? (
          <div className="text-center py-16 bg-white rounded-3xl border border-stone-200 p-8 shadow-xs">
            <UtensilsCrossed className="w-10 h-10 text-stone-300 mx-auto mb-3" />
            <h3 className="font-bold text-slate-800 text-base">No matching dishes found</h3>
            <p className="text-xs text-stone-500 mt-1">
              Try adjusting your search terms or dietary filters to explore other courses.
            </p>
          </div>
        ) : (
          filteredCategories.map((category) => (
            <section key={category.id} className="space-y-3">
              <div className="flex items-baseline justify-between border-b border-stone-200/80 pb-2">
                <div>
                  <h2 className="text-lg font-black text-slate-900 tracking-tight">{category.name}</h2>
                  {category.description && (
                    <p className="text-xs text-stone-500 mt-0.5">{category.description}</p>
                  )}
                </div>
                <span className="text-[11px] font-semibold text-stone-400">
                  {category.items.length} items
                </span>
              </div>

              <div className="space-y-3">
                {category.items.map((item) => (
                  <MenuItemCard key={item.id} item={item} onOpenDetails={setActiveItem} />
                ))}
              </div>
            </section>
          ))
        )}
      </main>

      {/* Floating Bottom Cart Pill (Visible when cart has items) */}
      {cartTotalItems > 0 && (
        <div className="fixed bottom-4 inset-x-0 z-40 max-w-lg mx-auto px-4">
          <button
            onClick={() => setIsCartOpen(true)}
            className="w-full bg-slate-900 text-white p-3.5 rounded-2xl shadow-xl flex items-center justify-between hover:bg-slate-800 transition-all cursor-pointer animate-in slide-in-from-bottom-3 duration-200"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-emerald-500 text-slate-950 font-black text-xs flex items-center justify-center">
                {cartTotalItems}
              </div>
              <div className="text-left">
                <div className="text-xs font-bold leading-tight">View Your Table Cart</div>
                <div className="text-[10px] text-stone-400 font-medium">Table #{menuData.tableNumber}</div>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="font-extrabold text-sm text-emerald-400">
                ₹{cartGrandTotal.toFixed(2)}
              </span>
              <span className="text-xs bg-white/10 px-2 py-0.5 rounded-lg font-semibold">
                Review &rarr;
              </span>
            </div>
          </button>
        </div>
      )}

      {/* Item Detail Modal with Customizer Selections */}
      <ItemDetailModal
        item={activeItem}
        onClose={() => setActiveItem(null)}
        onAddToCart={(payload) => addItemMutation.mutate(payload)}
        isAdding={addItemMutation.isPending}
      />

      {/* Customer Cart SlideOver */}
      <CartSlideOver
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        cart={cartData || null}
        isLoading={isCartLoading}
        onUpdateQuantity={(cartItemId, quantity) =>
          updateQuantityMutation.mutate({ cartItemId, quantity })
        }
        onRemoveItem={(cartItemId) => removeItemMutation.mutate(cartItemId)}
        onClearCart={() => clearCartMutation.mutate()}
        onProceedToCheckout={() => {
          setIsCartOpen(false);
          setOrderError(null);
          setIsConfirmModalOpen(true);
        }}
      />

      {/* Authoritative Order Confirmation Modal */}
      <OrderConfirmationModal
        isOpen={isConfirmModalOpen}
        onClose={() => setIsConfirmModalOpen(false)}
        cart={cartData || null}
        isSubmitting={submitOrderMutation.isPending}
        errorMessage={orderError}
        onConfirmOrder={(notes) => submitOrderMutation.mutate(notes)}
      />
    </div>
  );
};
