import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Search,
  Loader2,
  Sparkles,
  Smile,
  Image as ImageIcon,
  X,
} from 'lucide-react';
import emojiData from '../../data/emojiCategories.json';
import { GOVLYX_EMOJIS } from '../../utils/stickers';

const KLIPY_API_KEY =
  'wjrMshw6Wowp8dS6rkN59GsIKMPwNbVq8ca7zktoywAmo2kxViARpQgJiRIrA4QD';

interface ChatMediaPickerProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectEmoji: (char: string) => void;
  onSelectGif: (gifUrl: string) => void;
  onSelectSticker: (stickerUrl: string) => void;
}

interface KlipyGifItem {
  id: string;
  title?: string;
  file?: {
    hd?: { gif?: { url?: string }; webp?: { url?: string } };
    md?: { gif?: { url?: string }; webp?: { url?: string } };
    sm?: { gif?: { url?: string }; webp?: { url?: string } };
  };
  images?: {
    fixed_height?: { url?: string };
    original?: { url?: string };
  };
  url?: string;
}

export const ChatMediaPicker: React.FC<ChatMediaPickerProps> = ({
  isOpen,
  onClose,
  onSelectEmoji,
  onSelectGif,
  onSelectSticker,
}) => {
  const [activeTab, setActiveTab] = useState<'emojis' | 'gifs' | 'stickers'>(
    'emojis',
  );
  const [activeEmojiCategory, setActiveEmojiCategory] = useState<string>(
    emojiData[0]?.id || 'smileys',
  );
  const [searchQuery, setSearchQuery] = useState('');
  const categoryBarRef = useRef<HTMLDivElement>(null);

  // GIF states
  const [gifs, setGifs] = useState<KlipyGifItem[]>([]);
  const [loadingGifs, setLoadingGifs] = useState(false);
  const [gifPage, setGifPage] = useState(1);
  const [hasMoreGifs, setHasMoreGifs] = useState(true);

  // Filter emojis based on search
  const filteredEmojiCategories = useMemo(() => {
    if (!searchQuery.trim() || activeTab !== 'emojis') return emojiData;
    const q = searchQuery.toLowerCase().trim();
    return emojiData
      .map((cat) => ({
        ...cat,
        emojis: cat.emojis.filter(
          (e) =>
            e.n.toLowerCase().includes(q) ||
            e.c.includes(q) ||
            e.u.toLowerCase().includes(q),
        ),
      }))
      .filter((cat) => cat.emojis.length > 0);
  }, [searchQuery, activeTab]);

  // Only render the active category's emojis
  const activeCatEmojis = useMemo(() => {
    if (searchQuery.trim()) {
      return filteredEmojiCategories.flatMap((cat) => cat.emojis);
    }
    return (
      emojiData.find((cat) => cat.id === activeEmojiCategory)?.emojis ?? []
    );
  }, [activeEmojiCategory, filteredEmojiCategories, searchQuery]);

  // Fetch GIFs from KLIPY
  const fetchGifs = async (
    query: string,
    page: number = 1,
    append: boolean = false,
  ) => {
    try {
      setLoadingGifs(true);
      const baseUrl = 'https://api.klipy.com/api/v1';
      const cleanQ = query.trim();
      const url = cleanQ
        ? `${baseUrl}/${KLIPY_API_KEY}/gifs/search?q=${encodeURIComponent(cleanQ)}&page=${page}&per_page=24`
        : `${baseUrl}/${KLIPY_API_KEY}/gifs/trending?page=${page}&per_page=24`;

      const res = await fetch(url);
      if (!res.ok) throw new Error(`KLIPY API error: ${res.statusText}`);
      const json = await res.json();
      const items: KlipyGifItem[] = json?.data?.data || json?.data || [];

      if (append) setGifs((prev) => [...prev, ...items]);
      else setGifs(items);

      setHasMoreGifs(items.length >= 20);
    } catch (err) {
      console.error('Error fetching KLIPY GIFs:', err);
    } finally {
      setLoadingGifs(false);
    }
  };

  // Fetch GIFs when tab/query changes
  useEffect(() => {
    if (activeTab === 'gifs' && isOpen) {
      setGifPage(1);
      const timer = setTimeout(
        () => fetchGifs(searchQuery, 1, false),
        searchQuery ? 350 : 0,
      );
      return () => clearTimeout(timer);
    }
  }, [activeTab, searchQuery, isOpen]);

  const handleTabSwitch = (tab: 'emojis' | 'gifs' | 'stickers') => {
    setActiveTab(tab);
    setSearchQuery('');
  };

  if (!isOpen) return null;

  return (
    <div className="absolute bottom-full mb-2 left-0 right-0 sm:left-0 sm:w-[420px] md:w-[460px] max-w-[96vw] h-[360px] max-h-[55vh] bg-base-200 text-base-content border border-base-300 rounded-2xl shadow-2xl shadow-black/20 dark:shadow-black/60 flex flex-col overflow-hidden z-50 animate-in fade-in zoom-in-95 duration-200">
      {/* ── Top Category Icons Bar (emoji tab only, no active search) ── */}
      {activeTab === 'emojis' && !searchQuery.trim() && (
        <div
          ref={categoryBarRef}
          className="flex items-center gap-0 px-1 pt-1 pb-0.5 overflow-x-auto shrink-0 border-b border-base-300"
          style={{ scrollbarWidth: 'none' }}
        >
          {emojiData.map((cat) => (
            <button
              key={cat.id}
              type="button"
              title={cat.label}
              onClick={() => setActiveEmojiCategory(cat.id)}
              className={`shrink-0 w-7 h-7 flex items-center justify-center rounded-lg text-[15px] transition-all ${
                activeEmojiCategory === cat.id
                  ? 'bg-base-content/15'
                  : 'opacity-40 hover:opacity-80 hover:bg-base-content/8'
              }`}
            >
              {cat.icon}
            </button>
          ))}
        </div>
      )}

      {/* ── Search Bar (emoji & gif tabs) ── */}
      {activeTab !== 'stickers' && (
        <div className="px-2.5 pt-1.5 pb-1 shrink-0">
          <div className="relative flex items-center">
            <Search
              size={13}
              className="absolute left-2.5 text-base-content/40 pointer-events-none"
            />
            <input
              type="text"
              placeholder={
                activeTab === 'emojis' ? 'Search emoji...' : 'Search GIFs...'
              }
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-7 pr-7 py-1 bg-base-100 border border-base-300 rounded-lg text-xs text-base-content placeholder-base-content/40 focus:outline-none focus:border-[#1D4ED8] focus:ring-1 focus:ring-[#1D4ED8]/30 transition-all"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2 text-base-content/40 hover:text-base-content transition-colors"
              >
                <X size={12} />
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── Stickers header ── */}
      {activeTab === 'stickers' && (
        <div className="px-3 pt-1.5 pb-0.5 shrink-0">
          <p className="text-[9px] font-bold text-base-content/40 uppercase tracking-wider">
            Official Govlyx Stickers
          </p>
        </div>
      )}

      {/* ── Main Scrollable Content ── */}
      <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain touch-pan-y px-1.5 py-1 scrollbar-thin scrollbar-thumb-base-content/15 scrollbar-track-transparent">
        {/* EMOJIS TAB */}
        {activeTab === 'emojis' && (
          <div>
            {activeCatEmojis.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-32 text-base-content/30 text-xs gap-2">
                <Smile size={24} className="opacity-30" />
                {searchQuery.trim()
                  ? `No emojis found for "${searchQuery}"`
                  : 'No emojis in this category'}
              </div>
            ) : (
              <>
                {searchQuery.trim() && (
                  <div className="text-[9px] text-base-content/40 uppercase tracking-wider mb-1">
                    {activeCatEmojis.length} results
                  </div>
                )}
                <div className="grid grid-cols-9 sm:grid-cols-10 gap-0">
                  {activeCatEmojis.map((emoji) => (
                    <button
                      key={`${emoji.u}-${emoji.n}`}
                      type="button"
                      onClick={() => onSelectEmoji(emoji.c)}
                      title={emoji.n.replace(/_/g, ' ')}
                      className="group relative aspect-square flex items-center justify-center rounded-lg hover:bg-base-content/10 hover:scale-110 active:scale-95 transition-all text-[20px] select-none"
                    >
                      <span>{emoji.c}</span>
                      <span className="pointer-events-none absolute bottom-full mb-1 left-1/2 -translate-x-1/2 hidden group-hover:flex flex-col items-center z-30 px-1.5 py-0.5 bg-base-300 text-base-content text-[9px] font-semibold rounded-md border border-base-content/10 shadow-xl whitespace-nowrap capitalize">
                        {emoji.n.replace(/_/g, ' ')}
                      </span>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* GIFS TAB */}
        {activeTab === 'gifs' && (
          <div>
            {gifs.length === 0 && !loadingGifs ? (
              <div className="flex flex-col items-center justify-center h-44 text-base-content/30 text-xs gap-2">
                <ImageIcon size={30} className="opacity-30" />
                No GIFs found. Try a different search!
              </div>
            ) : (
              <div className="columns-2 gap-2 space-y-2">
                {gifs.map((gif, idx) => {
                  const mediaUrl =
                    gif.file?.hd?.gif?.url ||
                    gif.file?.md?.gif?.url ||
                    gif.file?.sm?.gif?.url ||
                    gif.images?.fixed_height?.url ||
                    gif.images?.original?.url ||
                    gif.url;
                  const previewUrl =
                    gif.file?.sm?.webp?.url ||
                    gif.file?.sm?.gif?.url ||
                    gif.file?.md?.webp?.url ||
                    mediaUrl;
                  if (!mediaUrl) return null;

                  return (
                    <button
                      key={gif.id ? `${gif.id}-${idx}` : idx}
                      type="button"
                      onClick={() => onSelectGif(mediaUrl)}
                      className="group relative w-full overflow-hidden rounded-xl bg-base-300 border border-base-300 hover:border-[#1D4ED8]/50 hover:shadow-md transition-all block break-inside-avoid"
                    >
                      <img
                        src={previewUrl}
                        alt={gif.title || 'GIF'}
                        loading="lazy"
                        className="w-full h-auto object-cover group-hover:scale-105 transition-transform duration-300"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end p-2">
                        <span className="text-[10px] text-white/90 font-medium truncate">
                          {gif.title || 'Send GIF'}
                        </span>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            {hasMoreGifs && gifs.length > 0 && (
              <div className="pt-3 pb-1 flex justify-center">
                <button
                  type="button"
                  disabled={loadingGifs}
                  onClick={() => {
                    const next = gifPage + 1;
                    setGifPage(next);
                    fetchGifs(searchQuery, next, true);
                  }}
                  className="px-4 py-1.5 bg-base-300 hover:bg-base-content/10 border border-base-content/10 rounded-xl text-xs text-base-content/70 hover:text-base-content transition-colors flex items-center gap-2"
                >
                  {loadingGifs ? (
                    <Loader2
                      size={13}
                      className="animate-spin text-[#1D4ED8]"
                    />
                  ) : null}
                  <span>
                    {loadingGifs ? 'Loading more...' : 'Load more GIFs'}
                  </span>
                </button>
              </div>
            )}

            {loadingGifs && gifs.length === 0 && (
              <div className="flex flex-col items-center justify-center h-44 text-base-content/30 text-xs gap-2">
                <Loader2 size={24} className="animate-spin text-[#1D4ED8]" />
                <span>Loading GIFs...</span>
              </div>
            )}
          </div>
        )}

        {/* STICKERS TAB */}
        {activeTab === 'stickers' && (
          <div className="grid grid-cols-4 sm:grid-cols-5 gap-2">
            {GOVLYX_EMOJIS.map((stickerUrl, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => onSelectSticker(stickerUrl)}
                className="group relative aspect-square p-2 rounded-2xl bg-base-100 hover:bg-base-300 border border-base-300 hover:border-[#1D4ED8]/40 hover:scale-105 active:scale-95 transition-all flex items-center justify-center"
              >
                <img
                  src={stickerUrl}
                  alt={`Govlyx sticker ${idx + 1}`}
                  loading="lazy"
                  className="w-full h-full object-contain drop-shadow-sm group-hover:drop-shadow-lg transition-all"
                />
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── Bottom Tab Bar — compact Telegram-style pill ── */}
      <div className="shrink-0 flex items-center justify-between px-2.5 py-1.5 border-t border-base-300 bg-base-300/50">
        {/* Tab Pills */}
        <div className="flex items-center bg-base-content/8 rounded-xl p-0.5 gap-0.5">
          {(
            [
              { id: 'emojis', label: 'Emoji', Icon: Smile },
              { id: 'gifs', label: 'GIFs', Icon: ImageIcon },
              { id: 'stickers', label: 'Stickers', Icon: Sparkles },
            ] as const
          ).map(({ id, label, Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => handleTabSwitch(id)}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold transition-all ${
                activeTab === id
                  ? 'bg-base-100 text-base-content shadow-sm'
                  : 'text-base-content/50 hover:text-base-content'
              }`}
            >
              <Icon size={13} />
              <span>{label}</span>
            </button>
          ))}
        </div>

        {/* Close Button */}
        <button
          type="button"
          onClick={onClose}
          className="w-7 h-7 flex items-center justify-center bg-base-content/8 hover:bg-base-content/15 rounded-lg text-base-content/50 hover:text-base-content transition-all border border-base-content/10"
          title="Close"
        >
          <X size={14} />
        </button>
      </div>
    </div>
  );
};
