import React from 'react';
import { Link } from 'react-router-dom';
import { UtensilsCrossed, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4">
      <div className="w-14 h-14 rounded-2xl bg-slate-900 flex items-center justify-center text-emerald-400 mb-6">
        <UtensilsCrossed className="w-7 h-7" />
      </div>
      <h1 className="text-4xl font-extrabold text-slate-900">404 - Page Not Found</h1>
      <p className="text-stone-600 text-sm max-w-md mt-2 mb-8">
        The dining route or view you are looking for does not exist or has been moved.
      </p>
      <Link to="/">
        <Button variant="primary">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to DineFlow Home
        </Button>
      </Link>
    </div>
  );
};