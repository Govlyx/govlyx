import React, { useState, useRef, useEffect, useCallback } from "react";
import { 
  Image as ImageIcon, X, Send, AtSign, Hash, 
  Zap, ChevronRight, Film, Plus
} from "lucide-react";
import axiosInstance from "../../api/axiosConfig";
import { checkProfanity } from "../../utils/profanity";
import { showToast } from "../../utils/toast";
import { parseError } from "../../utils/error-handler";

interface CommunityPostComposerProps {
  communityId: number;
  communityName: string;
  onPostSuccess: (post: any) => void;
  onCancel: () => void;
}

const CommunityPostComposer = ({ 
  communityId, 
  communityName, 
  onPostSuccess, 
  onCancel 
}: CommunityPostComposerProps) => {
  const [content, setContent] = useState("");
  const [mediaFiles, setMediaFiles] = useState<File[]>([]);
  const [posting, setPosting] = useState(false);
  const [allowComments, setAllowComments] = useState(true);
  
  // Suggestions state
  const [suggestionType, setSuggestionType] = useState<"user" | "tag" | null>(null);
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [suggestionQuery, setSuggestionQuery] = useState("");

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const suggestionRef = useRef<HTMLDivElement>(null);

  // Character limit
  const MAX_CHARS = 3000;

  // Handle media selection
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    setMediaFiles(prev => {
      const combined = [...prev, ...files];
      if (combined.length > 4) {
        showToast.error("Maximum of 4 attachments allowed.");
        return combined.slice(0, 4);
      }
      return combined;
    });
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const removeMediaFile = (index: number) => {
    setMediaFiles(prev => prev.filter((_, i) => i !== index));
  };

  // Suggestion discovery
  const checkTriggers = useCallback((value: string, cursorIndex: number) => {
    const textBeforeCursor = value.slice(0, cursorIndex);
    const lastWord = textBeforeCursor.split(/\s/).pop() || "";
    
    if (lastWord.startsWith("@") && lastWord.length >= 2) {
      setSuggestionType("user");
      setSuggestionQuery(lastWord.slice(1));
    } else if (lastWord.startsWith("#") && lastWord.length >= 2) {
      setSuggestionType("tag");
      setSuggestionQuery(lastWord.slice(1));
    } else {
      setSuggestionType(null);
      setSuggestions([]);
    }
  }, []);

  useEffect(() => {
    if (!suggestionType || !suggestionQuery) return;
    
    const fetchSuggestions = async () => {
      try {
        if (suggestionType === "user") {
          const res = await axiosInstance.get(`/api/users/search/tagging?query=${encodeURIComponent(suggestionQuery)}&limit=5`);
          const data = res.data?.data || res.data?.content || [];
          setSuggestions(Array.isArray(data) ? data : []);
        } else {
          const res = await axiosInstance.get(`/api/search/quick?q=%23${encodeURIComponent(suggestionQuery)}`);
          const grouped = res.data?.grouped || {};
          const tags = grouped.HASHTAG || [];
          setSuggestions(tags);
        }
        setSelectedIndex(0);
      } catch (err) {
        setSuggestions([]);
      }
    };

    const timeout = setTimeout(fetchSuggestions, 200);
    return () => clearTimeout(timeout);
  }, [suggestionType, suggestionQuery]);

  const insertSuggestion = (suggestion: any) => {
    if (!textareaRef.current) return;
    const value = content;
    const cursorIndex = textareaRef.current.selectionStart;
    const textBeforeCursor = value.slice(0, cursorIndex);
    const textAfterCursor = value.slice(cursorIndex);
    
    const words = textBeforeCursor.split(/\s/);
    words.pop(); // remove the @tag or #tag trigger
    
    const trigger = suggestionType === "user" ? "@" : "#";
    const insertValue = suggestionType === "user" ? (suggestion.username || suggestion.displayName) : (suggestion.hashtag || suggestion.token);
    
    const cleanValue = String(insertValue).replace(/^#+/, "");
    
    const newValue = words.join(" ") + (words.length > 0 ? " " : "") + trigger + cleanValue + " " + textAfterCursor;
    setContent(newValue);
    setSuggestionType(null);
    setSuggestions([]);
    
    setTimeout(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        const nextPos = (words.join(" ").length > 0 ? words.join(" ").length + 1 : 0) + trigger.length + cleanValue.length + 1;
        textareaRef.current.setSelectionRange(nextPos, nextPos);
      }
    }, 0);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (suggestionType && suggestions.length > 0) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedIndex(prev => (prev + 1) % suggestions.length);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedIndex(prev => (prev - 1 + suggestions.length) % suggestions.length);
      } else if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        insertSuggestion(suggestions[selectedIndex]);
      } else if (e.key === "Escape") {
        setSuggestionType(null);
      }
    }
  };

  const handleSubmit = async () => {
    if (!content.trim() && mediaFiles.length === 0) return;
    
    if (checkProfanity(content)) {
      showToast.error("Content contains prohibited language/profanity. Please check your words.");
      return;
    }

    setPosting(true);
    
    try {
      let result;
      if (mediaFiles.length > 0) {
        const formData = new FormData();
        const postPayload = {
          content: content.trim(),
          communityId: communityId,
          allowComments: allowComments
        };
        formData.append("post", new Blob([JSON.stringify(postPayload)], { type: "application/json" }));
        mediaFiles.forEach(file => formData.append("media", file));

        const res = await axiosInstance.post("/api/social-posts/with-media", formData);
        result = res.data?.data ?? res.data;
      } else {
        const res = await axiosInstance.post("/api/social-posts/text", {
          content: content.trim(),
          communityId: communityId,
          allowComments: allowComments
        });
        result = res.data?.data ?? res.data;
      }

      showToast.success("Post published successfully!");
      onPostSuccess(result);
      setContent("");
      setMediaFiles([]);
    } catch (err: any) {
      console.error("[Post Error]", err);
      showToast.error(parseError(err));
    } finally {
      setPosting(false);
    }
  };

  return (
    <div className="rounded-2xl border border-base-300 bg-base-100 shadow-sm overflow-hidden transition-all duration-200 mb-6">
      <div className="px-4 py-2 bg-gradient-to-r from-blue-700/5 to-transparent border-b border-base-300 flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase tracking-wider opacity-50 flex items-center gap-1.5">
          <Zap size={12} className="text-blue-700" /> Posting in {communityName}
        </span>
        <div className="flex items-center gap-3">
           <label className="flex items-center gap-2 cursor-pointer group">
              <span className="text-xs opacity-50 group-hover:opacity-100 transition-opacity">Allow comments</span>
              <input 
                type="checkbox" 
                checked={allowComments} 
                onChange={(e) => setAllowComments(e.target.checked)}
                className="toggle toggle-xs toggle-primary" 
              />
           </label>
        </div>
      </div>

      <div className="p-4 space-y-3 font-outfit">
        <div className="relative">
          <textarea
            ref={textareaRef}
            className="textarea textarea-ghost w-full min-h-[140px] p-0 text-base leading-relaxed bg-transparent focus:ring-0 resize-none pb-8"
            placeholder={`What's on your mind ${communityName}? Use @ to mention or # for tags.`}
            value={content}
            onKeyUp={(e: any) => checkTriggers(e.target.value, e.target.selectionStart)}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={posting}
          />
          
          <div className={`absolute bottom-0 right-0 text-[10px] font-medium transition-colors ${content.length > MAX_CHARS * 0.9 ? "text-error" : "opacity-30"}`}>
            {content.length.toLocaleString()} / {MAX_CHARS.toLocaleString()}
          </div>

          {suggestionType && suggestions.length > 0 && (
            <div 
              ref={suggestionRef}
              className="absolute z-50 mt-1 w-64 max-h-56 bg-base-100 border border-base-300 rounded-xl shadow-2xl overflow-y-auto"
              style={{ top: '100%', left: 0 }}
            >
              {suggestions.map((item, idx) => (
                <button
                  key={idx}
                  className={`w-full flex items-center gap-3 px-3 py-2.5 text-left transition-colors ${selectedIndex === idx ? "bg-blue-700/10 text-blue-700 border-l-2 border-blue-700" : "hover:bg-base-200"}`}
                  onClick={() => insertSuggestion(item)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                >
                  {suggestionType === "user" ? (
                    <>
                      <div className="w-8 h-8 rounded-full overflow-hidden border border-base-300 bg-base-200 shrink-0">
                        {item.profileImage ? (
                                <img src={item.profileImage} alt="" className="w-full h-full object-cover" />
                              ) : (
                                <div className="w-full h-full flex items-center justify-center bg-blue-700/10 text-blue-700 font-bold text-xs">
                                  {item.username[0].toUpperCase()}
                                </div>
                              )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold truncate">@{item.username}</p>
                        {item.displayName && <p className="text-[10px] opacity-50 truncate">{item.displayName}</p>}
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="w-8 h-8 rounded-lg flex items-center justify-center bg-base-200 text-base-content/40 shrink-0">
                        <Hash size={16} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold truncate">{item.hashtag || item.token}</p>
                        <p className="text-[10px] opacity-50">{item.postCount?.toLocaleString() || 0} posts</p>
                      </div>
                    </>
                  )}
                  {selectedIndex === idx && <ChevronRight size={14} className="opacity-40" />}
                </button>
              ))}
            </div>
          )}
        </div>

        {mediaFiles.length > 0 && (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 pt-2 border-t border-base-200 mt-2">
            {mediaFiles.map((file, idx) => {
              const isVideo = file.type.startsWith("video/");
              return (
                <div key={idx} className="relative group aspect-square rounded-xl overflow-hidden border border-base-300 bg-base-200 shadow-sm">
                  {isVideo ? (
                    <div className="w-full h-full flex flex-col items-center justify-center">
                       <Film size={24} className="opacity-40 mb-1" />
                       <span className="text-[9px] font-bold opacity-40 uppercase">Video</span>
                    </div>
                  ) : (
                    <img 
                      src={URL.createObjectURL(file)} 
                      alt="" 
                      className="w-full h-full object-cover" 
                    />
                  )}
                  <button 
                    className="absolute top-2 right-2 bg-black/60 text-white rounded-full p-1 opacity-0 group-hover:opacity-100 transition-opacity backdrop-blur-md"
                    onClick={() => removeMediaFile(idx)}
                  >
                    <X size={14} />
                  </button>
                  <div className="absolute bottom-2 left-2 bg-black/40 text-[9px] text-white px-1.5 py-0.5 rounded backdrop-blur-md opacity-0 group-hover:opacity-100 transition-opacity truncate max-w-[80%]">
                    {file.name}
                  </div>
                </div>
              );
            })}
            {mediaFiles.length < 4 && (
              <button 
                className="aspect-square rounded-xl border-2 border-dashed border-base-300 flex flex-col items-center justify-center opacity-40 hover:opacity-100 hover:border-blue-700 hover:bg-blue-700/5 transition-all"
                onClick={() => fileInputRef.current?.click()}
              >
                <Plus size={24} />
                <span className="text-[10px] font-bold mt-1 uppercase">Add More</span>
              </button>
            )}
          </div>
        )}

        <div className="flex items-center justify-between border-t border-base-300 pt-3">
          <div className="flex items-center gap-1">
            <button 
              className="btn btn-ghost btn-sm btn-circle text-blue-700" 
              onClick={() => fileInputRef.current?.click()}
              title="Add media (Image/Video)"
            >
              <ImageIcon size={20} />
            </button>
            <input 
              ref={fileInputRef}
              type="file" 
              accept="image/*,video/*" 
              multiple 
              className="hidden" 
              onChange={handleFileSelect} 
            />
            <button 
              className="btn btn-ghost btn-sm btn-circle opacity-40 hover:opacity-100" 
              onClick={() => {
                if (!textareaRef.current) return;
                const pos = textareaRef.current.selectionStart;
                setContent(prev => prev.slice(0, pos) + "@" + prev.slice(pos));
                textareaRef.current.focus();
                checkTriggers(content + "@", pos + 1);
              }}
              title="Mention someone"
            >
              <AtSign size={20} />
            </button>
            <button 
              className="btn btn-ghost btn-sm btn-circle opacity-40 hover:opacity-100"
              onClick={() => {
                if (!textareaRef.current) return;
                const pos = textareaRef.current.selectionStart;
                setContent(prev => prev.slice(0, pos) + "#" + prev.slice(pos));
                textareaRef.current.focus();
                checkTriggers(content + "#", pos + 1);
              }}
              title="Add Hashtag"
            >
              <Hash size={20} />
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button 
              className="btn btn-ghost btn-sm font-semibold opacity-60 hover:opacity-100" 
              onClick={onCancel}
              disabled={posting}
            >
              Cancel
            </button>
            <button 
              className="btn bg-blue-700 text-white font-bold border-none hover:bg-blue-800 btn-sm px-6 h-9 min-h-[36px] shadow-lg shadow-blue-700/20 gap-2"
              onClick={handleSubmit}
              disabled={(!content.trim() && mediaFiles.length === 0) || posting || content.length > MAX_CHARS}
            >
              {posting ? (
                <>
                  <span className="loading loading-spinner loading-xs" />
                  Posting...
                </>
              ) : (
                <>
                  <Send size={14} />
                  Post
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CommunityPostComposer;
