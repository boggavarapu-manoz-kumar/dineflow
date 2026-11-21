import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  toggleCategoryStatus,
  Category,
} from '@/lib/menuClient';
import { Card, Badge } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Plus, Edit2, Trash2, FolderPlus, CheckCircle, XCircle } from 'lucide-react';

export const CategoryManager: React.FC = () => {
  const queryClient = useQueryClient();
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [displayOrder, setDisplayOrder] = useState<number>(0);
  const [iconName, setIconName] = useState('');
  const [formError, setFormError] = useState('');

  const { data: categories = [], isLoading, error } = useQuery({
    queryKey: ['categories'],
    queryFn: () => getCategories(),
  });

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      resetForm();
    },
    onError: (err: Error) => {
      setFormError(err.message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Category> }) => updateCategory(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      resetForm();
    },
    onError: (err: Error) => {
      setFormError(err.message);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (err: Error) => {
      alert(`Cannot delete category: ${err.message}`);
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) => toggleCategoryStatus(id, isActive),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });

  const resetForm = () => {
    setName('');
    setDescription('');
    setDisplayOrder(0);
    setIconName('');
    setEditingCategory(null);
    setIsCreating(false);
    setFormError('');
  };

  const startEdit = (cat: Category) => {
    setEditingCategory(cat);
    setName(cat.name);
    setDescription(cat.description || '');
    setDisplayOrder(cat.displayOrder);
    setIconName(cat.iconName || '');
    setIsCreating(true);
    setFormError('');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError('Category name is required');
      return;
    }

    if (editingCategory) {
      updateMutation.mutate({
        id: editingCategory.id,
        data: { name: name.trim(), description: description.trim(), displayOrder, iconName },
      });
    } else {
      createMutation.mutate({
        name: name.trim(),
        description: description.trim(),
        displayOrder,
        iconName,
        isActive: true,
      });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-900">Menu Categories</h2>
          <p className="text-sm text-stone-500">Organize food dishes into high-level menu sections</p>
        </div>
        {!isCreating && (
          <Button
            variant="emerald"
            size="sm"
            onClick={() => {
              resetForm();
              setIsCreating(true);
            }}
          >
            <Plus className="w-4 h-4 mr-1.5" />
            Add Category
          </Button>
        )}
      </div>

      {isCreating && (
        <Card className="border-emerald-200 bg-emerald-50/20">
          <h3 className="text-md font-semibold text-slate-900 mb-4">
            {editingCategory ? 'Edit Category' : 'Create New Category'}
          </h3>
          {formError && (
            <div className="p-3 mb-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-medium">
              {formError}
            </div>
          )}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="md:col-span-2">
                <label className="block text-xs font-semibold text-slate-700 mb-1">Category Name *</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Appetizers & Starters"
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Display Order</label>
                <input
                  type="number"
                  value={displayOrder}
                  onChange={(e) => setDisplayOrder(parseInt(e.target.value) || 0)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief summary of items in this category"
                rows={2}
                className="w-full px-3.5 py-2 text-sm rounded-xl border border-stone-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-white"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" size="sm" onClick={resetForm}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="emerald"
                size="sm"
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {editingCategory ? 'Save Changes' : 'Create Category'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-32 bg-stone-200/60 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : error ? (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl text-rose-700 text-sm">
          Failed to load categories: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      ) : categories.length === 0 ? (
        <Card className="text-center py-12 border-dashed border-2">
          <FolderPlus className="w-12 h-12 text-stone-400 mx-auto mb-3" />
          <h4 className="font-semibold text-slate-900">No categories found</h4>
          <p className="text-xs text-stone-500 mt-1 max-w-sm mx-auto">
            Create your first menu category (e.g. Starters, Main Course, Drinks) to start adding dishes.
          </p>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {categories.map((cat) => (
            <Card key={cat.id} className="relative group hover:border-emerald-300 transition-colors">
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <h4 className="font-bold text-slate-900 text-base">{cat.name}</h4>
                    <Badge variant={cat.isActive ? 'emerald' : 'default'} className="text-[10px]">
                      {cat.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </div>
                  <p className="text-xs text-stone-500 mt-1.5 line-clamp-2">{cat.description || 'No description provided'}</p>
                </div>
                <span className="text-xs font-mono bg-stone-100 text-stone-600 px-2 py-0.5 rounded-md">
                  #{cat.displayOrder}
                </span>
              </div>

              <div className="mt-4 pt-3 border-t border-stone-100 flex items-center justify-between text-xs">
                <button
                  onClick={() => statusMutation.mutate({ id: cat.id, isActive: !cat.isActive })}
                  className="flex items-center gap-1 text-stone-600 hover:text-slate-900 font-medium cursor-pointer"
                >
                  {cat.isActive ? (
                    <>
                      <XCircle className="w-3.5 h-3.5 text-amber-500" /> Deactivate
                    </>
                  ) : (
                    <>
                      <CheckCircle className="w-3.5 h-3.5 text-emerald-500" /> Activate
                    </>
                  )}
                </button>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => startEdit(cat)}
                    className="p-1 text-stone-500 hover:text-emerald-600 transition-colors cursor-pointer"
                    title="Edit Category"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Are you sure you want to delete category "${cat.name}"?`)) {
                        deleteMutation.mutate(cat.id);
                      }
                    }}
                    className="p-1 text-stone-500 hover:text-rose-600 transition-colors cursor-pointer"
                    title="Delete Category"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
