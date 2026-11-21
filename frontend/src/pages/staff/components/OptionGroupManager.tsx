import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getMenuItems,
  createOptionGroup,
  linkOptionGroupToItem,
} from '@/lib/menuClient';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Plus, Layers } from 'lucide-react';

export const OptionGroupManager: React.FC = () => {
  const queryClient = useQueryClient();
  const [isCreatingGroup, setIsCreatingGroup] = useState(false);
  const [groupName, setGroupName] = useState('');
  const [groupDesc, setGroupDesc] = useState('');
  const [minSel, setMinSel] = useState<number>(0);
  const [maxSel, setMaxSel] = useState<number>(1);
  const [isRequired, setIsRequired] = useState(false);

  // Link to item
  const [selectedItemId, setSelectedItemId] = useState('');
  const [targetGroupId] = useState('');

  const { data: pagedItems } = useQuery({
    queryKey: ['menuItems'],
    queryFn: () => getMenuItems(),
  });
  const items = pagedItems?.content || [];

  const createGroupMutation = useMutation({
    mutationFn: createOptionGroup,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      resetGroupForm();
    },
  });

  const linkMutation = useMutation({
    mutationFn: ({ itemId, groupId }: { itemId: string; groupId: string }) =>
      linkOptionGroupToItem(itemId, groupId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['menuItems'] });
      alert('Option group attached to dish successfully!');
    },
  });

  const resetGroupForm = () => {
    setIsCreatingGroup(false);
    setGroupName('');
    setGroupDesc('');
    setMinSel(0);
    setMaxSel(1);
    setIsRequired(false);
  };

  const handleCreateGroup = (e: React.FormEvent) => {
    e.preventDefault();
    if (!groupName.trim()) return;
    createGroupMutation.mutate({
      name: groupName.trim(),
      description: groupDesc.trim(),
      minSelections: minSel,
      maxSelections: maxSel,
      isRequired,
      isActive: true,
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Customizer & Add-on Groups</h2>
          <p className="text-sm text-stone-500">
            Define choices and add-ons (e.g. Cheese, Spice Levels, Extra Toppings) attached to dishes
          </p>
        </div>
        <Button variant="emerald" size="sm" onClick={() => setIsCreatingGroup(!isCreatingGroup)}>
          <Plus className="w-4 h-4 mr-1.5" />
          Create Option Group
        </Button>
      </div>

      {isCreatingGroup && (
        <Card className="border-emerald-200 bg-emerald-50/10">
          <h3 className="text-md font-semibold text-slate-900 mb-4">New Option Group</h3>
          <form onSubmit={handleCreateGroup} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Group Name *</label>
                <input
                  type="text"
                  placeholder="e.g. Extra Pizza Toppings"
                  value={groupName}
                  onChange={(e) => setGroupName(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Description</label>
                <input
                  type="text"
                  placeholder="e.g. Pick up to 3 fresh vegetables"
                  value={groupDesc}
                  onChange={(e) => setGroupDesc(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Min Selections</label>
                <input
                  type="number"
                  value={minSel}
                  onChange={(e) => setMinSel(parseInt(e.target.value) || 0)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Max Selections</label>
                <input
                  type="number"
                  value={maxSel}
                  onChange={(e) => setMaxSel(parseInt(e.target.value) || 1)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 bg-white"
                />
              </div>

              <div className="flex items-center gap-2 pt-6">
                <input
                  type="checkbox"
                  id="req"
                  checked={isRequired}
                  onChange={(e) => setIsRequired(e.target.checked)}
                  className="w-4 h-4 rounded text-emerald-600 focus:ring-emerald-500"
                />
                <label htmlFor="req" className="text-xs font-medium text-slate-800">
                  Required Selection
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" size="sm" onClick={resetGroupForm}>
                Cancel
              </Button>
              <Button type="submit" variant="emerald" size="sm" disabled={createGroupMutation.isPending}>
                Save Group
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Link Group to Dish Section */}
      <Card>
        <h3 className="text-sm font-bold text-slate-900 mb-3 flex items-center gap-2">
          <Layers className="w-4 h-4 text-emerald-600" />
          Attach Add-on Group to Food Item
        </h3>
        <p className="text-xs text-stone-500 mb-4">
          Select any food dish and connect a modifier group so customers can customize it when ordering.
        </p>

        <div className="flex flex-col sm:flex-row gap-3">
          <select
            value={selectedItemId}
            onChange={(e) => setSelectedItemId(e.target.value)}
            className="flex-1 px-3 py-2 text-sm rounded-xl border border-stone-300 bg-white"
          >
            <option value="">Select target dish...</option>
            {items.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name} ({i.categoryName})
              </option>
            ))}
          </select>

          <Button
            variant="secondary"
            size="sm"
            disabled={!selectedItemId || !targetGroupId}
            onClick={() => {
              if (selectedItemId && targetGroupId) {
                linkMutation.mutate({ itemId: selectedItemId, groupId: targetGroupId });
              }
            }}
          >
            Attach Selected Group
          </Button>
        </div>
      </Card>
    </div>
  );
};
