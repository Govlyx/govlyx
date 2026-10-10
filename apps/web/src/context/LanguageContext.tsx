import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
} from "react";
import { useCurrentUser } from "../hooks/useUser";

// ─── Language map ──────────────────────────────────────────────────────────────
export const SUPPORTED_LANGUAGES = [
  { code: "en", label: "English",           nativeLabel: "English" },
  { code: "hi", label: "Hindi",             nativeLabel: "हिंदी" },
  { code: "mr", label: "Marathi",           nativeLabel: "मराठी" },
  { code: "te", label: "Telugu",            nativeLabel: "తెలుగు" },
  { code: "ta", label: "Tamil",             nativeLabel: "தமிழ்" },
  { code: "bn", label: "Bengali",           nativeLabel: "বাংলা" },
  { code: "gu", label: "Gujarati",          nativeLabel: "ગુજરાતી" },
  { code: "kn", label: "Kannada",           nativeLabel: "ಕನ್ನಡ" },
  { code: "ml", label: "Malayalam",         nativeLabel: "മലയാളം" },
  { code: "pa", label: "Punjabi",           nativeLabel: "ਪੰਜਾਬੀ" },
  { code: "ur", label: "Urdu",              nativeLabel: "اردو" },
] as const;

export type LangCode = typeof SUPPORTED_LANGUAGES[number]["code"];

const STORAGE_KEY = "govlyx_ui_language";

const STORAGE_PREFIX = "govlyx_tr:";
const translationCache = new Map<string, string>();

// Initialize cache from localStorage
try {
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key && key.startsWith(STORAGE_PREFIX)) {
      const val = localStorage.getItem(key);
      if (val) {
        translationCache.set(key.slice(STORAGE_PREFIX.length), val);
      }
    }
  }
} catch (e) {
  console.error("Failed to load translation cache from localStorage", e);
}

// ─── Translate fallback utility (Google -> Lingva -> MyMemory) ────────────────
export async function translateTextWithFallback(
  text: string,
  targetLang: string
): Promise<string> {
  if (!text || targetLang === "en") return text;

  // Protect brand name "Govlyx"
  const protectedText = text.replace(/Govlyx/gi, "GOVLYXTOKEN");
  const restoreBrand = (str: string) => str.replace(/GOVLYXTOKEN/gi, "Govlyx");

  // Tier 1: Google Translate (Direct CORS-enabled browser call to bypass Vercel server proxy)
  try {
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${targetLang}&dt=t&q=${encodeURIComponent(protectedText)}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Google Translate failed with status ${res.status}`);
    const json = await res.json();
    const translated = (json[0] as [string, string][])
      .map((seg) => seg[0])
      .join("");
    return restoreBrand(translated || protectedText);
  } catch (err) {
    console.warn("Google Translate failed, trying Lingva fallback...", err);
  }

  // Tier 2: Lingva API (Direct CORS-enabled browser call to bypass Vercel server proxy)
  try {
    const url = `https://lingva.ml/api/v1/auto/${targetLang}/${encodeURIComponent(protectedText)}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Lingva failed with status ${res.status}`);
    const json = await res.json();
    if (json && json.translation) {
      return restoreBrand(json.translation);
    }
    throw new Error("Invalid response shape from Lingva");
  } catch (err) {
    console.warn("Lingva fallback failed, trying MyMemory...", err);
  }

  // Tier 3: MyMemory API (Direct CORS-enabled browser call to bypass Vercel server proxy)
  try {
    const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(protectedText)}&langpair=auto|${targetLang}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`MyMemory failed with status ${res.status}`);
    const json = await res.json();
    if (json && json.responseData && json.responseData.translatedText) {
      return restoreBrand(json.responseData.translatedText);
    }
    throw new Error("Invalid response shape from MyMemory");
  } catch (err) {
    console.error("All translation layers failed", err);
    return text;
  }
}

// ─── Translate helper (Google unofficial endpoint) ────────────────────────────
async function googleTranslateBatch(
  texts: string[],
  targetLang: string
): Promise<string[]> {
  if (targetLang === "en") return texts;

  const promises = texts.map(async (text) => {
    const cacheKey = `${targetLang}:${text}`;
    if (translationCache.has(cacheKey)) {
      return translationCache.get(cacheKey)!;
    }

    try {
      const restored = await translateTextWithFallback(text, targetLang);
      if (restored !== text) {
        translationCache.set(cacheKey, restored);
        try {
          localStorage.setItem(STORAGE_PREFIX + cacheKey, restored);
        } catch (e) {
          console.warn("Failed to save translation cache item to localStorage", e);
        }
      }
      return restored;
    } catch (err) {
      console.error("Single translation failed for text:", text, err);
      return text;
    }
  });

  return Promise.all(promises);
}

