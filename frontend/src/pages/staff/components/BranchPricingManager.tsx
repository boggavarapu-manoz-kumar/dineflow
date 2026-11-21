import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getMenuItems, setBranchOverride } from '@/lib/menuClient';
import { Card, Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Store } from 'lucide-react';

export const BranchPricingManager: React.FC = () => {
  const queryClient = useQueryClient();
  const [selectedBranchId, setSelectedBranchId] = useState('00000000-0000-0000-0000-000000000001'); // Demo / Primary Branch
  const [overridePrice, setOverridePrice] = useState<Record<string, number>>({});
  const [inStockStatus, setInStockStatus] = useState<Record<string, boolean>>({});

  const { data: pagedItems, isLoading } = useQuery({
    queryKey: ['menuItems'],
    queryFn: () => getMenuItems(),
  });
  const items = pagedItems?.content || [];

  const overrideMutation = useMutation({
    mutationFn: ({ itemId, branchId, price, inStock }: { itemId: string; branchId: string; price?: number; inStock: boolean }) =>
      setBranchOverride(itemId, branchId, {
        priceOverride: price,
        isAvailable: inStock,
        isVisible: true,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      alert('Branch configuration saved!');
    },
  });

  const branches = [
    { id: '00000000-0000-0000-0000-000000000001', name: 'Downtown Flagship', code: 'MAIN-01' },
    { id: '00000000-0000-0000-0000-000000000002', name: 'Airport Express Outlet', code: 'AIR-02' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Branch Pricing & Stock Availability</h2>
          <p className="text-sm text-stone-500">
            Set branch-specific menu prices and mark out-of-stock items per outlet
          </p>
        </div>

        <div className="flex items-center gap-2 bg-white px-3 py-1.5 rounded-xl border border-stone-200">
          <Store className="w-4 h-4 text-emerald-600" />
          <span className="text-xs font-semibold text-slate-700">Active Branch:</span>
          <select
            value={selectedBranchId}
            onChange={(e) => setSelectedBranchId(e.target.value)}
            className="text-xs font-medium text-slate-900 bg-transparent focus:outline-none"
          >
            {branches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name} ({b.code})
              </option>
            ))}
          </select>
        </div>
      </div>

      <Card className="p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-stone-50 border-b border-stone-200 text-xs font-semibold text-slate-700">
              <tr>
                <th className="py-3 px-4">Food Dish</th>
                <th className="py-3 px-4">Category</th>
                <th className="py-3 px-4">Base Default Price</th>
                <th className="py-3 px-4">Branch Override Price</th>
                <th className="py-3 px-4">Effective Price</th>
                <th className="py-3 px-4">Outlet Stock</th>
                <th className="py-3 px-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {isLoading ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-stone-500">
                    Loading branch catalog...
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-stone-500">
                    No food items available to configure.
                  </td>
                </tr>
              ) : (
                items.map((item) => {
                  const customPrice = overridePrice[item.id] !== undefined ? overridePrice[item.id] : item.basePrice;
                  const isAvailable = inStockStatus[item.id] !== undefined ? inStockStatus[item.id] : item.isAvailable;
                  const hasOverride = overridePrice[item.id] !== undefined && overridePrice[item.id] !== item.basePrice;

                  return (
                    <tr key={item.id} className="hover:bg-stone-50/60 transition-colors">
                      <td className="py-3.5 px-4 font-semibold text-slate-900">{item.name}</td>
                      <td className="py-3.5 px-4 text-xs text-stone-600">{item.categoryName}</td>
                      <td className="py-3.5 px-4 font-mono text-stone-500">₹{item.basePrice.toFixed(2)}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5 w-32">
                          <span className="text-xs text-stone-400">₹</span>
                          <input
                            type="number"
                            step="0.01"
                            value={overridePrice[item.id] !== undefined ? overridePrice[item.id] : ''}
                            placeholder={item.basePrice.toString()}
                            onChange={(e) =>
                              setOverridePrice({
                                ...overridePrice,
                                [item.id]: parseFloat(e.target.value) || item.basePrice,
                              })
                            }
                            className="w-full px-2 py-1 text-xs rounded-lg border border-stone-200 bg-white"
                          />
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className={`font-bold font-mono text-sm ${hasOverride ? 'text-emerald-700' : 'text-slate-900'}`}>
                          ₹{customPrice.toFixed(2)}
                        </span>
                        {hasOverride && (
                          <Badge variant="emerald" className="ml-2 text-[9px] py-0 px-1">
                            Override
                          </Badge>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <button
                          type="button"
                          onClick={() =>
                            setInStockStatus({
                              ...inStockStatus,
                              [item.id]: !isAvailable,
                            })
                          }
                          className="cursor-pointer"
                        >
                          <Badge variant={isAvailable ? 'emerald' : 'default'} className="text-[10px]">
                            {isAvailable ? 'In Stock' : 'Out of Stock'}
                          </Badge>
                        </button>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() =>
                            overrideMutation.mutate({
                              itemId: item.id,
                              branchId: selectedBranchId,
                              price: overridePrice[item.id],
                              inStock: isAvailable,
                            })
                          }
                        >
                          Save
                        </Button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
