import { createClient, SupabaseClient, User } from '@supabase/supabase-js';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || '';
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY || '';

export const isSupabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY && !SUPABASE_URL.includes('your-supabase'));

export const supabase: SupabaseClient | null = isSupabaseConfigured
  ? createClient(SUPABASE_URL, SUPABASE_ANON_KEY)
  : null;

export interface AppUser {
  id: string;
  email: string;
  name: string;
  avatarUrl?: string;
  provider: 'google' | 'email' | 'demo';
}

export class AuthService {
  /**
   * Triggers Google OAuth Sign In via Supabase.
   * If Supabase env keys are not provided, falls back to instant local Google demo profile auth.
   */
  public static async signInWithGoogle(): Promise<{ user?: AppUser; error?: string }> {
    if (isSupabaseConfigured && supabase) {
      try {
        const { error } = await supabase.auth.signInWithOAuth({
          provider: 'google',
          options: {
            redirectTo: 'http://localhost:5173'
          }
        });
        if (error) return { error: error.message };
        return {};
      } catch (err: any) {
        return { error: err.message || 'Google Auth failed' };
      }
    } else {
      // Local Demo Google Sign-In Mode
      const demoGoogleUser: AppUser = {
        id: `google-user-${Date.now()}`,
        name: 'Demo Music Lover',
        email: 'user@gmail.com',
        avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80',
        provider: 'google'
      };
      return { user: demoGoogleUser };
    }
  }

  /**
   * Signs in with Email & Password.
   */
  public static async signInWithEmail(email: string, pass: string): Promise<{ user?: AppUser; error?: string }> {
    if (!email || !pass) return { error: 'Please enter both email and password.' };

    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase.auth.signInWithPassword({
          email,
          password: pass
        });
        if (error) return { error: error.message };
        if (data.user) {
          return { user: this.formatSupabaseUser(data.user, 'email') };
        }
      } catch (err: any) {
        return { error: err.message || 'Email Sign In failed' };
      }
    }

    // Demo Mode
    const name = email.split('@')[0] || 'User';
    return {
      user: {
        id: `email-user-${Date.now()}`,
        name: name.charAt(0).toUpperCase() + name.slice(1),
        email: email,
        avatarUrl: `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(email)}`,
        provider: 'email'
      }
    };
  }

  /**
   * Signs up with Email & Password.
   */
  public static async signUpWithEmail(email: string, pass: string): Promise<{ user?: AppUser; error?: string; message?: string }> {
    if (!email || !pass) return { error: 'Please enter email and password.' };

    if (isSupabaseConfigured && supabase) {
      try {
        const { data, error } = await supabase.auth.signUp({
          email,
          password: pass,
          options: {
            data: {
              full_name: email.split('@')[0]
            }
          }
        });
        if (error) return { error: error.message };
        if (data.user) {
          return { 
            user: this.formatSupabaseUser(data.user, 'email'),
            message: 'Account created successfully!' 
          };
        }
      } catch (err: any) {
        return { error: err.message || 'Registration failed' };
      }
    }

    // Demo Mode
    const name = email.split('@')[0] || 'New User';
    return {
      user: {
        id: `email-user-${Date.now()}`,
        name: name.charAt(0).toUpperCase() + name.slice(1),
        email: email,
        avatarUrl: `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(email)}`,
        provider: 'email'
      },
      message: 'Account created! (Demo Mode)'
    };
  }

  /**
   * Signs out the current user session.
   */
  public static async signOut(): Promise<void> {
    if (isSupabaseConfigured && supabase) {
      try {
        await supabase.auth.signOut();
      } catch (err) {
        console.warn('Supabase signout note:', err);
      }
    }
  }

  /**
   * Helper to format a Supabase User object into AppUser.
   */
  public static formatSupabaseUser(user: User, provider: 'google' | 'email' = 'google'): AppUser {
    const meta = user.user_metadata || {};
    return {
      id: user.id,
      email: user.email || '',
      name: meta.full_name || meta.name || user.email?.split('@')[0] || 'User',
      avatarUrl: meta.avatar_url || meta.picture || `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(user.email || 'user')}`,
      provider
    };
  }
}
