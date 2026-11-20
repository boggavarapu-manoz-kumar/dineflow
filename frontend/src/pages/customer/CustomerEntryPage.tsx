import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { UtensilsCrossed, User, Phone, ArrowRight, AlertCircle, Sparkles, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { enterCustomerSession } from '@/lib/authClient';

export const CustomerEntryPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const restaurantName = searchParams.get('restaurant') || 'The Grand Heritage';
  const tableNumber = searchParams.get('table') || '04';
  const entryToken = searchParams.get('token') || 'demo-qr-token';

  const [name, setName] = useState('');
  const [mobileNumber, setMobileNumber] = useState('');
  const [countryCode, setCountryCode] = useState('+91');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [sessionCreated, setSessionCreated] = useState(false);

  const handleContinue = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!name.trim()) {
      setErrorMsg('Please enter your name to proceed.');
      return;
    }

    if (!mobileNumber.trim() || mobileNumber.length < 8) {
      setErrorMsg('Please enter a valid mobile number for order status updates.');
      return;
    }

    setLoading(true);
    try {
      await enterCustomerSession(name.trim(), countryCode + mobileNumber.trim(), entryToken);
      setSessionCreated(true);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Invalid or expired QR entry token. Please scan table QR again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#FDFBF7] flex flex-col justify-center px-4 py-8 text-slate-900">
      <div className="max-w-md w-full mx-auto space-y-6">
        {/* Restaurant Header */}
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-slate-900 flex items-center justify-center text-emerald-400 mx-auto shadow-md">
            <UtensilsCrossed className="w-6 h-6 stroke-[2.2]" />
          </div>
          <div>
            <span className="text-[10px] font-bold tracking-widest text-emerald-800 uppercase bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full">
              Table #{tableNumber}
            </span>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 mt-2">
              Welcome to {restaurantName}
            </h1>
            <p className="text-xs text-stone-500 font-medium">Enjoy a seamless dining experience.</p>
          </div>
        </div>

        {/* Card */}
        <div className="bg-white rounded-3xl p-6 sm:p-8 shadow-xl shadow-stone-200/50 border border-stone-200/80">
          {sessionCreated ? (
            <div className="text-center py-6 space-y-4">
              <div className="w-12 h-12 bg-emerald-100 text-emerald-700 rounded-full flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-7 h-7" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-slate-900">Session Verified!</h3>
                <p className="text-xs text-stone-500 mt-1">
                  Ready to browse the digital menu for Table #{tableNumber}.
                </p>
              </div>
              <div className="p-3 bg-stone-50 rounded-xl border border-stone-200 text-xs text-stone-600 text-left">
                <span className="font-semibold text-slate-900">Next Step:</span> In Sprint 3, you will automatically transition to the interactive digital menu with category filtering and real-time cart.
              </div>
            </div>
          ) : (
            <form onSubmit={handleContinue} className="space-y-4">
              {errorMsg && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                  <span>{errorMsg}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Your Name
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-stone-400 absolute left-3.5 top-3" />
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g. Rahul Sharma"
                    className="w-full bg-stone-50/80 border border-stone-200 rounded-xl pl-10 pr-3 py-2.5 text-sm text-slate-900 placeholder-stone-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Mobile Number
                </label>
                <div className="flex gap-2">
                  <select
                    value={countryCode}
                    onChange={(e) => setCountryCode(e.target.value)}
                    className="bg-stone-50/80 border border-stone-200 rounded-xl px-2.5 py-2.5 text-xs text-slate-900 font-medium focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="+91">+91 (IN)</option>
                    <option value="+1">+1 (US)</option>
                    <option value="+44">+44 (UK)</option>
                    <option value="+971">+971 (AE)</option>
                  </select>
                  <div className="relative flex-1">
                    <Phone className="w-4 h-4 text-stone-400 absolute left-3.5 top-3" />
                    <input
                      type="tel"
                      value={mobileNumber}
                      onChange={(e) => setMobileNumber(e.target.value)}
                      placeholder="98765 43210"
                      className="w-full bg-stone-50/80 border border-stone-200 rounded-xl pl-10 pr-3 py-2.5 text-sm text-slate-900 placeholder-stone-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white"
                    />
                  </div>
                </div>
                <p className="text-[11px] text-stone-400 mt-1">
                  Used solely for real-time kitchen preparation SMS & bill routing.
                </p>
              </div>

              <div className="pt-3">
                <Button
                  type="submit"
                  variant="emerald"
                  size="lg"
                  disabled={loading}
                  className="w-full justify-center shadow-lg shadow-emerald-900/10"
                >
                  {loading ? 'Verifying table...' : 'Continue to Menu'}
                  <ArrowRight className="w-4 h-4 ml-2" />
                </Button>
              </div>

              <div className="pt-2 flex items-center justify-center gap-1.5 text-[11px] text-stone-500">
                <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
                <span>Zero login friction · No password · Instant ordering</span>
              </div>
            </form>
          )}
        </div>

        {/* Footer tagline */}
        <div className="text-center text-[11px] text-stone-400">
          Powered by <span className="font-semibold text-slate-700">DineFlow</span> · Scan. Order. Enjoy.
        </div>
      </div>
    </div>
  );
};