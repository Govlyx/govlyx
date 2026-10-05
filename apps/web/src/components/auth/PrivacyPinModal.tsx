import React, { useState } from 'react';
import {
  Shield,
  Key,
  Eye,
  EyeOff,
  Check,
  Copy,
  AlertTriangle,
  Lock,
  RefreshCw,
  Smartphone,
  Dices,
  Sparkles,
  ArrowLeft,
} from 'lucide-react';
import { vaultService } from '../../services/vaultService';
import {
  saveVaultBlob,
  setupVault,
  resetVault,
  fetchGeneratedPseudonym,
} from '../../api/authService';
import { showToast } from '../../utils/toast';

export interface PrivacyPinModalProps {
  isOpen: boolean;
  mode: 'SETUP' | 'UNLOCK';
  vaultBlob?: string | null;
  vaultSalt?: string | null;
  seedBlindSalt?: string | null;
  serverActorToken: string;
  onSuccess: (actorToken: string) => void;
  onCancel?: () => void;
}

const ADJECTIVES = [
  'Brave',
  'Swift',
  'Clever',
  'Mighty',
  'Silent',
  'Wise',
  'Lucky',
  'Bold',
  'Fierce',
  'Calm',
  'Wild',
  'Bright',
  'Cool',
  'Fast',
  'Gentle',
  'Sharp',
  'Loyal',
  'Kind',
  'Strong',
  'Fearless',
  'Noble',
  'Radiant',
  'Smart',
  'Eager',
  'Joyful',
  'Daring',
  'Heroic',
  'Vibrant',
  'Nimble',
  'Cosmic',
  'Golden',
  'Zen',
  'Stellar',
  'Electric',
  'Quantum',
  'Solar',
  'Lunar',
  'Emerald',
  'Sapphire',
  'Swanky',
];

const NOUNS = [
  'Tiger',
  'Eagle',
  'Shark',
  'Panther',
  'Wolf',
  'Falcon',
  'Lion',
  'Bear',
  'Hawk',
  'Cheetah',
  'Puma',
  'Dragon',
  'Phoenix',
  'Leopard',
  'Fox',
  'Jaguar',
  'Lynx',
  'Raven',
  'Owl',
  'Stallion',
  'Mustang',
  'Buffalo',
  'Elephant',
  'Dolphin',
  'Penguin',
  'Otter',
  'Starfish',
  'Mantis',
  'Peacock',
  'Swan',
  'Robin',
  'Falcon',
];

function generateClientPseudonym(): string {
  const adj = ADJECTIVES[Math.floor(Math.random() * ADJECTIVES.length)];
  const noun = NOUNS[Math.floor(Math.random() * NOUNS.length)];
  const num = 1000 + Math.floor(Math.random() * 9000);
  return `${adj}${noun}${num}`;
}

