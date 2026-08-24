import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AuthService, isSupabaseConfigured } from '../../services/supabaseService';
import { X, Sparkles, Mail, Lock, LogIn, UserPlus, AlertCircle, ShieldCheck, CheckCircle2 } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const AuthModal: React.FC = () => {
  const isAuthModalOpen = useMusicStore(state => state.isAuthModalOpen);
  const setIsAuthModalOpen = useMusicStore(state => state.setIsAuthModalOpen);
  const setUser = useMusicStore(state => state.setUser);

  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  if (!isAuthModalOpen) return null;

  const handleGoogleSignIn = async () => {
    setLoading(true);
    setErrorMsg(null);
    const res = await AuthService.signInWithGoogle();
    setLoading(false);
    if (res.error) {
      setErrorMsg(res.error);
    } else if (res.user) {
      setUser(res.user);
      setIsAuthModalOpen(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg('Please enter both email and password.');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    setSuccessMsg(null);

    if (mode === 'signin') {
      const res = await AuthService.signInWithEmail(email, password);
      setLoading(false);
      if (res.error) {
        setErrorMsg(res.error);
      } else if (res.user) {
        setUser(res.user);
        setIsAuthModalOpen(false);
      }
    } else {
      const res = await AuthService.signUpWithEmail(email, password);
      setLoading(false);
      if (res.error) {
        setErrorMsg(res.error);
      } else {
        if (res.user) setUser(res.user);
        if (res.message) setSuccessMsg(res.message);
        setTimeout(() => setIsAuthModalOpen(false), 1500);
      }
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/80 backdrop-blur-xl">
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 15 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 15 }}
          transition={{ type: 'spring', damping: 25, stiffness: 350 }}
          className="relative w-full max-w-md p-6 sm:p-8 rounded-3xl glass-panel-elevated border border-white/15 shadow-2xl overflow-hidden bg-[#0c0c12]"
        >
          {/* Background Glow */}
          <div className="absolute -top-20 -right-20 w-60 h-60 rounded-full bg-purple-500/20 filter blur-[80px] pointer-events-none" />
          <div className="absolute -bottom-20 -left-20 w-60 h-60 rounded-full bg-emerald-500/20 filter blur-[80px] pointer-events-none" />

          {/* Close Button */}
          <button
            onClick={() => setIsAuthModalOpen(false)}
            className="absolute top-5 right-5 p-2 rounded-full text-zinc-400 hover:text-white hover:bg-white/10 transition-all z-10"
          >
            <X className="w-5 h-5" />
          </button>

          {/* Header Branding */}
          <div className="text-center space-y-2 mb-6 relative z-10">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-white/10 border border-white/10 text-xs font-extrabold text-emerald-300">
              <Sparkles className="w-3.5 h-3.5 text-amber-300" />
              <span>ELARA Authentication</span>
            </div>
            <h2 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
              {mode === 'signin' ? 'Welcome Back' : 'Create Account'}
            </h2>
            <p className="text-xs text-zinc-400">
              Sign in to sync your Liked Songs, custom playlists, and listening stats.
            </p>
          </div>

          {/* Messages */}
          {errorMsg && (
            <div className="mb-4 p-3 rounded-2xl bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0 mt-0.5" />
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && (
            <div className="mb-4 p-3 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 text-xs flex items-start gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
              <span>{successMsg}</span>
            </div>
          )}

          {/* Google OAuth Button */}
          <div className="space-y-4 relative z-10">
            <button
              onClick={handleGoogleSignIn}
              disabled={loading}
              className="w-full py-3.5 px-4 rounded-2xl bg-white hover:bg-zinc-100 text-zinc-900 font-extrabold text-xs sm:text-sm shadow-xl hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-3 disabled:opacity-50"
            >
              {/* Google SVG Icon */}
              <svg className="w-5 h-5" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                />
                <path
                  fill="#34A853"
                  d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                />
                <path
                  fill="#EA4335"
                  d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                />
              </svg>
              <span>Continue with Google</span>
            </button>

            <div className="relative flex items-center justify-center my-4">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-white/10" />
              </div>
              <span className="relative px-3 text-[10px] uppercase font-mono tracking-widest bg-[#0c0c12] text-zinc-500">
                Or with Email
              </span>
            </div>

            {/* Email / Password Form */}
            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="relative">
                <Mail className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-400" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="Enter your email"
                  className="w-full pl-10 pr-4 py-3 rounded-2xl bg-white/[0.06] focus:bg-white/[0.1] border border-white/10 focus:border-emerald-400/60 text-xs sm:text-sm text-white placeholder-zinc-500 focus:outline-none transition-all"
                />
              </div>

              <div className="relative">
                <Lock className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-400" />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="Enter password"
                  className="w-full pl-10 pr-4 py-3 rounded-2xl bg-white/[0.06] focus:bg-white/[0.1] border border-white/10 focus:border-emerald-400/60 text-xs sm:text-sm text-white placeholder-zinc-500 focus:outline-none transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-emerald-400 via-cyan-400 to-indigo-500 text-black font-extrabold text-xs sm:text-sm shadow-xl hover:scale-[1.01] active:scale-[0.99] transition-all flex items-center justify-center gap-2 disabled:opacity-50 mt-2"
              >
                {mode === 'signin' ? (
                  <>
                    <LogIn className="w-4 h-4" />
                    <span>Sign In</span>
                  </>
                ) : (
                  <>
                    <UserPlus className="w-4 h-4" />
                    <span>Create Account</span>
                  </>
                )}
              </button>
            </form>

            {/* Toggle Mode */}
            <div className="pt-2 text-center text-xs text-zinc-400">
              {mode === 'signin' ? (
                <p>
                  Don't have an account?{' '}
                  <button
                    onClick={() => { setMode('signup'); setErrorMsg(null); }}
                    className="text-emerald-400 font-bold hover:underline"
                  >
                    Sign Up
                  </button>
                </p>
              ) : (
                <p>
                  Already have an account?{' '}
                  <button
                    onClick={() => { setMode('signin'); setErrorMsg(null); }}
                    className="text-emerald-400 font-bold hover:underline"
                  >
                    Sign In
                  </button>
                </p>
              )}
            </div>

            {/* Provider Status Note */}
            <div className="pt-4 border-t border-white/[0.06] flex items-center justify-between text-[10px] font-mono text-zinc-500">
              <span className="flex items-center gap-1.5">
                <ShieldCheck className={`w-3.5 h-3.5 ${isSupabaseConfigured ? 'text-emerald-400' : 'text-amber-400'}`} />
                {isSupabaseConfigured ? 'Supabase Auth Active' : 'Supabase Demo Ready'}
              </span>
              <span>OAuth 2.0 Secured</span>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
