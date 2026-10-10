package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class DynamicNLPProcessor {

    // Common English, Roman-Hindi, and Native Script stop words
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            // English
            "a", "an", "and", "are", "as", "at", "be", "but", "by",
            "for", "if", "in", "into", "is", "it",
            "no", "not", "of", "on", "or", "such",
            "that", "the", "their", "then", "there", "these",
            "they", "this", "to", "was", "will", "with", "what", "where",
            "please", "fix", "issue", "problem", "resolve", "help",
            "near", "outside", "behind", "front", "very", "too", "much",
            "just", "have", "been", "like", "from", "when", "about", "your",
            "would", "could", "should", "does", "done", "doing", "has", "had",
            "out", "our", "you", "she", "his", "her", "him", "how", "who", "why",
            "can", "may", "yes", "all", "any", "one", "two", "see", "get", "got", "did", "say",
            // Roman Hindi / Hinglish
            "hai", "ki", "ka", "ke", "ko", "se", "mein", "par",
            "karo", "kijiye", "bhi", "toh", "hi", "aur", "ya", "ye",
            "wo", "kya", "kab", "kaise", "idhar", "udhar", "yaha", "waha",
            "yahan", "wahan", "sir", "madam", "ji", "mera", "meri", "mere",
            "tum", "aap", "hum", "uska", "unki", "yeh", "woh", "sab", "mat",
            "aaj", "kal", "jab", "tab", "bas", "yhi", "whi", "nai", "nhi", "nahi",
            "hua", "hui", "hue", "raha", "rahi", "rahe", "gaya", "gayi", "gaye",
            // Hindi / Marathi (Devanagari)
            "है", "की", "का", "के", "को", "से", "में", "पर", "और", "भी", "तो",
            "ही", "या", "ये", "वो", "क्या", "कब", "कैसे", "यहाँ", "वहाँ", "इधर",
            "उधर", "नहीं", "हाँ", "एक", "दो", "आप", "तुम", "हम", "मेरा", "मेरी",
            "मेरे", "मुझे", "कुछ", "कोई", "कौन", "क्यों", "कर", "करना", "किया",
            "किए", "लिए", "वाले", "वाली", "हो", "होता", "होती", "होते", "था",
            "थी", "थे", "आहे", "नाही", "पण", "आणि", "तर", "की", "ते", "त्या",
            // Bengali
            "এবং", "আর", "কি", "কে", "কেন", "কোথায়", "কিভাবে", "না", "হ্যাঁ", 
            "এই", "সেই", "আমার", "তুমি", "আপনি", "তারা", "তার", "হয়", "হবে",
            // Telugu
            "మరియు", "లేదా", "అని", "కి", "కు", "నుంచి", "తో", "లో", "మీద",
            "లేదు", "అవును", "ఇది", "అది", "నేను", "మీరు", "వాళ్ళు", "ఎవరు",
            // Tamil
            "மற்றும்", "அல்லது", "என்று", "க்கு", "இருந்து", "உடன்", "இல்", "மேல்",
            "இல்லை", "ஆம்", "இது", "அது", "நான்", "நீங்கள்", "அவர்கள்", "யார்",
            // Kannada
            "ಮತ್ತು", "ಅಥವಾ", "ಎಂದು", "ಗೆ", "ಇಂದ", "ಜೊತೆ", "ರಲ್ಲಿ", "ಮೇಲೆ",
            "ಇಲ್ಲ", "ಹೌದು", "ಇದು", "ಅದು", "ನಾನು", "ನೀವು", "ಅವರು", "ಯಾರು",
            // Malayalam
            "കൂടാതെ", "അല്ലെങ്കിൽ", "എന്ന്", "ക്ക്", "നിന്ന്", "കൂടെ", "ൽ", "മേൽ",
            "ഇല്ല", "അതെ", "ഇത്", "അത്", "ഞാൻ", "നിങ്ങൾ", "അവർ", "ആര്",
            // Gujarati
            "અને", "કે", "માટે", "થી", "માં", "પર", "નથી", "હા", "આ", "તે",
            "હું", "તમે", "તેઓ", "કોણ", "શું", "ક્યાં"
    ));

    private static final Pattern WORD_SPLIT = Pattern.compile("[ \\t\\n\\r,#@_\\.\\-\\?\\!]+");

    public Map<String, Double> parseCandidates(SocialPost post) {
        Map<String, Double> candidates = new HashMap<>();

        // 1. Hashtags (highest weight)
        if (post.hasHashtags()) {
            for (String tag : post.getHashtagsList()) {
                String raw = tag.replaceAll("^#", "").trim().toLowerCase();
                if (!raw.isBlank() && raw.length() > 2) {
                    candidates.put(raw, 1.0);
                }
            }
        }

        // 2. Poll Text
        if (post.hasPoll() && post.getPoll() != null && post.getPoll().getQuestion() != null) {
            extractFromText(post.getPoll().getQuestion(), 0.70, candidates);
        }

        // 3. Post Content
        if (post.getContent() != null) {
            extractFromText(post.getContent(), 0.60, candidates);
        }

        return candidates;
    }

    private void extractFromText(String text, double strength, Map<String, Double> result) {
        if (text == null || text.isBlank()) return;
        
        for (String token : WORD_SPLIT.split(text)) {
            if (token.isBlank()) continue;
            
            String normalized = token.toLowerCase().trim();
            
            // Drop numbers and alphanumeric garbage (basic heuristic)
            if (normalized.matches(".*\\d.*")) continue;
            
            // Length checks depending on script
            if (isAscii(normalized)) {
                // English / Roman Hindi: drop short noise words <= 2 (e.g. 'is', 'to', 'a')
                // This keeps 3-letter nouns like 'bus', 'car', 'gym', 'jal', 'app'
                if (normalized.length() <= 2) continue;
            } else {
                // Native scripts (Devanagari, Dravidian, etc.): words like 'जल' (water) are length 2. 
                // So we keep anything > 1 (i.e. length >= 2).
                if (normalized.length() <= 1) continue;
            }
            
            if (!STOP_WORDS.contains(normalized)) {
                // If it's already there (e.g. from hashtag), keep the higher strength
                result.merge(normalized, strength, Math::max);
            }
        }
    }

    private boolean isAscii(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
}
