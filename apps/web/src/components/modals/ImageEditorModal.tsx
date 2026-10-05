import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X,
  Crop,
  Sparkles,
  Smile,
  ZoomIn,
  ZoomOut,
  Trash2,
  Check,
  RefreshCw,
} from 'lucide-react';
import { GOVLYX_EMOJIS } from '../../utils/stickers';

interface ImageEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  imageSrc: string;
  onSave: (editedBlob: Blob) => Promise<void>;
  cropShape?: 'circle' | 'rect';
  title?: string;
}

interface StickerInstance {
  id: number;
  url: string;
  x: number;
  y: number;
  scale: number;
  rotation: number;
}

const FILTER_PRESETS = [
  { name: 'None', value: 'none', style: 'none' },
  { name: 'Grayscale', value: 'grayscale', style: 'grayscale(1)' },
  { name: 'Sepia', value: 'sepia', style: 'sepia(1)' },
  {
    name: 'Warm',
    value: 'warm',
    style: 'sepia(0.2) saturate(1.4) hue-rotate(-10deg)',
  },
  {
    name: 'Cool',
    value: 'cool',
    style: 'saturate(1.2) hue-rotate(10deg) brightness(1.05)',
  },
  {
    name: 'Vintage',
    value: 'vintage',
    style: 'sepia(0.5) contrast(1.2) saturate(1.2)',
  },
  { name: 'Brighten', value: 'brighten', style: 'brightness(1.3)' },
  { name: 'Contrast', value: 'contrast', style: 'contrast(1.5)' },
];