export const PrivacyPinModal: React.FC<PrivacyPinModalProps> = ({
  isOpen,
  mode,
  vaultBlob,
  vaultSalt,
  seedBlindSalt,
  serverActorToken,
  onSuccess,
  onCancel,
}) => {
  // Mode override for Reset / Create New Identity flow
  const [isResetFlow, setIsResetFlow] = useState(false);

  // Input states
  const [passphrase, setPassphrase] = useState('');
  const [confirmPassphrase, setConfirmPassphrase] = useState('');
  const [showPassphrase, setShowPassphrase] = useState(false);
  const [showConfirmPassphrase, setShowConfirmPassphrase] = useState(false);

  // Pseudonym state for Setup & Reset
  const [customUsername, setCustomUsername] = useState(() =>
    generateClientPseudonym(),
  );

  // Setup / Reset Step: 1 (Input) | 2 (Mandatory Backup & Screenshot Confirmation)
  const [step, setStep] = useState<1 | 2>(1);
  const [hasBackedUp, setHasBackedUp] = useState(false);
  const [copied, setCopied] = useState(false);

  // Loading & error states
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(passphrase);
    setCopied(true);
    showToast.success('Passphrase copied to clipboard!');
    setTimeout(() => setCopied(false), 2500);
  };

  const handleRandomizeUsername = async () => {
    // 1. Instant optimistic update
    setCustomUsername(generateClientPseudonym());
    // 2. Fetch guaranteed unique server pseudonym
    try {
      const serverPseudo = await fetchGeneratedPseudonym();
      if (serverPseudo) {
        setCustomUsername(serverPseudo);
      }
    } catch {
      // Fallback already in place
    }
  };

  // ── Mode: UNLOCK ───────────────────────────────────────────────────────────
  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!passphrase) {
      setError('Please enter your Privacy Key or PIN.');
      return;
    }

    if (!vaultBlob || !vaultSalt) {
      setError(
        'No vault found on this account. Please create a new Privacy Key.',
      );
      return;
    }

    setLoading(true);
    try {
      // Decrypt vaultBlob locally in browser using Web Crypto API
      const blindSalt = await vaultService.unlockVaultWithPin(
        passphrase,
        vaultBlob,
        vaultSalt,
      );

      // Derive actor_token from decrypted blindSalt
      const actorToken = await vaultService.deriveActorToken(
        serverActorToken,
        blindSalt,
      );

      showToast.success('Privacy Shield unlocked!');
      onSuccess(actorToken);
    } catch {
      setError(
        'Incorrect Privacy Key. If you forgot your key, you can reset and create a new anonymous identity below.',
      );
    } finally {
      setLoading(false);
    }
  };

  // ── Mode: SETUP or RESET Flow (Step 1 -> Step 2) ───────────────────────────
  const handleProceedToBackup = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (passphrase.length < 4) {
      setError(
        'Privacy Key must be at least 4 characters (e.g. 4-6 digit PIN or secret passphrase)',
      );
      return;
    }

    if (passphrase !== confirmPassphrase) {
      setError('Passphrases do not match. Please re-type carefully.');
      return;
    }

    const cleanName = customUsername.trim().replace(/^@+/, '');
    if (cleanName.length < 3) {
      setError('Username must be at least 3 characters long.');
      return;
    }

    // Advance to Step 2: Screenshot & Backup Confirmation Gate
    setStep(2);
  };

  const handleCompleteSetupOrReset = async () => {
    if (!hasBackedUp) {
      setError(
        'Please confirm that you have saved or screenshotted your Privacy Key.',
      );
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const cleanUsername = customUsername.trim().replace(/^@+/, '');

      // 1. Generate new 256-bit blindSalt, encrypt with Web Crypto API AES-GCM
      // If backend provided a seedBlindSalt (and not in reset flow), use it
      const seedToUse = !isResetFlow ? seedBlindSalt || undefined : undefined;
      const {
        blindSalt,
        vaultBlob: newVaultBlob,
        vaultSalt: newVaultSalt,
      } = await vaultService.setupNewVault(passphrase, seedToUse);

      // 2. Derive deterministic zero-knowledge actor_token
      const actorToken = await vaultService.deriveActorToken(
        serverActorToken,
        blindSalt,
      );

      // 3. Save encrypted vault & register new civic persona with requested username
      if (isResetFlow) {
        await resetVault({
          clientSalt: blindSalt,
          vaultBlob: newVaultBlob,
          vaultSalt: newVaultSalt,
          actorToken,
          username: cleanUsername,
        });
        showToast.success('New anonymous identity & pseudonym activated!');
      } else {
        try {
          await setupVault({
            clientSalt: blindSalt,
            vaultBlob: newVaultBlob,
            vaultSalt: newVaultSalt,
            actorToken,
            username: cleanUsername,
          });
        } catch {
          await saveVaultBlob({
            vaultBlob: newVaultBlob,
            vaultSalt: newVaultSalt,
            actorToken,
            clientSalt: blindSalt,
            username: cleanUsername,
          });
        }
        showToast.success('Privacy Shield activated successfully!');
      }

      onSuccess(actorToken);
    } catch (err: any) {
      setError(err?.message || 'Failed to initialize cryptographic vault.');
    } finally {
      setLoading(false);
    }
  };

  const isSetupOrReset = mode === 'SETUP' || isResetFlow;

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/75 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="relative w-full max-w-md rounded-3xl bg-base-100 border border-black/10 dark:border-white/10 shadow-2xl overflow-hidden p-6 sm:p-8">
        {/* Glow Header Accent */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-blue-600 via-indigo-500 to-emerald-500" />

        {/* Modal Top Icon & Title */}
        <div className="flex items-center gap-3.5 mb-5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-500/10 text-blue-600 dark:bg-blue-500/20 dark:text-blue-400">
            {isSetupOrReset ? (
              <Shield className="h-6 w-6" />
            ) : (
              <Lock className="h-6 w-6" />
            )}
          </div>
          <div>
            <h3 className="text-lg font-black tracking-tight text-base-content">
              {isResetFlow
                ? 'Reset Privacy Identity'
                : mode === 'SETUP'
                  ? 'Activate Zero-Knowledge Shield'
                  : 'Unlock Privacy Vault'}
            </h3>
            <p className="text-xs text-base-content/80 dark:text-zinc-300 font-medium">
              {isResetFlow
                ? 'Create a fresh secret key & new anonymous username.'
                : mode === 'SETUP'
                  ? 'Your posts & civic actions will be mathematically anonymous.'
                  : 'Enter your Secret Key or PIN to decrypt your civic persona.'}
            </p>
          </div>
        </div>

        {error && (
          <div className="mb-5 flex items-start gap-2.5 rounded-2xl bg-red-500/10 border border-red-500/20 p-3.5 text-xs text-red-500 font-medium">
            <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* ── MODE: UNLOCK (When not resetting) ── */}
        {!isResetFlow && mode === 'UNLOCK' && (
          <form onSubmit={handleUnlock} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-base-content/90 dark:text-zinc-200 mb-1.5">
                Enter Privacy Key or Pin
              </label>
              <div className="relative">
                <input
                  type={showPassphrase ? 'text' : 'password'}
                  value={passphrase}
                  onChange={(e) => setPassphrase(e.target.value)}
                  placeholder="Enter your Secret Key or PIN"
                  className="input input-bordered w-full rounded-2xl pr-11 font-mono text-sm tracking-wider text-base-content dark:text-zinc-100 placeholder:text-base-content/40 dark:placeholder:text-zinc-400"
                  autoFocus
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassphrase(!showPassphrase)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/60 dark:text-zinc-400 hover:text-base-content dark:hover:text-white"
                >
                  {showPassphrase ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            <div className="pt-2 flex items-center justify-between gap-3">
              <span className="text-[11px] text-base-content/75 dark:text-zinc-300">
                Encrypted locally via Web Crypto
              </span>
              <button
                type="submit"
                disabled={loading || !passphrase}
                className="btn btn-primary rounded-2xl px-6 text-xs font-bold gap-2"
              >
                {loading && <RefreshCw className="h-3.5 w-3.5 animate-spin" />}
                Unlock Vault
              </button>
            </div>

            {/* Forgot Key / Reset Identity Action */}
            <div className="pt-3 mt-3 border-t border-black/5 dark:border-white/5 text-center">
              <button
                type="button"
                onClick={() => {
                  setError(null);
                  setPassphrase('');
                  setConfirmPassphrase('');
                  setShowPassphrase(false);
                  setShowConfirmPassphrase(false);
                  setCustomUsername(generateClientPseudonym());
                  setStep(1);
                  setIsResetFlow(true);
                }}
                className="text-xs font-bold text-[#1D4ED8] dark:text-blue-400 hover:underline inline-flex items-center gap-1.5 cursor-pointer py-1"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                Don't know your Secret Key? Reset & Create New Identity
              </button>
            </div>
          </form>
        )}

        {/* ── MODE: SETUP or RESET FLOW ── */}
        {isSetupOrReset && (
          <>
            {step === 1 ? (
              <form onSubmit={handleProceedToBackup} className="space-y-4">
                {isResetFlow ? (
                  <div className="rounded-2xl bg-amber-500/10 border border-amber-500/20 p-3.5 text-xs text-amber-600 dark:text-amber-400 leading-relaxed space-y-1">
                    <div className="font-bold flex items-center gap-1.5">
                      <Sparkles className="h-3.5 w-3.5" /> Fresh Anonymous
                      Identity
                    </div>
                    <p>
                      Because Govlyx never stores your key, lost keys cannot be
                      decrypted. Resetting creates a{' '}
                      <strong>New Secret Key</strong> and{' '}
                      <strong>New Anonymous Username</strong>. Your past posts
                      remain safe & decoupled.
                    </p>
                  </div>
                ) : (
                  <div className="rounded-2xl bg-blue-500/5 dark:bg-blue-500/10 border border-blue-500/15 p-3.5 text-xs text-base-content/90 dark:text-zinc-200 leading-relaxed space-y-1">
                    <div className="font-bold flex items-center gap-1.5 text-blue-600 dark:text-blue-400">
                      <Key className="h-3.5 w-3.5" /> Choose Any Secret Key
                    </div>
                    <p className="text-base-content/80 dark:text-zinc-300">
                      Enter a 4-6 digit numeric PIN (e.g.{' '}
                      <span className="font-mono font-bold text-base-content dark:text-white">
                        4821
                      </span>
                      ) or a memorable passphrase (e.g.{' '}
                      <span className="font-mono font-bold text-base-content dark:text-white">
                        Monsoon#Rain78
                      </span>
                      ).
                    </p>
                  </div>
                )}

                {/* Secret Key Input */}
                <div>
                  <label className="block text-xs font-semibold text-base-content/90 dark:text-zinc-100 mb-1.5">
                    {isResetFlow
                      ? 'Create New Secret Key / Pin'
                      : 'Create Secret Key / Pin'}
                  </label>
                  <div className="relative">
                    <input
                      type={showPassphrase ? 'text' : 'password'}
                      value={passphrase}
                      onChange={(e) => setPassphrase(e.target.value)}
                      placeholder="e.g. 4821 or secret-phrase"
                      className="input input-bordered w-full rounded-2xl pr-11 font-mono text-sm tracking-wider text-base-content dark:text-zinc-100 placeholder:text-base-content/40 dark:placeholder:text-zinc-400"
                      autoFocus
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassphrase(!showPassphrase)}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/60 dark:text-zinc-400 hover:text-base-content dark:hover:text-white"
                    >
                      {showPassphrase ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Confirm Key Input */}
                <div>
                  <label className="block text-xs font-semibold text-base-content/90 dark:text-zinc-100 mb-1.5">
                    Confirm Secret Key
                  </label>
                  <div className="relative">
                    <input
                      type={showConfirmPassphrase ? 'text' : 'password'}
                      value={confirmPassphrase}
                      onChange={(e) => setConfirmPassphrase(e.target.value)}
                      placeholder="Re-enter your secret key"
                      className="input input-bordered w-full rounded-2xl pr-11 font-mono text-sm tracking-wider text-base-content dark:text-zinc-100 placeholder:text-base-content/40 dark:placeholder:text-zinc-400"
                      required
                    />
                    <button
                      type="button"
                      onClick={() =>
                        setShowConfirmPassphrase(!showConfirmPassphrase)
                      }
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-base-content/60 dark:text-zinc-400 hover:text-base-content dark:hover:text-white"
                    >
                      {showConfirmPassphrase ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Anonymous Username / Pseudonym Customization */}
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-semibold text-base-content/90 dark:text-zinc-100">
                      Anonymous Username
                    </label>
                    <button
                      type="button"
                      onClick={handleRandomizeUsername}
                      className="text-[11px] font-bold text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1 cursor-pointer"
                    >
                      <Dices className="h-3 w-3" /> Randomize
                    </button>
                  </div>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-base-content/60 dark:text-zinc-400 font-mono font-bold text-sm">
                      @
                    </span>
                    <input
                      type="text"
                      value={customUsername.replace(/^@+/, '')}
                      onChange={(e) =>
                        setCustomUsername(
                          e.target.value.replace(/[^a-zA-Z0-9_]/g, ''),
                        )
                      }
                      placeholder="e.g. BraveTiger4821"
                      className="input input-bordered w-full pl-8 pr-11 rounded-2xl font-mono text-sm font-semibold tracking-wide text-base-content dark:text-zinc-100 placeholder:text-base-content/40 dark:placeholder:text-zinc-400"
                      required
                    />
                  </div>
                  <span className="text-[11px] text-base-content/75 dark:text-zinc-300 mt-1 block">
                    This is your public display name on neighborhood posts and
                    polls.
                  </span>
                </div>

                <div className="pt-2 flex items-center justify-between gap-3">
                  {isResetFlow ? (
                    <button
                      type="button"
                      onClick={() => {
                        setIsResetFlow(false);
                        setError(null);
                        setPassphrase('');
                        setConfirmPassphrase('');
                        setShowPassphrase(false);
                        setShowConfirmPassphrase(false);
                      }}
                      className="btn btn-ghost rounded-2xl text-xs gap-1 text-base-content/80 dark:text-zinc-300 hover:text-base-content dark:hover:text-white"
                    >
                      <ArrowLeft className="h-3.5 w-3.5" /> Back to Unlock
                    </button>
                  ) : onCancel ? (
                    <button
                      type="button"
                      onClick={onCancel}
                      className="btn btn-ghost rounded-2xl text-xs text-base-content/80 dark:text-zinc-300 hover:text-base-content dark:hover:text-white"
                    >
                      Cancel
                    </button>
                  ) : (
                    <div />
                  )}
                  <button
                    type="submit"
                    className="btn btn-primary rounded-2xl px-6 text-xs font-bold"
                  >
                    Next: Backup & Confirm
                  </button>
                </div>
              </form>
            ) : (
              /* Step 2: Screenshot & Backup Warning Confirmation Gate */
              <div className="space-y-4 animate-in fade-in slide-in-from-right-2 duration-200">
                <div className="rounded-2xl bg-amber-500/10 border border-amber-500/25 p-4 text-xs text-amber-600 dark:text-amber-400 space-y-2">
                  <div className="font-black flex items-center gap-1.5 text-sm">
                    <Smartphone className="h-4 w-4" /> Screenshot & Save Your
                    Key!
                  </div>
                  <p className="leading-relaxed text-base-content/90 dark:text-zinc-200">
                    Under Govlyx Zero-Knowledge architecture, the server{' '}
                    <strong>never</strong> has your key. If you forget it or
                    switch browsers without it, it <strong>cannot</strong> be
                    recovered by anyone.
                  </p>
                </div>

                {/* Key & Username Display Card */}
                <div className="rounded-2xl bg-base-200/70 border border-black/5 dark:border-white/5 p-3.5 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-[11px] font-semibold text-base-content/80 dark:text-zinc-300 block">
                        Your Anonymous Handle
                      </span>
                      <span className="font-mono text-sm font-black text-blue-600 dark:text-blue-400">
                        @{customUsername.replace(/^@+/, '')}
                      </span>
                    </div>
                  </div>

                  <div className="pt-2 border-t border-black/5 dark:border-white/5 flex items-center justify-between">
                    <div className="min-w-0 pr-2">
                      <span className="text-[11px] font-semibold text-base-content/80 dark:text-zinc-300 block">
                        Your Secret Key
                      </span>
                      <span className="font-mono text-base font-black text-base-content dark:text-white tracking-wider break-all">
                        {passphrase}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={handleCopy}
                      className="btn btn-sm btn-ghost rounded-xl gap-1.5 shrink-0 text-xs font-bold"
                    >
                      {copied ? (
                        <Check className="h-3.5 w-3.5 text-emerald-500" />
                      ) : (
                        <Copy className="h-3.5 w-3.5" />
                      )}
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                </div>

                {/* Confirmation Checkbox */}
                <label className="flex items-start gap-3 cursor-pointer select-none rounded-2xl bg-base-200/40 p-3 hover:bg-base-200/70 transition-colors">
                  <input
                    type="checkbox"
                    checked={hasBackedUp}
                    onChange={(e) => setHasBackedUp(e.target.checked)}
                    className="checkbox checkbox-primary rounded-lg checkbox-sm mt-0.5"
                  />
                  <span className="text-xs font-medium text-base-content/90 dark:text-zinc-200 leading-tight">
                    I have saved or taken a screenshot of my Secret Key. I
                    understand it cannot be recovered if lost.
                  </span>
                </label>

                <div className="pt-2 flex items-center justify-between gap-3">
                  <button
                    type="button"
                    onClick={() => setStep(1)}
                    disabled={loading}
                    className="btn btn-ghost rounded-2xl text-xs"
                  >
                    Back
                  </button>
                  <button
                    type="button"
                    onClick={handleCompleteSetupOrReset}
                    disabled={!hasBackedUp || loading}
                    className="btn btn-primary rounded-2xl px-6 text-xs font-bold gap-2"
                  >
                    {loading && (
                      <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                    )}
                    {isResetFlow ? 'Activate New Shield' : 'Activate Shield'}
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default PrivacyPinModal;
