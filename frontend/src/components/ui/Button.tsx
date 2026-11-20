import * as React from 'react';
import { cn } from '@/lib/utils';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'emerald';
  size?: 'sm' | 'md' | 'lg';
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', children, ...props }, ref) => {
    const base = 'inline-flex items-center justify-center font-medium rounded-xl transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none cursor-pointer';
    
    const variants = {
      primary: 'bg-slate-900 text-white hover:bg-slate-800 shadow-sm hover:shadow active:scale-[0.98]',
      secondary: 'bg-stone-100 text-slate-800 hover:bg-stone-200 active:scale-[0.98]',
      outline: 'border border-slate-300 bg-transparent text-slate-700 hover:bg-stone-100 hover:border-slate-400',
      ghost: 'bg-transparent text-slate-600 hover:text-slate-900 hover:bg-stone-100',
      emerald: 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-sm hover:shadow-emerald-900/20 active:scale-[0.98]',
    };

    const sizes = {
      sm: 'text-xs px-3 py-1.5 gap-1.5',
      md: 'text-sm px-4 py-2.5 gap-2',
      lg: 'text-base px-6 py-3.5 gap-2.5',
    };

    return (
      <button
        ref={ref}
        className={cn(base, variants[variant], sizes[size], className)}
        {...props}
      >
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';