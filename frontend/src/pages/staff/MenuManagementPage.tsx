import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getCategories, getMenuItems } from '@/lib/menuClient';
import { Card, Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { CategoryManager } from './components/CategoryManager';
import { MenuItemManager } from './components/MenuItemManager';
import { OptionGroupManager } from './components/OptionGroupManager';
import { BranchPricingManager } from './components/BranchPricingManager';
import {
  LayoutDashboard,
  FolderTree,
  UtensilsCrossed,
  SlidersHorizontal,
  Building2,
  AlertTriangle,
  CheckCircle,
} from 'lucide-react';

type Tab = 'overview' | 'categories' | 'items' | 'customizers' | 'branches';

export const MenuManagementPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('overview');

  const { data: categories = [], isLoading: catsLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => getCategories(),
  });

  const { data: pagedItems, isLoading: itemsLoading } = useQuery({
    queryKey: ['menuItems'],
    queryFn: () => getMenuItems(),
  });

  const items = pagedItems?.content || [];

  const totalCategories = categories.length;
  const totalItems = items.length;
  const activeItems = items.filter((i) => i.isActive).length;
  const outOfStockItems = items.filter((i) => !i.isAvailable).length;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between pb-6 border-b border-stone-200 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
              Menu & Catalog Management
            </h1>
            <Badge variant="emerald">Live Operations</Badge>
          </div>
          <p className="text-sm text-stone-500 mt-1">
            Organize dishes, variants, custom modifiers, and branch pricing across DineFlow
          </p>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto py-4 border-b border-stone-200 text-sm font-medium">
        <button
          onClick={() => setActiveTab('overview')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'overview'
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-stone-600 hover:text-slate-900 hover:bg-stone-100'
          }`}
        >
          <LayoutDashboard className="w-4 h-4" />
          Overview
        </button>

        <button
          onClick={() => setActiveTab('categories')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'categories'
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-stone-600 hover:text-slate-900 hover:bg-stone-100'
          }`}
        >
          <FolderTree className="w-4 h-4" />
          Categories ({totalCategories})
        </button>

        <button
          onClick={() => setActiveTab('items')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'items'
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-stone-600 hover:text-slate-900 hover:bg-stone-100'
          }`}
        >
          <UtensilsCrossed className="w-4 h-4" />
          Dishes & Items ({totalItems})
        </button>

        <button
          onClick={() => setActiveTab('customizers')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'customizers'
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-stone-600 hover:text-slate-900 hover:bg-stone-100'
          }`}
        >
          <SlidersHorizontal className="w-4 h-4" />
          Modifiers & Add-ons
        </button>

        <button
          onClick={() => setActiveTab('branches')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
            activeTab === 'branches'
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-stone-600 hover:text-slate-900 hover:bg-stone-100'
          }`}
        >
          <Building2 className="w-4 h-4" />
          Branch Overrides
        </button>
      </div>

      {/* Main Content Areas */}
      <div className="py-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* KPI Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <Card className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                  <FolderTree className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-xs text-stone-500 font-medium">Menu Categories</p>
                  <h3 className="text-2xl font-bold text-slate-900 mt-0.5">{catsLoading ? '...' : totalCategories}</h3>
                </div>
              </Card>

              <Card className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center">
                  <UtensilsCrossed className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-xs text-stone-500 font-medium">Total Food Items</p>
                  <h3 className="text-2xl font-bold text-slate-900 mt-0.5">{itemsLoading ? '...' : totalItems}</h3>
                </div>
              </Card>

              <Card className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-700 flex items-center justify-center">
                  <CheckCircle className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-xs text-stone-500 font-medium">Active In-Service</p>
                  <h3 className="text-2xl font-bold text-slate-900 mt-0.5">{itemsLoading ? '...' : activeItems}</h3>
                </div>
              </Card>

              <Card className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
                  <AlertTriangle className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-xs text-stone-500 font-medium">Out of Stock</p>
                  <h3 className="text-2xl font-bold text-slate-900 mt-0.5">{itemsLoading ? '...' : outOfStockItems}</h3>
                </div>
              </Card>
            </div>

            {/* Quick Actions & Recent Snapshot */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <Card className="lg:col-span-2">
                <h3 className="text-base font-bold text-slate-900 mb-3">Live Menu Breakdown</h3>
                <div className="divide-y divide-stone-100">
                  {categories.map((c) => {
                    const count = items.filter((i) => i.categoryId === c.id).length;
                    return (
                      <div key={c.id} className="py-3 flex items-center justify-between text-sm">
                        <span className="font-semibold text-slate-800">{c.name}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-stone-500 font-medium">{count} dishes</span>
                          <Badge variant={c.isActive ? 'emerald' : 'default'} className="text-[10px]">
                            {c.isActive ? 'Active' : 'Inactive'}
                          </Badge>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>

              <Card className="bg-stone-50 border-stone-200/80">
                <h3 className="text-base font-bold text-slate-900 mb-2">Publishing & Catalog Status</h3>
                <p className="text-xs text-stone-500 mb-4">
                  Changes to dishes and category order are synchronized in real-time across QR dining tables.
                </p>
                <div className="space-y-3">
                  <div className="p-3 bg-white rounded-xl border border-stone-200 text-xs">
                    <span className="font-semibold text-slate-800 block">Single Truth Multi-Tenant Isolation</span>
                    <span className="text-stone-500 mt-0.5 block">Isolated under active authenticated tenant context</span>
                  </div>
                  <Button variant="emerald" className="w-full text-xs" onClick={() => setActiveTab('items')}>
                    Manage Dishes Now
                  </Button>
                </div>
              </Card>
            </div>
          </div>
        )}

        {activeTab === 'categories' && <CategoryManager />}
        {activeTab === 'items' && <MenuItemManager />}
        {activeTab === 'customizers' && <OptionGroupManager />}
        {activeTab === 'branches' && <BranchPricingManager />}
      </div>
    </div>
  );
};
