import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getMenuItems,
  getCategories,
  createMenuItem,
  updateMenuItem,
  deleteMenuItem,
  addVariant,
  uploadMenuItemImage,
  MenuItemSummary,
  DietaryClassification,
} from '@/lib/menuClient';
import { Card, Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Plus, Search, Edit3, Trash2, Image, Layers } from 'lucide-react';

export const MenuItemManager: React.FC = () => {
  const queryClient = useQueryClient();
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [dietaryFilter, setDietaryFilter] = useState<string>('ALL');
  
  // Editor Drawer/Modal state
  const [isEditing, setIsEditing] = useState(false);
  const [editingItemId, setEditingItemId] = useState<string | null>(null);

  // Form Fields
  const [categoryId, setCategoryId] = useState('');
  const [name, setName] = useState('');
  const [shortDesc, setShortDesc] = useState('');
  const [detailedDesc, setDetailedDesc] = useState('');
  const [basePrice, setBasePrice] = useState<number>(0);
  const [dietary, setDietary] = useState<DietaryClassification>('VEGETARIAN');
  const [prepTime, setPrepTime] = useState<number>(15);
  const [displayOrder, setDisplayOrder] = useState<number>(0);
  const [formError, setFormError] = useState('');

  // Variant helper inside modal
  const [variantName, setVariantName] = useState('');
  const [variantPrice, setVariantPrice] = useState<number>(0);

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => getCategories(),
  });

  const { data: pagedItems, isLoading } = useQuery({
    queryKey: ['menuItems', selectedCategory],
    queryFn: () => getMenuItems(selectedCategory || undefined),
  });

  const items = pagedItems?.content || [];

  const createMutation = useMutation({
    mutationFn: createMenuItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      resetForm();
    },
    onError: (err: Error) => setFormError(err.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof updateMenuItem>[1] }) =>
      updateMenuItem(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      resetForm();
    },
    onError: (err: Error) => setFormError(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteMenuItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
    },
  });

  const variantMutation = useMutation({
    mutationFn: ({ itemId, variant }: { itemId: string; variant: Parameters<typeof addVariant>[1] }) =>
      addVariant(itemId, variant),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      setVariantName('');
      setVariantPrice(0);
    },
  });

  const resetForm = () => {
    setIsEditing(false);
    setEditingItemId(null);
    setCategoryId(categories[0]?.id || '');
    setName('');
    setShortDesc('');
    setDetailedDesc('');
    setBasePrice(0);
    setDietary('VEGETARIAN');
    setPrepTime(15);
    setDisplayOrder(0);
    setFormError('');
  };

  const handleStartEdit = (item: MenuItemSummary) => {
    setEditingItemId(item.id);
    setCategoryId(item.categoryId);
    setName(item.name);
    setShortDesc(item.shortDescription || '');
    setBasePrice(item.basePrice);
    setDietary(item.dietaryClassification);
    setDisplayOrder(item.displayOrder);
    setIsEditing(true);
    setFormError('');
  };

  const handleSaveItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError('Item name is required');
      return;
    }
    if (basePrice < 0) {
      setFormError('Base price cannot be negative');
      return;
    }

    const payload = {
      categoryId,
      name: name.trim(),
      shortDescription: shortDesc.trim(),
      detailedDescription: detailedDesc.trim(),
      basePrice,
      dietaryClassification: dietary,
      preparationTimeMinutes: prepTime,
      displayOrder,
      isActive: true,
      isAvailable: true,
    };

    if (editingItemId) {
      updateMutation.mutate({ id: editingItemId, data: payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>, itemId: string) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      await uploadMenuItemImage(itemId, file, true);
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
    } catch (err: unknown) {
      alert(`Image upload failed: ${err instanceof Error ? err.message : 'Upload error'}`);
    }
  };

  const filteredItems = items.filter((item) => {
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesDiet = dietaryFilter === 'ALL' || item.dietaryClassification === dietaryFilter;
    return matchesSearch && matchesDiet;
  });

  const dietaryColors: Record<DietaryClassification, { badge: 'emerald' | 'warning' | 'default' | 'navy'; label: string }> = {
    VEGETARIAN: { badge: 'emerald', label: 'Veg' },
    NON_VEGETARIAN: { badge: 'warning', label: 'Non-Veg' },
    VEGAN: { badge: 'emerald', label: 'Vegan' },
    CONTAINS_EGG: { badge: 'warning', label: 'Egg' },
    JAIN: { badge: 'emerald', label: 'Jain' },
  };

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Food Items & Dishes</h2>
          <p className="text-sm text-stone-500">Manage individual dishes, descriptions, pricing, and variants</p>
        </div>
        <Button
          variant="emerald"
          size="sm"
          onClick={() => {
            resetForm();
            setIsEditing(true);
          }}
        >
          <Plus className="w-4 h-4 mr-1.5" />
          Add Food Item
        </Button>
      </div>

      {/* Filter Toolbar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3 bg-stone-100 rounded-2xl border border-stone-200">
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search items by name..."
            className="w-full pl-9 pr-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        <select
          value={selectedCategory}
          onChange={(e) => setSelectedCategory(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">All Categories ({categories.length})</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>

        <select
          value={dietaryFilter}
          onChange={(e) => setDietaryFilter(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="ALL">All Dietary Classifications</option>
          <option value="VEGETARIAN">Vegetarian</option>
          <option value="NON_VEGETARIAN">Non-Vegetarian</option>
          <option value="VEGAN">Vegan</option>
          <option value="CONTAINS_EGG">Contains Egg</option>
          <option value="JAIN">Jain</option>
        </select>
      </div>

      {/* Item Form Card */}
      {isEditing && (
        <Card className="border-emerald-200 bg-emerald-50/10">
          <h3 className="text-md font-semibold text-slate-900 mb-4">
            {editingItemId ? 'Edit Food Item' : 'Create New Food Item'}
          </h3>
          {formError && (
            <div className="p-3 mb-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-medium">
              {formError}
            </div>
          )}

          <form onSubmit={handleSaveItem} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="md:col-span-2">
                <label className="block text-xs font-semibold text-slate-700 mb-1">Item Name *</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Classic Margherita Pizza"
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Category *</label>
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full px-3 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                >
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Base Price (INR) *</label>
                <input
                  type="number"
                  step="0.01"
                  value={basePrice}
                  onChange={(e) => setBasePrice(parseFloat(e.target.value) || 0)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Dietary Tag</label>
                <select
                  value={dietary}
                  onChange={(e) => setDietary(e.target.value as DietaryClassification)}
                  className="w-full px-3 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                >
                  <option value="VEGETARIAN">Vegetarian</option>
                  <option value="NON_VEGETARIAN">Non-Vegetarian</option>
                  <option value="VEGAN">Vegan</option>
                  <option value="CONTAINS_EGG">Contains Egg</option>
                  <option value="JAIN">Jain</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Prep Time (Mins)</label>
                <input
                  type="number"
                  value={prepTime}
                  onChange={(e) => setPrepTime(parseInt(e.target.value) || 0)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Short Description</label>
              <textarea
                value={shortDesc}
                onChange={(e) => setShortDesc(e.target.value)}
                rows={2}
                placeholder="Delicious one-sentence customer teaser"
                className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" size="sm" onClick={resetForm}>
                Cancel
              </Button>
              <Button type="submit" variant="emerald" size="sm" disabled={createMutation.isPending || updateMutation.isPending}>
                {editingItemId ? 'Update Item' : 'Create Item'}
              </Button>
            </div>
          </form>

          {/* Quick Variant Addition if item exists */}
          {editingItemId && (
            <div className="mt-6 pt-6 border-t border-stone-200">
              <h4 className="text-sm font-bold text-slate-900 mb-2">Sizes & Variants</h4>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder="Variant name (e.g. Regular, Large)"
                  value={variantName}
                  onChange={(e) => setVariantName(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-stone-300 bg-white w-48"
                />
                <input
                  type="number"
                  placeholder="Price"
                  value={variantPrice || ''}
                  onChange={(e) => setVariantPrice(parseFloat(e.target.value) || 0)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-stone-300 bg-white w-28"
                />
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    if (variantName && editingItemId) {
                      variantMutation.mutate({
                        itemId: editingItemId,
                        variant: { name: variantName, price: variantPrice },
                      });
                    }
                  }}
                >
                  Add Variant
                </Button>
              </div>
            </div>
          )}
        </Card>
      )}

      {/* Item Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="h-44 bg-stone-200/60 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : filteredItems.length === 0 ? (
        <Card className="text-center py-12 border-dashed border-2">
          <Layers className="w-12 h-12 text-stone-400 mx-auto mb-3" />
          <h4 className="font-semibold text-slate-900">No food items found</h4>
          <p className="text-xs text-stone-500 mt-1 max-w-sm mx-auto">
            {searchQuery ? 'No dishes matched your search criteria.' : 'Start adding delicious food items to this category.'}
          </p>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredItems.map((item) => (
            <Card key={item.id} className="relative group hover:border-emerald-300 transition-all flex flex-col justify-between">
              <div>
                <div className="flex items-start gap-3">
                  <div className="w-14 h-14 rounded-xl bg-stone-100 border border-stone-200 flex items-center justify-center overflow-hidden shrink-0 relative group/img">
                    {item.primaryImageUrl ? (
                      <img src={item.primaryImageUrl} alt={item.name} className="w-full h-full object-cover" />
                    ) : (
                      <Image className="w-6 h-6 text-stone-400" />
                    )}
                    <label className="absolute inset-0 bg-black/50 text-white opacity-0 group-hover/img:opacity-100 flex items-center justify-center cursor-pointer text-[10px] font-medium transition-opacity">
                      Upload
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp"
                        className="hidden"
                        onChange={(e) => handleImageUpload(e, item.id)}
                      />
                    </label>
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <h4 className="font-bold text-slate-900 text-sm truncate">{item.name}</h4>
                    </div>
                    <p className="text-[11px] text-emerald-700 font-medium">{item.categoryName}</p>
                    <p className="text-xs text-stone-500 mt-1 line-clamp-2">{item.shortDescription || 'No description'}</p>
                  </div>
                </div>

                <div className="flex items-center justify-between mt-3 pt-2.5 border-t border-stone-100">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-slate-900 text-sm">₹{item.basePrice.toFixed(2)}</span>
                    <Badge variant={dietaryColors[item.dietaryClassification].badge} className="text-[10px] py-0 px-2">
                      {dietaryColors[item.dietaryClassification].label}
                    </Badge>
                  </div>
                  <Badge variant={item.isAvailable ? 'emerald' : 'default'} className="text-[10px]">
                    {item.isAvailable ? 'In Stock' : 'Out of Stock'}
                  </Badge>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 mt-3 pt-2 border-t border-stone-50">
                <button
                  onClick={() => handleStartEdit(item)}
                  className="p-1.5 text-stone-500 hover:text-emerald-600 rounded-lg hover:bg-stone-50 transition-colors cursor-pointer"
                  title="Edit Dish"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Are you sure you want to delete dish "${item.name}"?`)) {
                      deleteMutation.mutate(item.id);
                    }
                  }}
                  className="p-1.5 text-stone-500 hover:text-rose-600 rounded-lg hover:bg-stone-50 transition-colors cursor-pointer"
                  title="Delete Dish"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
