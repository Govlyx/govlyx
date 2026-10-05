/**
 * Zero-Knowledge Client Vault Engine for Govlyx.
 * - Manages the client-side blindSalt and derivation of actor_token.
 * - Privacy PIN / Passphrase is NEVER transmitted across the network.
 * - Hardware-accelerated Web Crypto API (AES-GCM, PBKDF2, HMAC-SHA256).
 * - Multi-Tab synchronization and sessionStorage caching for fast F5 page reloads.
 */

const DB_NAME = 'govlyx_privacy_vault';
const DB_VERSION = 1;
const STORE_NAME = 'vault_store';
const BLIND_SALT_KEY = 'client_blind_salt';
const SESSION_ACTOR_TOKEN_KEY = 'govlyx_actor_token';

// In-memory cache for ultra-fast request interceptors (cleared on page reload if not in sessionStorage)
let inMemoryActorToken: string | null = null;
let inMemoryBlindSalt: string | null = null;

// Initialize in-memory token from sessionStorage or localStorage if present
if (typeof window !== 'undefined') {
  try {
    const cached =
      sessionStorage.getItem(SESSION_ACTOR_TOKEN_KEY) ||
      localStorage.getItem(SESSION_ACTOR_TOKEN_KEY);
    if (cached) {
      inMemoryActorToken = cached;
    }
  } catch {
    /* ignore restricted storage */
  }
}

