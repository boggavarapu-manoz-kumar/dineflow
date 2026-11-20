import * as React from 'react';
import { cn } from '@/lib/utils';

export const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn('rounded-2xl border border-stone-200 bg-white p-6 shadow-sm shadow-stone-200/50', className)}
      {...props}
    />
  )
);
Card.displayName = 'Card';

export const Badge = ({
  className,
  variant = 'default',
  children,
}: {
  className?: string;
  variant?: 'default' | 'emerald' | 'navy' | 'warning';
  children: React.ReactNode;
}) => {
  const variants = {
    default: 'bg-stone-100 text-stone-700 border-stone-200',
    emerald: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    navy: 'bg-slate-100 text-slate-800 border-slate-200',
    warning: 'bg-amber-50 text-amber-800 border-amber-200',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold border',
        variants[variant],
        className
      )}
    >
      {children}
    </span>
  );
};