import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Eye, EyeOff, Lock, Mail, ArrowRight, AlertCircle } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/Button';

export const SuperAdminLoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!email || !password) {
      setErrorMsg('Please provide both administrator email and password.');
      return;
    }

    setLoading(true);
    try {
      await login(email, password); // null tenantId for Super Admin
      navigate('/super-admin/dashboard');
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Invalid administrator credentials');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8 text-stone-200">
      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-emerald-400 mx-auto shadow-xl">
          <ShieldCheck className="w-7 h-7" />
        </div>
        <h2 className="mt-4 text-2xl font-bold tracking-tight text-white">
          DineFlow Platform Administration
        </h2>
        <p className="mt-1 text-xs text-stone-400">
          Dedicated access for SaaS platform engineers and super administrators.
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-slate-900 border border-slate-800 py-8 px-6 shadow-2xl rounded-2xl sm:px-10">
          {errorMsg && (
            <div className="mb-6 p-3.5 rounded-xl bg-red-950/60 border border-red-800/80 text-red-200 text-xs flex items-center gap-2.5">
              <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-stone-300 mb-1.5">
                Admin Email Address
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-stone-500 absolute left-3.5 top-3" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="superadmin@dineflow.io"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-3 py-2.5 text-sm text-white placeholder-stone-600 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-semibold uppercase tracking-wider text-stone-300">
                  Password
                </label>
                <a href="#forgot" className="text-xs text-emerald-400 hover:underline">
                  Forgot password?
                </a>
              </div>
              <div className="relative">
                <Lock className="w-4 h-4 text-stone-500 absolute left-3.5 top-3" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-10 py-2.5 text-sm text-white placeholder-stone-600 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-3 text-stone-500 hover:text-stone-300"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center">
              <input
                id="remember"
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                className="h-4 w-4 rounded bg-slate-950 border-slate-800 text-emerald-600 focus:ring-emerald-500"
              />
              <label htmlFor="remember" className="ml-2 text-xs text-stone-400">
                Remember admin identifier on this secure station
              </label>
            </div>

            <Button
              type="submit"
              variant="emerald"
              size="lg"
              disabled={loading}
              className="w-full justify-center"
            >
              {loading ? 'Authenticating...' : 'Sign In as Super Admin'}
              <ArrowRight className="w-4 h-4 ml-2" />
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
};