export async function translateText(
  text: string,
  targetLang: string
): Promise<string> {
  if (!text || targetLang === "en") return text;
  const [result] = await googleTranslateBatch([text], targetLang);
  return result;
}

// ─── DOM-level full-page translation ─────────────────────────────────────────
/**
 * Walks all visible text nodes under document.body and translates them
 * to `targetLang`. Stores original values so we can revert to English.
 */
const originals = new WeakMap<Text, string>();
const originalPlaceholders = new WeakMap<Element, string>();
let currentPageLang = "en";
let globalObserver: MutationObserver | null = null;

function isTranslationAllowed(el: HTMLElement | null): boolean {
  let curr = el;
  while (curr) {
    if (curr.classList && curr.classList.contains("notranslate")) {
      return false;
    }
    if (curr.getAttribute && curr.getAttribute("translate") === "no") {
      return false;
    }
    if (typeof curr.className === "string" && curr.className.split(/\s+/).includes("notranslate")) {
      return false;
    }
    curr = curr.parentElement as HTMLElement | null;
  }
  return true;
}

function isValidTextNode(node: Node, parent: HTMLElement): boolean {
  const tag = parent.tagName.toLowerCase();
  if (["script", "style", "noscript", "input", "textarea", "code", "pre"].includes(tag))
    return false;
  if (!isTranslationAllowed(parent as HTMLElement))
    return false;
  const text = (node.textContent ?? "").trim();
  if (!text || text.length < 2) return false;
  return true;
}

function isValidPlaceholderElement(el: Element): boolean {
  if (!isTranslationAllowed(el as HTMLElement)) return false;
  const placeholder = el.getAttribute("placeholder")?.trim();
  if (!placeholder || placeholder.length < 2) return false;
  return true;
}

async function applyPageTranslation(lang: string, onShowLoader?: (show: boolean) => void) {
  if (lang === currentPageLang) return;

  // First: revert to originals if switching
  if (currentPageLang !== "en") {
    revertPageTranslation();
  }

  currentPageLang = lang;
  if (lang === "en") return;

  const walker = document.createTreeWalker(
    document.body,
    NodeFilter.SHOW_TEXT,
    {
      acceptNode: (node) => {
        const parent = node.parentElement;
        if (!parent) return NodeFilter.FILTER_REJECT;
        if (isValidTextNode(node, parent as HTMLElement)) return NodeFilter.FILTER_ACCEPT;
        return NodeFilter.FILTER_REJECT;
      },
    }
  );

  const nodes: Text[] = [];
  let current: Node | null;
  while ((current = walker.nextNode())) {
    nodes.push(current as Text);
  }

  const inputs = Array.from(document.querySelectorAll("input[placeholder], textarea[placeholder]")).filter(
    isValidPlaceholderElement
  );

  // Save originals
  for (const node of nodes) {
    if (!originals.has(node)) {
      originals.set(node, node.textContent ?? "");
    }
  }
  for (const el of inputs) {
    if (!originalPlaceholders.has(el)) {
      originalPlaceholders.set(el, el.getAttribute("placeholder") ?? "");
    }
  }

  interface TranslateItem {
    type: "text" | "placeholder";
    node: Text | Element;
    original: string;
  }

  const items: TranslateItem[] = [];
  for (const node of nodes) {
    items.push({
      type: "text",
      node,
      original: originals.get(node) ?? ""
    });
  }
  for (const el of inputs) {
    items.push({
      type: "placeholder",
      node: el,
      original: originalPlaceholders.get(el) ?? ""
    });
  }

  const needsFetch = items.some(item => !translationCache.has(`${lang}:${item.original}`));
  if (needsFetch && onShowLoader) {
    onShowLoader(true);
  }

  try {
    // Buffer translations
    const translationsToApply: { type: "text" | "placeholder"; node: Text | Element; text: string }[] = [];

    // Batch translate in groups of 20 to avoid very long URLs
    const BATCH = 20;
    for (let i = 0; i < items.length; i += BATCH) {
      if (currentPageLang !== lang) return; // aborted, discard buffer
      const batch = items.slice(i, i + BATCH);
      const texts = batch.map((item) => item.original);
      try {
        const translated = await googleTranslateBatch(texts, lang);
        for (let j = 0; j < batch.length; j++) {
          translationsToApply.push({
            type: batch[j].type,
            node: batch[j].node,
            text: translated[j],
          });
        }
      } catch (err) {
        console.error("Batch translation failed:", err);
        // Fallback: use original values for this batch
        for (let j = 0; j < batch.length; j++) {
          translationsToApply.push({
            type: batch[j].type,
            node: batch[j].node,
            text: batch[j].original,
          });
        }
      }
    }

    // Apply all translation updates atomically at the end
    if (currentPageLang === lang && translationsToApply.length > 0) {
      for (const item of translationsToApply) {
        if (item.type === "text") {
          const textNode = item.node as Text;
          if (textNode.isConnected) {
            textNode.textContent = item.text;
          }
        } else {
          const el = item.node as Element;
          if (el.isConnected) {
            el.setAttribute("placeholder", item.text);
          }
        }
      }
    }
  } finally {
    if (needsFetch && onShowLoader) {
      onShowLoader(false);
    }
  }
}

