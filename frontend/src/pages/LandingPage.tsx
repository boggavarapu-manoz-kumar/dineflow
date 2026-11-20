import React, { useState } from 'react';
import { 
  QrCode, 
  Smartphone, 
  ChefHat, 
  ConciergeBell, 
  Building2, 
  Gamepad2, 
  ShieldCheck, 
  ArrowRight, 
  CheckCircle2, 
  Sparkles, 
  Clock, 
  TrendingUp, 
  Database, 
  Server
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card, Badge } from '@/components/ui/Card';

export const LandingPage: React.FC = () => {
  const [interactiveCartCount, setInteractiveCartCount] = useState<number>(2);
  const [orderPlaced, setOrderPlaced] = useState<boolean>(false);

  const sampleItems = [
    { id: 1, name: 'Truffle Mushroom Risotto', price: '₹540', tag: "Chef's Special" },
    { id: 2, name: 'Charcoal Smoked Paneer Tikka', price: '₹420', tag: 'Popular' },
    { id: 3, name: 'Artisan Berry Kombucha', price: '₹220', tag: 'Beverage' },
  ];

  return (
    <div className="space-y-24 py-8">
      {/* Hero Section */}
      <section className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8 pb-12">
        <div className="text-center max-w-3xl mx-auto space-y-6">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold tracking-wide">
            <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
            <span>Next-Gen Multi-Tenant Restaurant Hospitality Platform</span>
          </div>

          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.15]">
            Scan. Order. <span className="text-emerald-600 underline decoration-emerald-300 underline-offset-4">Enjoy.</span>
          </h1>

          <p className="text-lg sm:text-xl text-stone-600 leading-relaxed font-normal">
            Eliminate ordering friction. Guests scan table QR codes, place instant orders without app downloads or account friction, and stay engaged with lightweight browser games while orders route directly to kitchen displays.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
            <a href="#workflow">
              <Button variant="emerald" size="lg" className="w-full sm:w-auto">
                <QrCode className="w-5 h-5 mr-2" />
                Live Customer Demo
              </Button>
            </a>
            <a href="#dashboard-preview">
              <Button variant="primary" size="lg" className="w-full sm:w-auto">
                <Building2 className="w-5 h-5 mr-2" />
                Hotel Admin Console
              </Button>
            </a>
          </div>

          {/* Key Stat Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-10">
            <div className="p-4 rounded-xl bg-white border border-stone-200 text-left">
              <p className="text-2xl font-bold text-slate-900">0s</p>
              <p className="text-xs text-stone-500 font-medium mt-0.5">Account Setup Delay</p>
            </div>
            <div className="p-4 rounded-xl bg-white border border-stone-200 text-left">
              <p className="text-2xl font-bold text-slate-900">5 in 1</p>
              <p className="text-xs text-stone-500 font-medium mt-0.5">Unified Role Experiences</p>
            </div>
            <div className="p-4 rounded-xl bg-white border border-stone-200 text-left">
              <p className="text-2xl font-bold text-emerald-600">100%</p>
              <p className="text-xs text-stone-500 font-medium mt-0.5">Tenant Data Isolation</p>
            </div>
            <div className="p-4 rounded-xl bg-white border border-stone-200 text-left">
              <p className="text-2xl font-bold text-slate-900">&lt; 150ms</p>
              <p className="text-xs text-stone-500 font-medium mt-0.5">API Dispatch Latency</p>
            </div>
          </div>
        </div>
      </section>

      {/* 5 Distinct Application Experiences */}
      <section id="experiences" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge variant="navy" className="mb-2">Unified Platform Architecture</Badge>
          <h2 className="text-3xl font-bold tracking-tight text-slate-900">Five Tailored SaaS Experiences</h2>
          <p className="text-stone-600 text-sm mt-2">
            Each role interacts with a purpose-built view designed for high-throughput restaurant environments.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
          <Card className="hover:border-emerald-300 transition-all flex flex-col justify-between">
            <div className="space-y-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-700">
                <Smartphone className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-900 text-base">1. Customer App</h3>
              <p className="text-xs text-stone-600 leading-relaxed">
                Scan table QR, no passwords, browse rich visual menu, add items to cart, track real-time prep status, and play wait games.
              </p>
            </div>
            <Badge variant="emerald" className="mt-4 self-start">Mobile First</Badge>
          </Card>

          <Card className="hover:border-slate-400 transition-all flex flex-col justify-between">
            <div className="space-y-3">
              <div className="w-10 h-10 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-800">
                <Building2 className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-900 text-base">2. Hotel Admin</h3>
              <p className="text-xs text-stone-600 leading-relaxed">
                Configure table QR codes, manage staff roster, modify real-time item pricing/availability, and audit daily sales reports.
              </p>
            </div>
            <Badge variant="navy" className="mt-4 self-start">Management</Badge>
          </Card>

          <Card className="hover:border-amber-300 transition-all flex flex-col justify-between">
            <div className="space-y-3">
              <div className="w-10 h-10 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center text-amber-800">
                <ChefHat className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-900 text-base">3. Kitchen Display</h3>
              <p className="text-xs text-stone-600 leading-relaxed">
                Real-time touch-screen tickets sorted by prep urgency, timer indicators, modifier notes, and order ready triggers.
              </p>
            </div>
            <Badge variant="warning" className="mt-4 self-start">KDS Station</Badge>
          </Card>

          <Card className="hover:border-blue-300 transition-all flex flex-col justify-between">
            <div className="space-y-3">
              <div className="w-10 h-10 rounded-xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-800">
                <ConciergeBell className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-900 text-base">4. Waiter App</h3>
              <p className="text-xs text-stone-600 leading-relaxed">
                Vibration alerts when orders leave kitchen, table dispatch routing, service call responses, and cash/card receipt closure.
              </p>
            </div>
            <Badge variant="default" className="mt-4 self-start">Handheld Staff</Badge>
          </Card>

          <Card className="hover:border-purple-300 transition-all flex flex-col justify-between">
            <div className="space-y-3">
              <div className="w-10 h-10 rounded-xl bg-purple-50 border border-purple-200 flex items-center justify-center text-purple-800">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h3 className="font-semibold text-slate-900 text-base">5. SaaS Super Admin</h3>
              <p className="text-xs text-stone-600 leading-relaxed">
                Global platform oversight: onboard new hotel tenants, manage billing tier subscriptions, and inspect cross-tenant health metrics.
              </p>
            </div>
            <Badge variant="navy" className="mt-4 self-start">Platform Owner</Badge>
          </Card>
        </div>
      </section>

      {/* Customer Ordering Workflow Preview */}
      <section id="workflow" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="rounded-3xl bg-slate-900 text-white p-8 sm:p-12 shadow-xl shadow-slate-900/10">
          <div className="max-w-3xl mb-10">
            <Badge variant="emerald" className="bg-emerald-950/80 text-emerald-300 border-emerald-800 mb-3">
              Interactive Workflow Preview
            </Badge>
            <h2 className="text-2xl sm:text-4xl font-bold tracking-tight">
              Customer Journey: Zero Friction Table Ordering
            </h2>
            <p className="text-slate-400 text-sm sm:text-base mt-2">
              Test the preview ordering interaction below to inspect how guest inputs translate into state updates.
            </p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Step list */}
            <div className="lg:col-span-5 space-y-4">
              <div className="p-4 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm shrink-0">1</div>
                <div>
                  <h4 className="text-sm font-semibold text-white">QR Code Scan</h4>
                  <p className="text-xs text-slate-400 mt-0.5">Scanned Table 04 at The Grand Heritage Hotel. Session bound securely via Tenant Context.</p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm shrink-0">2</div>
                <div>
                  <h4 className="text-sm font-semibold text-white">Guest Identification</h4>
                  <p className="text-xs text-slate-400 mt-0.5">Name and Mobile number entered. No password or email verification friction.</p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm shrink-0">3</div>
                <div>
                  <h4 className="text-sm font-semibold text-white">Kitchen & Waiter Synchronization</h4>
                  <p className="text-xs text-slate-400 mt-0.5">Kitchen KDS alerted in real-time. Waiter assigned for table dispatch.</p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex items-start gap-3">
                <div className="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm shrink-0">4</div>
                <div>
                  <h4 className="text-sm font-semibold text-white">In-App Entertainment</h4>
                  <p className="text-xs text-slate-400 mt-0.5">Guests play fast HTML5 memory & trivia mini-games right in mobile browser while meal cooks.</p>
                </div>
              </div>
            </div>

            {/* Simulated Mobile Mockup */}
            <div className="lg:col-span-7">
              <div className="max-w-md mx-auto bg-stone-50 text-slate-900 rounded-3xl p-5 shadow-2xl border-4 border-slate-800">
                <div className="flex items-center justify-between border-b border-stone-200 pb-3 mb-4">
                  <div>
                    <span className="text-[10px] font-bold tracking-wider text-emerald-700 uppercase">The Grand Heritage</span>
                    <h4 className="font-bold text-slate-900 text-sm">Table #04 (Dine-in)</h4>
                  </div>
                  <Badge variant="emerald" className="text-[11px]">Guest: Rahul S.</Badge>
                </div>

                {orderPlaced ? (
                  <div className="py-8 text-center space-y-4">
                    <div className="w-14 h-14 bg-emerald-100 text-emerald-700 rounded-full flex items-center justify-center mx-auto">
                      <CheckCircle2 className="w-8 h-8" />
                    </div>
                    <div>
                      <h4 className="text-lg font-bold text-slate-900">Order #DF-8092 Placed!</h4>
                      <p className="text-xs text-stone-500 mt-1">Kitchen station preparing your food (Estimated: 14 mins).</p>
                    </div>

                    <div className="p-3 bg-stone-100 rounded-xl text-left border border-stone-200 text-xs space-y-1">
                      <div className="flex justify-between font-medium"><span>Truffle Mushroom Risotto</span><span>₹540</span></div>
                      <div className="flex justify-between font-medium"><span>Charcoal Paneer Tikka</span><span>₹420</span></div>
                      <div className="flex justify-between text-stone-500 pt-1 border-t border-stone-200 font-semibold"><span>Total</span><span>₹960</span></div>
                    </div>

                    <div className="pt-2">
                      <Button 
                        variant="emerald" 
                        size="sm" 
                        className="w-full flex items-center justify-center gap-2"
                        onClick={() => alert("Launching lightweight waiting mini-game: DineFlow Chef Memory Trivia")}
                      >
                        <Gamepad2 className="w-4 h-4" />
                        Play Trivia While Waiting
                      </Button>
                      <button 
                        onClick={() => setOrderPlaced(false)} 
                        className="text-[11px] text-stone-500 hover:text-slate-900 underline mt-3 block mx-auto cursor-pointer"
                      >
                        Reset Demo
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <p className="text-xs font-semibold text-stone-500 uppercase tracking-wider">Chef Recommendations</p>
                    {sampleItems.map((item) => (
                      <div key={item.id} className="p-3 rounded-xl bg-white border border-stone-200 flex items-center justify-between">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-xs text-slate-900">{item.name}</span>
                            <span className="text-[9px] px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">{item.tag}</span>
                          </div>
                          <span className="text-xs font-bold text-stone-700">{item.price}</span>
                        </div>
                        <Button 
                          variant="secondary" 
                          size="sm" 
                          className="text-xs h-7 px-2.5"
                          onClick={() => setInteractiveCartCount(prev => prev + 1)}
                        >
                          + Add
                        </Button>
                      </div>
                    ))}

                    <div className="pt-3 border-t border-stone-200">
                      <div className="flex items-center justify-between text-xs mb-2">
                        <span className="text-stone-600 font-medium">Items in Cart:</span>
                        <span className="font-bold text-slate-900">{interactiveCartCount} items (₹960)</span>
                      </div>
                      <Button 
                        variant="primary" 
                        size="md" 
                        className="w-full justify-between"
                        onClick={() => setOrderPlaced(true)}
                      >
                        <span>Place Table Order</span>
                        <ArrowRight className="w-4 h-4" />
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Hotel Dashboard Preview */}
      <section id="dashboard-preview" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-8 gap-4">
          <div>
            <Badge variant="navy" className="mb-2">Hotel Management Experience</Badge>
            <h2 className="text-3xl font-bold tracking-tight text-slate-900">Hotel Administration Operations</h2>
            <p className="text-stone-600 text-sm mt-1">
              Live operational metrics for manager oversight across active tables and staff allocations.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-stone-500 font-medium">Current Hotel:</span>
            <Badge variant="emerald">The Grand Heritage (Tenant ID: 0192e4a8-...)</Badge>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          <Card className="space-y-2">
            <div className="flex items-center justify-between text-stone-500">
              <span className="text-xs font-semibold uppercase tracking-wider">Active Tables</span>
              <Building2 className="w-4 h-4 text-emerald-600" />
            </div>
            <div className="text-3xl font-bold text-slate-900">18 <span className="text-sm font-normal text-stone-500">/ 24</span></div>
            <p className="text-xs text-emerald-700 font-medium">75% Occupancy Rate</p>
          </Card>

          <Card className="space-y-2">
            <div className="flex items-center justify-between text-stone-500">
              <span className="text-xs font-semibold uppercase tracking-wider">Today's Revenue</span>
              <TrendingUp className="w-4 h-4 text-emerald-600" />
            </div>
            <div className="text-3xl font-bold text-slate-900">₹84,250</div>
            <p className="text-xs text-emerald-700 font-medium">+18% vs yesterday</p>
          </Card>

          <Card className="space-y-2">
            <div className="flex items-center justify-between text-stone-500">
              <span className="text-xs font-semibold uppercase tracking-wider">Avg Prep Time</span>
              <Clock className="w-4 h-4 text-amber-600" />
            </div>
            <div className="text-3xl font-bold text-slate-900">14.2m</div>
            <p className="text-xs text-stone-500 font-medium">Target: &lt; 18.0m</p>
          </Card>

          <Card className="space-y-2">
            <div className="flex items-center justify-between text-stone-500">
              <span className="text-xs font-semibold uppercase tracking-wider">Staff on Duty</span>
              <ConciergeBell className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-3xl font-bold text-slate-900">7 Waiters</div>
            <p className="text-xs text-emerald-700 font-medium">3 Kitchen Chefs active</p>
          </Card>
        </div>

        {/* Live Orders Table Mockup */}
        <div className="mt-8 overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
          <div className="px-6 py-4 border-b border-stone-200 flex items-center justify-between">
            <h3 className="font-semibold text-slate-900 text-sm">Real-time Order Feed (Hotel Tenant Partition)</h3>
            <span className="text-xs text-stone-500">Auto-refreshes via WebSocket / SSE</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-stone-50 text-[11px] font-semibold text-stone-500 uppercase tracking-wider border-b border-stone-200">
                <tr>
                  <th className="px-6 py-3">Order ID</th>
                  <th className="px-6 py-3">Table</th>
                  <th className="px-6 py-3">Customer</th>
                  <th className="px-6 py-3">Items</th>
                  <th className="px-6 py-3">Total</th>
                  <th className="px-6 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100 text-stone-700 text-xs">
                <tr>
                  <td className="px-6 py-3.5 font-mono font-medium text-slate-900">#DF-8092</td>
                  <td className="px-6 py-3.5 font-medium">Table 04</td>
                  <td className="px-6 py-3.5">Rahul S. (+91 98765 43210)</td>
                  <td className="px-6 py-3.5">2 items (Risotto, Paneer Tikka)</td>
                  <td className="px-6 py-3.5 font-bold">₹960</td>
                  <td className="px-6 py-3.5"><Badge variant="warning">In Kitchen</Badge></td>
                </tr>
                <tr>
                  <td className="px-6 py-3.5 font-mono font-medium text-slate-900">#DF-8091</td>
                  <td className="px-6 py-3.5 font-medium">Table 11</td>
                  <td className="px-6 py-3.5">Priya M. (+91 91234 56789)</td>
                  <td className="px-6 py-3.5">3 items (Kombucha, Pasta, Brownie)</td>
                  <td className="px-6 py-3.5 font-bold">₹1,120</td>
                  <td className="px-6 py-3.5"><Badge variant="emerald">Served</Badge></td>
                </tr>
                <tr>
                  <td className="px-6 py-3.5 font-mono font-medium text-slate-900">#DF-8090</td>
                  <td className="px-6 py-3.5 font-medium">Table 02</td>
                  <td className="px-6 py-3.5">Vikram A. (+91 99887 76655)</td>
                  <td className="px-6 py-3.5">1 item (Cold Coffee)</td>
                  <td className="px-6 py-3.5 font-bold">₹240</td>
                  <td className="px-6 py-3.5"><Badge variant="navy">Completed</Badge></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* Multi-Tenant Architectural Principles */}
      <section id="architecture" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="rounded-3xl border border-stone-200 bg-white p-8 sm:p-12">
          <div className="max-w-2xl mb-8">
            <Badge variant="navy" className="mb-2">Sprint 1 Non-Negotiables</Badge>
            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
              Commercial-Grade Multi-Tenant Isolation
            </h2>
            <p className="text-stone-600 text-sm mt-1">
              Engineered from day one to safeguard customer and hotel records across strict boundaries.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="space-y-2 p-4 rounded-xl bg-stone-50 border border-stone-200">
              <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 flex items-center justify-center font-bold text-xs">
                <ShieldCheck className="w-4 h-4" />
              </div>
              <h4 className="font-semibold text-slate-900 text-sm">Tenant Context Abstraction</h4>
              <p className="text-xs text-stone-600 leading-relaxed">
                Backed by ThreadLocal context (`TenantContext`) and MDC logging. Resolves validated tenant UUID and prevents cross-tenant leakage.
              </p>
            </div>

            <div className="space-y-2 p-4 rounded-xl bg-stone-50 border border-stone-200">
              <div className="w-8 h-8 rounded-lg bg-slate-200 text-slate-800 flex items-center justify-center font-bold text-xs">
                <Database className="w-4 h-4" />
              </div>
              <h4 className="font-semibold text-slate-900 text-sm">Shared Database with Discriminator</h4>
              <p className="text-xs text-stone-600 leading-relaxed">
                PostgreSQL schema partitioning with indexed `tenant_id` columns and Flyway automated repeatable migrations.
              </p>
            </div>

            <div className="space-y-2 p-4 rounded-xl bg-stone-50 border border-stone-200">
              <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-800 flex items-center justify-center font-bold text-xs">
                <Server className="w-4 h-4" />
              </div>
              <h4 className="font-semibold text-slate-900 text-sm">Modular Monolith Boundary</h4>
              <p className="text-xs text-stone-600 leading-relaxed">
                Zero premature microservice complexity. Explicit controller-service-repository layered structure with strict DTO boundaries.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};