// IndexedDB Helper
function openVaultDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB is not supported in this environment'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export const vaultService = {
  /**
   * Authority Role Check:
   * Authorities (ROLE_DEPARTMENT, ROLE_ADMIN) DO NOT use an actor_token.
   */
  isAuthorityRole(role: string | null | undefined): boolean {
    return role === 'ROLE_DEPARTMENT' || role === 'ROLE_ADMIN';
  },

  /** Check if blindSalt is already cached locally in IndexedDB */
  async hasLocalBlindSalt(): Promise<boolean> {
    if (inMemoryBlindSalt) return true;
    try {
      const db = await openVaultDB();
      return new Promise((resolve) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const req = store.get(BLIND_SALT_KEY);
        req.onsuccess = () => {
          if (req.result) {
            inMemoryBlindSalt = req.result;
            resolve(true);
          } else {
            resolve(false);
          }
        };
        req.onerror = () => resolve(false);
      });
    } catch {
      return false;
    }
  },

  /** Retrieve the cached blindSalt from IndexedDB */
  async getStoredBlindSalt(): Promise<string | null> {
    if (inMemoryBlindSalt) return inMemoryBlindSalt;
    const exists = await this.hasLocalBlindSalt();
    return exists ? inMemoryBlindSalt : null;
  },

  /** Store blindSalt securely in browser IndexedDB */
  async saveBlindSalt(blindSalt: string): Promise<void> {
    inMemoryBlindSalt = blindSalt;
    const db = await openVaultDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.put(blindSalt, BLIND_SALT_KEY);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  },

  /** Clear vault on logout or reset (cleans IndexedDB, storage, and RAM) */
  async clearVault(): Promise<void> {
    inMemoryBlindSalt = null;
    inMemoryActorToken = null;
    try {
      sessionStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
      localStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
    } catch {
      /* ignore */
    }
    try {
      const db = await openVaultDB();
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).clear();
    } catch {
      /* ignore */
    }
  },

  /** Clear session vault only (e.g. cross-tab sync logout or soft logout) */
  clearSessionVault(): void {
    inMemoryActorToken = null;
    try {
      sessionStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
      localStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
    } catch {
      /* ignore */
    }
  },

  /**
   * DERIVATION: Derive AES-256 Key from PIN/passphrase via PBKDF2
   */
  async deriveKeyFromPin(pin: string, saltHex: string): Promise<CryptoKey> {
    const enc = new TextEncoder();
    const pinKey = await window.crypto.subtle.importKey(
      'raw',
      enc.encode(pin),
      { name: 'PBKDF2' },
      false,
      ['deriveKey'],
    );

    // Convert hex salt to Uint8Array
    const saltBytes = new Uint8Array(
      saltHex.match(/.{1,2}/g)!.map((byte) => parseInt(byte, 16)),
    );

    return window.crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: saltBytes,
        iterations: 100000,
        hash: 'SHA-256',
      },
      pinKey,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt'],
    );
  },

  /**
   * SETUP FLOW: Generate 256-bit blindSalt, encrypt with PIN, return payload for backend
   * Accepts optional customBlindSalt (used when backend passes seed_blind_salt for existing accounts)
   */
  async setupNewVault(
    pin: string,
    customBlindSalt?: string,
  ): Promise<{
    blindSalt: string;
    vaultBlob: string;
    vaultSalt: string;
  }> {
    let blindSalt = customBlindSalt;
    if (!blindSalt) {
      // 1. Generate 32 bytes (256-bit) cryptographically secure random blindSalt
      const blindSaltBytes = new Uint8Array(32);
      window.crypto.getRandomValues(blindSaltBytes);
      blindSalt = Array.from(blindSaltBytes)
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('');
    }

    // 2. Generate random 16 bytes salt for PBKDF2
    const vaultSaltBytes = new Uint8Array(16);
    window.crypto.getRandomValues(vaultSaltBytes);
    const vaultSalt = Array.from(vaultSaltBytes)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');

    // 3. Derive key from PIN/passphrase
    const aesKey = await this.deriveKeyFromPin(pin, vaultSalt);

    // 4. Encrypt blindSalt using AES-GCM (12-byte random IV)
    const iv = new Uint8Array(12);
    window.crypto.getRandomValues(iv);

    const ciphertextBuffer = await window.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      new TextEncoder().encode(blindSalt),
    );

    // 5. Pack into serializable Base64 payload
    const vaultBlobObj = {
      iv: btoa(String.fromCharCode(...iv)),
      ciphertext: btoa(
        String.fromCharCode(...new Uint8Array(ciphertextBuffer)),
      ),
    };
    const vaultBlob = JSON.stringify(vaultBlobObj);

    // 6. Save blindSalt to local IndexedDB
    await this.saveBlindSalt(blindSalt);

    return { blindSalt, vaultBlob, vaultSalt };
  },

  /**
   * UNLOCK FLOW: Decrypt vaultBlob using entered PIN on new device / fresh browser
   */
  async unlockVaultWithPin(
    pin: string,
    vaultBlobStr: string,
    vaultSalt: string,
  ): Promise<string> {
    try {
      const parsedBlob = JSON.parse(vaultBlobStr);
      const iv = new Uint8Array(
        atob(parsedBlob.iv)
          .split('')
          .map((c) => c.charCodeAt(0)),
      );
      const ciphertext = new Uint8Array(
        atob(parsedBlob.ciphertext)
          .split('')
          .map((c) => c.charCodeAt(0)),
      );

      const aesKey = await this.deriveKeyFromPin(pin, vaultSalt);

      const decryptedBuffer = await window.crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        aesKey,
        ciphertext,
      );

      const blindSalt = new TextDecoder().decode(decryptedBuffer);

      // Successfully decrypted -> cache in IndexedDB
      await this.saveBlindSalt(blindSalt);
      return blindSalt;
    } catch {
      throw new Error('INVALID_PIN');
    }
  },

  /**
   * DERIVE FINAL ACTOR TOKEN:
   * actor_token = "act_" + HMAC-SHA256(serverActorToken, blindSalt)
   */
  async deriveActorToken(
    serverActorToken: string,
    blindSalt: string,
  ): Promise<string> {
    const enc = new TextEncoder();
    const key = await window.crypto.subtle.importKey(
      'raw',
      enc.encode(blindSalt),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign'],
    );

    const signature = await window.crypto.subtle.sign(
      'HMAC',
      key,
      enc.encode(serverActorToken),
    );

    const hashArray = Array.from(new Uint8Array(signature));
    const hashHex = hashArray
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');

    const actorToken = `act_${hashHex}`;
    inMemoryActorToken = actorToken;

    // Cache in sessionStorage and localStorage for fast resume across page refreshes and multi-tab sync
    try {
      sessionStorage.setItem(SESSION_ACTOR_TOKEN_KEY, actorToken);
      localStorage.setItem(SESSION_ACTOR_TOKEN_KEY, actorToken);
    } catch {
      /* ignore */
    }

    return actorToken;
  },

  /** Get cached actor token synchronously for axios interceptor */
  getCachedActorToken(): string | null {
    if (inMemoryActorToken) return inMemoryActorToken;
    try {
      const fromSession = sessionStorage.getItem(SESSION_ACTOR_TOKEN_KEY);
      if (fromSession) {
        inMemoryActorToken = fromSession;
        return fromSession;
      }
      const fromLocal = localStorage.getItem(SESSION_ACTOR_TOKEN_KEY);
      if (fromLocal) {
        inMemoryActorToken = fromLocal;
        return fromLocal;
      }
    } catch {
      /* ignore */
    }
    return null;
  },

  setCachedActorToken(token: string | null): void {
    inMemoryActorToken = token;
    try {
      if (token) {
        sessionStorage.setItem(SESSION_ACTOR_TOKEN_KEY, token);
        localStorage.setItem(SESSION_ACTOR_TOKEN_KEY, token);
      } else {
        sessionStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
        localStorage.removeItem(SESSION_ACTOR_TOKEN_KEY);
      }
    } catch {
      /* ignore */
    }
  },
};

export const getSessionActorToken = () => vaultService.getCachedActorToken();
