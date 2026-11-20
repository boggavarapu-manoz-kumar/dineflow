import React from 'react';
import { UtensilsCrossed, Shield, Layers, Terminal } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-slate-950 text-stone-300 pt-16 pb-12 border-t border-slate-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10 pb-12 border-b border-slate-900">
          <div className="space-y-4 md:col-span-2">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-slate-800 flex items-center justify-center text-emerald-400">
                <UtensilsCrossed className="w-5 h-5 stroke-[2.2]" />
              </div>
              <span className="font-bold text-xl tracking-tight text-white">Dine<span className="text-emerald-400">Flow</span></span>
            </div>
            <p className="text-sm text-stone-400 max-w-md leading-relaxed">
              Commercial multi-tenant QR-based hotel & restaurant platform. Streamlining contact-free table ordering, real-time kitchen orchestration, and waiter dispatch.
            </p>
            <div className="flex items-center gap-4 text-xs text-stone-400">
              <span className="inline-flex items-center gap-1.5"><Shield className="w-3.5 h-3.5 text-emerald-400" /> Multi-Tenant Isolation</span>
              <span className="inline-flex items-center gap-1.5"><Layers className="w-3.5 h-3.5 text-emerald-400" /> Modular Monolith</span>
              <span className="inline-flex items-center gap-1.5"><Terminal className="w-3.5 h-3.5 text-emerald-400" /> Spring Boot 3+ / React TS</span>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-stone-200 mb-4">5 Experiences</h4>
            <ul className="space-y-2.5 text-sm text-stone-400">
              <li>Customer Web App (Table QR)</li>
              <li>Hotel Admin Dashboard</li>
              <li>Kitchen Display System (KDS)</li>
              <li>Waiter Dispatch App</li>
              <li>SaaS Super Admin</li>
            </ul>
          </div>

          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-stone-200 mb-4">Engineering Standards</h4>
            <ul className="space-y-2.5 text-sm text-stone-400">
              <li>Java 25 LTS & Spring Boot</li>
              <li>Flyway SQL Migrations</li>
              <li>PostgreSQL Partitioning / Tenancy</li>
              <li>TypeScript Strict & TanStack</li>
              <li>Zero Leaked Secrets</li>
            </ul>
          </div>
        </div>

        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between text-xs text-stone-400 gap-4">
          <p>© {new Date().getFullYear()} DineFlow SaaS. All rights reserved. Architectural Foundation Sprint 1.</p>
          <div className="flex items-center gap-6">
            <span className="text-stone-300">DineFlow Core Foundation</span>
            <span>Tagline: Scan. Order. Enjoy.</span>
          </div>
        </div>
      </div>
    </footer>
  );
};