// ─── Revert Page Translation ──────────────────────────────────────────────────
function revertPageTranslation() {
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node: Node | null;
  while ((node = walker.nextNode())) {
    const textNode = node as Text;
    const orig = originals.get(textNode);
    if (orig !== undefined && textNode.isConnected) {
      textNode.textContent = orig;
    }
  }

  const inputs = document.querySelectorAll("input[placeholder], textarea[placeholder]");
  for (const el of Array.from(inputs)) {
    const orig = originalPlaceholders.get(el);
    if (orig !== undefined && el.isConnected) {
      el.setAttribute("placeholder", orig);
    }
  }
}

async function translateNewNodes(nodes: Text[], elements: Element[], lang: string) {
  if (lang === "en" || lang !== currentPageLang) return;

  const newNodes = nodes.filter((n) => !originals.has(n));
  const newElements = elements.filter((el) => !originalPlaceholders.has(el));

  if (newNodes.length === 0 && newElements.length === 0) return;

  // Save originals
  for (const n of newNodes) {
    originals.set(n, n.textContent ?? "");
  }
  for (const el of newElements) {
    originalPlaceholders.set(el, el.getAttribute("placeholder") ?? "");
  }

  interface TranslateItem {
    type: "text" | "placeholder";
    node: Text | Element;
    original: string;
  }

  const items: TranslateItem[] = [];
  for (const n of newNodes) {
    items.push({ type: "text", node: n, original: originals.get(n) ?? "" });
  }
  for (const el of newElements) {
    items.push({ type: "placeholder", node: el, original: originalPlaceholders.get(el) ?? "" });
  }

  const translationsToApply: { type: "text" | "placeholder"; node: Text | Element; text: string }[] = [];

  const BATCH = 20;
  for (let i = 0; i < items.length; i += BATCH) {
    if (currentPageLang !== lang) return;
    const batch = items.slice(i, i + BATCH);
    const texts = batch.map(item => item.original);
    try {
      const translated = await googleTranslateBatch(texts, lang);
      for (let j = 0; j < batch.length; j++) {
        translationsToApply.push({
          type: batch[j].type,
          node: batch[j].node,
          text: translated[j],
        });
      }
    } catch (err) {
      console.error("Dynamic batch translation failed:", err);
    }
  }

  if (currentPageLang === lang && translationsToApply.length > 0) {
    if (globalObserver) globalObserver.disconnect();

    for (const item of translationsToApply) {
      if (item.type === "text") {
        const textNode = item.node as Text;
        if (textNode.isConnected) {
          textNode.textContent = item.text;
        }
      } else {
        const el = item.node as Element;
        if (el.isConnected) {
          el.setAttribute("placeholder", item.text);
        }
      }
    }

    if (globalObserver) {
      globalObserver.observe(document.body, {
        childList: true,
        subtree: true,
      });
    }
  }
}

