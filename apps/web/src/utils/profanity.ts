import filter from "leo-profanity";

// Load standard English dictionary
filter.loadDictionary("en");

// Custom Indian regional / Hinglish / Marathi / local profanity list
const INDIAN_PROFANITY_WORDS = [
  // Hinglish / Hindi bad words
  "chutiya", "chutiye", "bhenchod", "behenchod", "madarchod", "gandu", "bsdk", "bhosdike", "bhosdika",
  "harami", "saala", "sala", "kamina", "kamine", "lauda", "loda", "chut", "gaand", "randi", "r@ndi",
  "randwa", "kutta", "kuttey", "kuttiya", "nalla", "saale", "bhadwa", "bhadwe", "chudaap", "chudap", 
  "lund", "jhand", "tatye", "maaderchod", "bhenchods",
  // Devanagari script bad words
  "चूतिया", "भेनचोद", "बहनचोद", "मादरचोद", "गांडू", "भोसड़ीके", "भोसड़ीका", "लौड़ा", "लोड़ा", "रांडी", "कुत्ता", "कुत्ते", "कमीना", "हरामी", "गांड"
];

// Add custom local dictionary words
filter.add(INDIAN_PROFANITY_WORDS);

/**
 * Checks if a string contains any profanity/bad words.
 * Returns true if profanity is found, false otherwise.
 * Allows manual masking (e.g. f*ck or b*enchod) by only checking full words.
 */
export function checkProfanity(text: string | null | undefined): boolean {
  if (!text) return false;

  // 1. Direct check of the original text
  if (filter.check(text)) {
    return true;
  }

  // 2. Normalize and check lowercase with stripped punctuation to catch simple bypasses (like f.u.c.k)
  const cleaned = text.toLowerCase().replace(/[^a-zA-Z0-9\s\u0900-\u097F]/g, ""); // keeps devanagari letters as well
  if (filter.check(cleaned)) {
    return true;
  }

  // 3. Check individual words specifically
  const words = cleaned.split(/\s+/);
  for (const word of words) {
    if (filter.check(word)) {
      return true;
    }
  }

  return false;
}