export default function ImageEditorModal({
  isOpen,
  onClose,
  imageSrc,
  onSave,
  cropShape = 'circle',
  title,
}: ImageEditorModalProps) {
  const isRect = cropShape === 'rect';
  const [activeTab, setActiveTab] = useState<'crop' | 'filter' | 'stickers'>(
    'crop',
  );

  // Image layout state
  const [zoom, setZoom] = useState(1);
  const [imageOffset, setImageOffset] = useState({ x: 0, y: 0 });
  const [selectedFilter, setSelectedFilter] = useState('none');
  const [stickers, setStickers] = useState<StickerInstance[]>([]);
  const [activeStickerId, setActiveStickerId] = useState<number | null>(null);

  // Dragging states
  const [isDragging, setIsDragging] = useState(false);
  const [dragMode, setDragMode] = useState<'image' | 'sticker' | null>(null);
  const dragStartPos = useRef({ x: 0, y: 0 });
  const dragStartOffset = useRef({ x: 0, y: 0 });

  const viewportRef = useRef<HTMLDivElement>(null);
  const [processing, setProcessing] = useState(false);

  // Image properties
  const [imgDimensions, setImgDimensions] = useState({
    width: 0,
    height: 0,
    aspect: 1,
  });

  useEffect(() => {
    if (imageSrc) {
      const img = new Image();
      img.src = imageSrc;
      img.onload = () => {
        setImgDimensions({
          width: img.width,
          height: img.height,
          aspect: img.width / img.height,
        });
        // Reset states
        setZoom(1);
        setImageOffset({ x: 0, y: 0 });
        setSelectedFilter('none');
        setStickers([]);
        setActiveStickerId(null);
      };
    }
  }, [imageSrc]);

  const activeSticker = stickers.find((s) => s.id === activeStickerId);

  // Dragging handlers
  const handleStartDrag = (
    e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement>,
  ) => {
    if (!viewportRef.current) return;

    // Get client coordinates
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;

    // Get coordinates relative to the viewport
    const rect = viewportRef.current.getBoundingClientRect();
    const mx = clientX - rect.left;
    const my = clientY - rect.top;

    // Check if we clicked on a sticker (traverse backwards to select topmost sticker)
    let clickedStickerId: number | null = null;
    for (let i = stickers.length - 1; i >= 0; i--) {
      const s = stickers[i];
      const radius = 30 * s.scale; // Approximate sticker radius in the 200px viewport
      const distance = Math.sqrt(Math.pow(mx - s.x, 2) + Math.pow(my - s.y, 2));
      if (distance < radius) {
        clickedStickerId = s.id;
        break;
      }
    }

    if (clickedStickerId !== null) {
      // Start dragging sticker
      setActiveStickerId(clickedStickerId);
      setDragMode('sticker');
      setIsDragging(true);
      const s = stickers.find((st) => st.id === clickedStickerId)!;
      dragStartPos.current = { x: clientX, y: clientY };
      dragStartOffset.current = { x: s.x, y: s.y };
      setActiveTab('stickers');
    } else {
      // Start dragging background image (for cropping)
      setDragMode('image');
      setIsDragging(true);
      dragStartPos.current = { x: clientX, y: clientY };
      dragStartOffset.current = { x: imageOffset.x, y: imageOffset.y };
    }
  };

  const handleDrag = (
    e: React.MouseEvent<HTMLDivElement> | React.TouchEvent<HTMLDivElement>,
  ) => {
    if (!isDragging || !dragMode) return;

    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY;

    const dx = clientX - dragStartPos.current.x;
    const dy = clientY - dragStartPos.current.y;

    if (dragMode === 'image') {
      setImageOffset({
        x: dragStartOffset.current.x + dx,
        y: dragStartOffset.current.y + dy,
      });
    } else if (dragMode === 'sticker' && activeStickerId !== null) {
      setStickers((prev) =>
        prev.map((s) =>
          s.id === activeStickerId
            ? {
                ...s,
                x: dragStartOffset.current.x + dx,
                y: dragStartOffset.current.y + dy,
              }
            : s,
        ),
      );
    }
  };

  const handleStopDrag = () => {
    setIsDragging(false);
    setDragMode(null);
  };

  const vpWidth = isRect ? 340 : 200;
  const vpHeight = isRect ? 150 : 200;
  const vpAspect = vpWidth / vpHeight;

  const canvasWidth = isRect ? 850 : 500;
  const canvasHeight = isRect ? 375 : 500;

  const imgWidth =
    imgDimensions.aspect > vpAspect ? vpHeight * imgDimensions.aspect : vpWidth;
  const imgHeight =
    imgDimensions.aspect > vpAspect ? vpHeight : vpWidth / imgDimensions.aspect;

  const addSticker = (url: string) => {
    const newSticker: StickerInstance = {
      id: Date.now(),
      url,
      x: vpWidth / 2,
      y: vpHeight / 2,
      scale: 1,
      rotation: 0,
    };
    setStickers((prev) => [...prev, newSticker]);
    setActiveStickerId(newSticker.id);
  };

  const updateSticker = (changes: Partial<StickerInstance>) => {
    if (activeStickerId === null) return;
    setStickers((prev) =>
      prev.map((s) => (s.id === activeStickerId ? { ...s, ...changes } : s)),
    );
  };

  const deleteActiveSticker = () => {
    if (activeStickerId === null) return;
    setStickers((prev) => prev.filter((s) => s.id !== activeStickerId));
    setActiveStickerId(null);
  };

  // Compile final image onto Canvas
  const handleApply = async () => {
    setProcessing(true);
    try {
      const canvas = document.createElement('canvas');
      canvas.width = canvasWidth;
      canvas.height = canvasHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('Could not create 2D canvas context');

      // Draw background image with selected filter
      const filterObj = FILTER_PRESETS.find((f) => f.value === selectedFilter);
      ctx.filter = filterObj ? filterObj.style : 'none';

      const baseImg = new Image();
      baseImg.src = imageSrc;
      await new Promise((resolve, reject) => {
        baseImg.onload = resolve;
        baseImg.onerror = reject;
      });

      const canvasAspect = canvasWidth / canvasHeight;
      let drawWidth = canvasWidth;
      let drawHeight = canvasHeight;
      if (imgDimensions.aspect > canvasAspect) {
        drawWidth = canvasHeight * imgDimensions.aspect;
      } else {
        drawHeight = canvasWidth / imgDimensions.aspect;
      }

      ctx.save();
      ctx.translate(canvasWidth / 2, canvasHeight / 2);
      ctx.translate(imageOffset.x * 2.5, imageOffset.y * 2.5); // coordinates scale 2.5x from viewport to canvas
      ctx.scale(zoom, zoom);
      ctx.drawImage(
        baseImg,
        -drawWidth / 2,
        -drawHeight / 2,
        drawWidth,
        drawHeight,
      );
      ctx.restore();

      // Reset filter for stickers so stickers retain original color
      ctx.filter = 'none';

      // Draw stickers
      for (const sticker of stickers) {
        const sImg = new Image();
        sImg.crossOrigin = 'anonymous';
        await new Promise((resolve, reject) => {
          sImg.onload = () => resolve(null);
          sImg.onerror = () => {
            fetch(sticker.url)
              .then((r) => r.text())
              .then((text) => {
                const blob = new Blob([text], {
                  type: 'image/svg+xml;charset=utf-8',
                });
                const blobUrl = URL.createObjectURL(blob);
                const fbImg = new Image();
                fbImg.onload = () => {
                  URL.revokeObjectURL(blobUrl);
                  sImg.src = fbImg.src;
                  resolve(null);
                };
                fbImg.onerror = (err) => {
                  URL.revokeObjectURL(blobUrl);
                  reject(err);
                };
                fbImg.src = blobUrl;
              })
              .catch(reject);
          };
          sImg.src = sticker.url;
        });

        ctx.save();
        ctx.translate(sticker.x * 2.5, sticker.y * 2.5); // coordinates scale 2.5x
        ctx.rotate((sticker.rotation * Math.PI) / 180);
        const baseStickerSize = 150 * sticker.scale; // 60px in viewport container maps to 150px in canvas
        ctx.drawImage(
          sImg,
          -baseStickerSize / 2,
          -baseStickerSize / 2,
          baseStickerSize,
          baseStickerSize,
        );
        ctx.restore();
      }

      // Convert to Blob and Save
      canvas.toBlob(
        async (blob) => {
          if (blob && blob.size > 0) {
            try {
              await onSave(blob);
            } catch (saveErr) {
              console.error('Save callback error:', saveErr);
            } finally {
              setProcessing(false);
            }
          } else {
            console.error('Canvas compilation failed or empty');
            alert('Failed to render image. Please try again.');
            setProcessing(false);
          }
        },
        'image/jpeg',
        0.92,
      );
    } catch (err) {
      console.error(err);
      alert('Failed to render and save image.');
      setProcessing(false);
    }
  };

  const selectedFilterStyle =
    FILTER_PRESETS.find((f) => f.value === selectedFilter)?.style ?? 'none';

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-2 sm:p-4 backdrop-blur-sm bg-black/40">
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            className="w-full max-w-md max-h-[92vh] md:max-h-[95vh] overflow-hidden rounded-2xl sm:rounded-3xl border border-black/10 dark:border-white/15 bg-base-100 shadow-2xl flex flex-col"
          >
            {/* Header */}
            <div className="px-3.5 sm:px-4 py-2.5 sm:py-3.5 border-b border-base-300 flex items-center justify-between">
              <h2 className="text-xs sm:text-sm font-black uppercase tracking-wider flex items-center gap-1.5 sm:gap-2">
                <Sparkles size={14} className="text-primary sm:size-4" />{' '}
                {title || (isRect ? 'Edit Cover Photo' : 'Edit Profile Photo')}
              </h2>
              <button
                onClick={onClose}
                disabled={processing}
                className="p-1 sm:p-1.5 rounded-full hover:bg-base-200 text-base-content/60 transition-colors cursor-pointer"
              >
                <X size={16} className="sm:size-[18px]" />
              </button>
            </div>

            {/* Editing Canvas Viewport Container */}
            <div className="p-3 sm:p-4 md:p-6 flex flex-col items-center justify-center bg-base-200/30 border-b border-base-300 relative">
              <div
                ref={viewportRef}
                onMouseDown={handleStartDrag}
                onMouseMove={handleDrag}
                onMouseUp={handleStopDrag}
                onMouseLeave={handleStopDrag}
                onTouchStart={handleStartDrag}
                onTouchMove={handleDrag}
                onTouchEnd={handleStopDrag}
                className={`${
                  isRect
                    ? 'w-[280px] sm:w-[340px] max-w-[85vw] h-[120px] sm:h-[150px] rounded-xl sm:rounded-2xl'
                    : 'w-[160px] sm:w-[200px] h-[160px] sm:h-[200px] rounded-full'
                } overflow-hidden border-4 border-primary/20 bg-black relative select-none cursor-move shadow-inner transition-all duration-300`}
              >
                {/* Background Image with CSS Transform & CSS Filters */}
                {imageSrc && (
                  <img
                    src={imageSrc}
                    alt=""
                    draggable="false"
                    className="absolute pointer-events-none select-none max-w-none origin-center"
                    style={{
                      width: `${imgWidth}px`,
                      height: `${imgHeight}px`,
                      top: '50%',
                      left: '50%',
                      marginLeft: `${-imgWidth / 2}px`,
                      marginTop: `${-imgHeight / 2}px`,
                      transform: `translate(${imageOffset.x}px, ${imageOffset.y}px) scale(${zoom})`,
                      filter: selectedFilterStyle,
                    }}
                  />
                )}

                {/* Stickers rendered on top */}
                {stickers.map((s) => (
                  <div
                    key={s.id}
                    className={`absolute pointer-events-none select-none origin-center ${
                      activeStickerId === s.id
                        ? 'ring-2 ring-primary rounded-lg'
                        : ''
                    }`}
                    style={{
                      left: `${s.x}px`,
                      top: `${s.y}px`,
                      width: '60px',
                      height: '60px',
                      marginLeft: '-30px',
                      marginTop: '-30px',
                      transform: `scale(${s.scale}) rotate(${s.rotation}deg)`,
                    }}
                  >
                    <img
                      src={s.url}
                      alt="sticker"
                      draggable="false"
                      className="w-full h-full object-contain"
                    />
                  </div>
                ))}
              </div>
              <p className="text-[9px] sm:text-[10px] text-base-content/40 font-bold uppercase tracking-wider mt-1.5 sm:mt-2 select-none">
                Drag to align photo • Tap stickers to modify
              </p>
            </div>
            {/* Mode Selector Tabs */}
            <div className="flex border-b border-base-300">
              <button
                onClick={() => setActiveTab('crop')}
                className={`flex-1 py-2 sm:py-3 text-[11px] sm:text-xs font-black uppercase tracking-wider flex items-center justify-center gap-1.5 border-b-2 transition-all ${
                  activeTab === 'crop'
                    ? 'border-red-500 text-red-500 dark:border-red-400 dark:text-red-400 bg-red-500/5 dark:bg-red-400/5'
                    : 'border-transparent text-base-content/60 hover:text-base-content hover:bg-base-200/50'
                }`}
              >
                <Crop size={13} className="sm:size-3.5" /> Crop
              </button>
              <button
                onClick={() => setActiveTab('filter')}
                className={`flex-1 py-2 sm:py-3 text-[11px] sm:text-xs font-black uppercase tracking-wider flex items-center justify-center gap-1.5 border-b-2 transition-all ${
                  activeTab === 'filter'
                    ? 'border-red-500 text-red-500 dark:border-red-400 dark:text-red-400 bg-red-500/5 dark:bg-red-400/5'
                    : 'border-transparent text-base-content/60 hover:text-base-content hover:bg-base-200/50'
                }`}
              >
                <Sparkles size={13} className="sm:size-3.5" /> Filters
              </button>
              <button
                onClick={() => setActiveTab('stickers')}
                className={`flex-1 py-2 sm:py-3 text-[11px] sm:text-xs font-black uppercase tracking-wider flex items-center justify-center gap-1.5 border-b-2 transition-all ${
                  activeTab === 'stickers'
                    ? 'border-red-500 text-red-500 dark:border-red-400 dark:text-red-400 bg-red-500/5 dark:bg-red-400/5'
                    : 'border-transparent text-base-content/60 hover:text-base-content hover:bg-base-200/50'
                }`}
              >
                <Smile size={13} className="sm:size-3.5" /> Stickers
              </button>
            </div>
            {/* Control Panel Area */}
            <div className="p-3 sm:p-4 md:p-5 flex-1 min-h-[140px] sm:min-h-[180px] max-h-[220px] sm:max-h-[250px] overflow-y-auto overscroll-contain bg-base-200/10 scrollbar-thin">
              {/* CROP MODE */}
              {activeTab === 'crop' && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-xs font-bold text-base-content/60 flex items-center gap-1">
                      <ZoomOut size={12} /> Size
                    </span>
                    <input
                      type="range"
                      min="1"
                      max="3"
                      step="0.05"
                      value={zoom}
                      onChange={(e) => setZoom(parseFloat(e.target.value))}
                      className="range range-xs range-primary flex-1"
                    />
                    <span className="text-xs font-bold text-base-content/60 flex items-center gap-1">
                      <ZoomIn size={12} />
                    </span>
                  </div>
                  <button
                    onClick={() => {
                      setZoom(1);
                      setImageOffset({ x: 0, y: 0 });
                    }}
                    className="btn btn-xs bg-base-200 hover:bg-base-300 text-base-content border-none rounded-lg w-full font-bold"
                  >
                    Reset Alignment
                  </button>
                </div>
              )}

              {/* FILTERS MODE */}
              {activeTab === 'filter' && (
                <div className="grid grid-cols-4 sm:grid-cols-7 gap-2">
                  {FILTER_PRESETS.map((filter) => (
                    <button
                      key={filter.value}
                      onClick={() => setSelectedFilter(filter.value)}
                      className={`flex flex-col items-center p-1.5 rounded-xl border-2 transition-all cursor-pointer ${
                        selectedFilter === filter.value
                          ? 'border-primary bg-primary/5 text-primary scale-102'
                          : 'border-transparent text-base-content/75 hover:bg-base-200/50'
                      }`}
                    >
                      <div
                        className="w-11 h-11 rounded-lg bg-base-300 overflow-hidden relative"
                        style={{ filter: filter.style }}
                      >
                        <img
                          src={imageSrc}
                          className="w-full h-full object-cover"
                          alt=""
                        />
                      </div>
                      <span className="text-[9px] font-bold truncate w-full text-center mt-1">
                        {filter.name}
                      </span>
                    </button>
                  ))}
                </div>
              )}

              {/* STICKERS MODE */}
              {activeTab === 'stickers' && (
                <div className="space-y-3 pb-2">
                  {/* Sticker controls (shown when a sticker is active) */}
                  {activeSticker ? (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      className="border border-base-300 rounded-2xl p-3 bg-base-200/50 space-y-3"
                    >
                      <div className="flex items-center justify-between border-b border-base-300/40 pb-2">
                        <span className="text-[10px] font-black uppercase tracking-wider text-base-content/50">
                          Modify Selected Sticker
                        </span>
                        <button
                          onClick={deleteActiveSticker}
                          className="btn btn-ghost btn-circle btn-xs text-error hover:bg-error/10"
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>

                      {/* Scale Slider */}
                      <div className="flex items-center gap-3">
                        <span className="text-[10px] font-bold text-base-content/60 w-12 uppercase">
                          Scale
                        </span>
                        <input
                          type="range"
                          min="0.3"
                          max="2.5"
                          step="0.05"
                          value={activeSticker.scale}
                          onChange={(e) =>
                            updateSticker({ scale: parseFloat(e.target.value) })
                          }
                          className="range range-xs range-primary flex-1"
                        />
                      </div>

                      {/* Rotate Slider */}
                      <div className="flex items-center gap-3">
                        <span className="text-[10px] font-bold text-base-content/60 w-12 uppercase">
                          Rotate
                        </span>
                        <input
                          type="range"
                          min="-180"
                          max="180"
                          step="5"
                          value={activeSticker.rotation}
                          onChange={(e) =>
                            updateSticker({
                              rotation: parseInt(e.target.value),
                            })
                          }
                          className="range range-xs range-primary flex-1"
                        />
                      </div>
                    </motion.div>
                  ) : null}

                  {/* Stickers picker grid */}
                  <div>
                    <p className="text-[10px] font-black uppercase tracking-wider text-base-content/50 mb-1.5">
                      Add a Sticker
                    </p>
                    <div className="grid grid-cols-5 sm:grid-cols-6 gap-2 border border-base-300/30 rounded-2xl p-2 bg-base-200/30">
                      {GOVLYX_EMOJIS.map((url, i) => (
                        <button
                          key={i}
                          type="button"
                          onClick={() => addSticker(url)}
                          className="p-1.5 hover:bg-base-200/80 rounded-xl transition-all aspect-square flex items-center justify-center select-none hover:scale-110 active:scale-95 cursor-pointer border border-transparent hover:border-primary/30"
                        >
                          <img
                            src={url}
                            alt="Govlyx Emoji"
                            loading="lazy"
                            className="w-10 h-10 object-contain drop-shadow-sm"
                          />
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Bottom Actions */}
            <div className="p-3 sm:p-4 border-t border-base-300 flex gap-2.5 sm:gap-3">
              <button
                onClick={onClose}
                disabled={processing}
                className="btn btn-xs sm:btn-sm flex-1 bg-base-200 hover:bg-base-300 text-base-content border-none rounded-xl h-8.5 sm:h-10 font-bold text-xs"
              >
                Cancel
              </button>
              <button
                onClick={handleApply}
                disabled={processing}
                className="btn btn-xs sm:btn-sm flex-1 bg-[#1D4ED8] hover:bg-blue-800 text-white border-none rounded-xl h-8.5 sm:h-10 font-black text-xs shadow-lg shadow-primary/20"
              >
                {processing ? (
                  <RefreshCw size={13} className="animate-spin" />
                ) : (
                  <Check size={13} />
                )}
                {processing ? 'Processing…' : 'Apply & Save'}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