function startObserver(lang: string) {
  if (globalObserver) {
    globalObserver.disconnect();
  }

  globalObserver = new MutationObserver((mutations) => {
    const addedTextNodes: Text[] = [];
    const addedElements: Element[] = [];

    for (const mutation of mutations) {
      if (mutation.type === "childList") {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType === Node.TEXT_NODE) {
            const parent = node.parentElement;
            if (parent && isValidTextNode(node, parent as HTMLElement)) {
              addedTextNodes.push(node as Text);
            }
          } else if (node.nodeType === Node.ELEMENT_NODE) {
            const el = node as Element;
            const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
              acceptNode: (n) => {
                const p = n.parentElement;
                if (p && isValidTextNode(n, p as HTMLElement)) return NodeFilter.FILTER_ACCEPT;
                return NodeFilter.FILTER_REJECT;
              },
            });
            let txt: Node | null;
            while ((txt = walker.nextNode())) {
              addedTextNodes.push(txt as Text);
            }

            if (el.tagName && ["input", "textarea"].includes(el.tagName.toLowerCase())) {
              if (isValidPlaceholderElement(el)) {
                addedElements.push(el);
              }
            }
            el.querySelectorAll("input[placeholder], textarea[placeholder]").forEach((input) => {
              if (isValidPlaceholderElement(input)) {
                addedElements.push(input);
              }
            });
          }
        });
      }
    }

    if (addedTextNodes.length > 0 || addedElements.length > 0) {
      translateNewNodes(addedTextNodes, addedElements, lang);
    }
  });

  globalObserver.observe(document.body, {
    childList: true,
    subtree: true,
  });
}

function stopObserver() {
  if (globalObserver) {
    globalObserver.disconnect();
    globalObserver = null;
  }
}

// ─── Context ──────────────────────────────────────────────────────────────────
interface LanguageContextValue {
  language: LangCode;
  setLanguage: (lang: LangCode) => void;
  isTranslating: boolean;
}

const LanguageContext = createContext<LanguageContextValue>({
  language: "en",
  setLanguage: () => {},
  isTranslating: false,
});

export const useLanguage = () => useContext(LanguageContext);

// ─── Provider ─────────────────────────────────────────────────────────────────
export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<LangCode>(
    () => (localStorage.getItem(STORAGE_KEY) as LangCode) ?? "en"
  );
  const [isTranslating, setIsTranslating] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const setLanguage = useCallback((lang: LangCode) => {
    setLanguageState(lang);
    localStorage.setItem(STORAGE_KEY, lang);
  }, []);

  const { data: userProfile } = useCurrentUser();
  const lastUserIdRef = useRef<any>(null);
  const lastProfileLangRef = useRef<string | null>(null);

  useEffect(() => {
    if (!userProfile) return;

    // Reset sync flag if user changes (login/logout/switch)
    const currentUserId = (userProfile as any).id;
    if (lastUserIdRef.current !== currentUserId) {
      lastUserIdRef.current = currentUserId;
      lastProfileLangRef.current = null;
    }

    const backendLang = (userProfile.interfaceLanguage || userProfile.preferredLanguage) as LangCode | undefined;
    if (backendLang && backendLang !== lastProfileLangRef.current) {
      lastProfileLangRef.current = backendLang;
      if (backendLang !== language) {
        setLanguageState(backendLang);
        localStorage.setItem(STORAGE_KEY, backendLang);
      }
    }
  }, [userProfile, language]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    if (language === "en") {
      currentPageLang = "en";
      stopObserver();
      revertPageTranslation();
      setIsTranslating(false);
      return;
    }

    // Small debounce so rapid changes don't fire multiple translations
    debounceRef.current = setTimeout(async () => {
      stopObserver();
      await applyPageTranslation(language, setIsTranslating);
      startObserver(language);
    }, 300);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
      stopObserver();
    };
  }, [language]);

  return (
    <LanguageContext.Provider value={{ language, setLanguage, isTranslating }}>
      {children}
      {isTranslating && (
        <div className="fixed inset-0 z-[10000] flex flex-col items-center justify-center bg-black/40 backdrop-blur-md transition-all duration-300">
          <div className="flex flex-col items-center p-6 rounded-3xl bg-base-100 border border-base-300 shadow-2xl space-y-4 max-w-xs text-center">
            <span className="loading loading-spinner loading-lg text-[#1D4ED8]" />
            <div className="space-y-1">
              <h3 className="font-bold text-sm text-base-content">Updating Language</h3>
              <p className="text-xs opacity-60">Translating interface elements...</p>
            </div>
          </div>
        </div>
      )}
    </LanguageContext.Provider>
  );
}
