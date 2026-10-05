import {
  X,
  Plus,
  BarChart2,
  FileTypeCorner,
  AlertTriangle,
  ImagePlus,
  Paperclip,
  Loader2,
  CheckCircle2,
  WifiOff,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Crop,
  Scissors,
  Palette,
  Undo,
  Check,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { useState, useRef, useEffect, type JSX } from 'react';
import { createPortal } from 'react-dom';
import { useCurrentUser } from '../../hooks/useUser';
import { useCreatePost, useCreatePoll } from '../../hooks/usePostInteractions';
import { MdLocationOn } from 'react-icons/md';
import {
  RiAttachment2,
  RiBold,
  RiItalic,
  RiCodeLine,
  RiHashtag,
  RiAtLine,
  RiEmotionHappyLine,
} from 'react-icons/ri';

import { apiUrl } from '../../utils/apiUrl';
import { showToast } from '../../utils/toast';
import { getAuthToken } from '../../utils/auth';
import { vaultService } from '../../services/vaultService';

const authHeaders = (): HeadersInit => {
  const token = getAuthToken();
  const actorToken = vaultService.getCachedActorToken();
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (actorToken) headers['X-Actor-Token'] = actorToken;
  return headers;
};

const generateUUID = (): string => {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

// ─── TYPES ────────────────────────────────────────────────────────────────────
type PostType = 'post' | 'poll';
type Props = {
  open: boolean;
  onClose: () => void;
  communityId?: number;
  communityName?: string;
  onPostCreated?: (post: any) => void;
};

interface ApiResult {
  ok: boolean;
  message?: string;
  data?: any; // the created post/poll from backend (or duplicate post data on 409)
  status?: number;
}

// ─── JACCARD SIMILARITY MATCHING (FRONTEND DUPLICATE CHECK) ───────────────────
const STOP_WORDS = new Set([
  // English
  'a',
  'an',
  'and',
  'are',
  'as',
  'at',
  'be',
  'but',
  'by',
  'for',
  'if',
  'in',
  'into',
  'is',
  'it',
  'no',
  'not',
  'of',
  'on',
  'or',
  'such',
  'that',
  'the',
  'their',
  'then',
  'there',
  'these',
  'they',
  'this',
  'to',
  'was',
  'will',
  'with',
  'please',
  'fix',
  'issue',
  'problem',
  'resolve',
  'help',
  'near',
  'outside',
  'behind',
  'front',
  'very',
  'too',
  'much',
  // Hindi / Hinglish
  'hai',
  'ki',
  'ka',
  'ke',
  'ko',
  'se',
  'mein',
  'par',
  'karo',
  'kijiye',
  'bhi',
  'toh',
  'hi',
  'aur',
  'ya',
  'ye',
  'wo',
  'kya',
  'kab',
  'kaise',
  'idhar',
  'udhar',
  'yahan',
  'wahan',
  'sir',
  'madam',
  'ji',
]);

function tokenizeAndClean(text: string): Set<string> {
  if (!text || !text.trim()) return new Set();
  const words = text.toLowerCase().split(/\W+/);
  const resultSet = new Set<string>();
  for (const word of words) {
    if (word.length > 2 && !STOP_WORDS.has(word)) {
      resultSet.add(word);
    }
  }
  return resultSet;
}

function calculateSimilarity(text1: string, text2: string): number {
  if (!text1 || !text2) return 0.0;
  if (text1.toLowerCase() === text2.toLowerCase()) return 1.0;

  const set1 = tokenizeAndClean(text1);
  const set2 = tokenizeAndClean(text2);

  if (set1.size === 0 && set2.size === 0) return 1.0;
  if (set1.size === 0 || set2.size === 0) return 0.0;

  let intersectionSize = 0;
  for (const item of set1) {
    if (set2.has(item)) {
      intersectionSize++;
    }
  }

  const unionSize = set1.size + set2.size - intersectionSize;
  return intersectionSize / unionSize;
}

/**
 * Fetches recent active posts — GET /api/posts/active (not cached in backend)
 */
async function apiGetActivePosts(limit: number = 40): Promise<ApiResult> {
  const res = await fetch(apiUrl(`/api/posts/active?limit=${limit}`), {
    method: 'GET',
    headers: { ...authHeaders() },
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    return {
      ok: false,
      status: res.status,
      message: json?.message ?? `HTTP ${res.status}`,
    };
  }
  return { ok: true, data: json?.data?.data ?? [], status: res.status };
}

// ─── API CALLS ────────────────────────────────────────────────────────────────

// ─── MEDIA CAROUSEL (Sliding previews, edit/replace on click, custom editor modal) ──
function MediaCarousel({
  files,
  onChange,
}: {
  files: File[];
  onChange: (files: File[]) => void;
}) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [previews, setPreviews] = useState<string[]>([]);

  // Editor Modal States
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [editMode, setEditMode] = useState<'none' | 'crop' | 'filter' | 'draw'>(
    'none',
  );
  const [activeFilter, setActiveFilter] = useState<string>('none');
  const [filterDropdownOpen, setFilterDropdownOpen] = useState(false);
  const [drawColor, setDrawColor] = useState<string>('#1D4ED8');
  const [brushSize, setBrushSize] = useState<number>(5);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawingCanvasRef = useRef<HTMLCanvasElement>(null);
  const isDrawingRef = useRef<boolean>(false);
  const originalImageRef = useRef<HTMLImageElement | null>(null);

  // Crop selection coordinates
  const [cropBox, setCropBox] = useState({ x: 10, y: 10, w: 80, h: 80 }); // Percentage based
  const isResizingCropRef = useRef<string | null>(null);
  const dragStartRef = useRef({
    x: 0,
    y: 0,
    boxX: 0,
    boxY: 0,
    boxW: 0,
    boxH: 0,
  });

  useEffect(() => {
    const urls = files.map((f) => URL.createObjectURL(f));
    setPreviews(urls);
    return () => {
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [files]);

  useEffect(() => {
    if (activeIndex >= files.length) {
      setActiveIndex(Math.max(0, files.length - 1));
    }
  }, [files, activeIndex]);

  // Load image to edit
  useEffect(() => {
    if (editingIndex === null || editingIndex >= previews.length) return;
    const img = new Image();
    img.src = previews[editingIndex];
    img.onload = () => {
      originalImageRef.current = img;
      resetCanvas(img);
    };
  }, [editingIndex, previews]);

  const resetCanvas = (img: HTMLImageElement) => {
    const canvas = canvasRef.current;
    const drawCanvas = drawingCanvasRef.current;
    if (!canvas || !drawCanvas) return;

    // Constrain canvas dimensions to reasonable screen size
    const isMobile = window.innerWidth < 640;
    const maxW = Math.min(window.innerWidth - (isMobile ? 24 : 80), 500);
    const maxH = Math.min(window.innerHeight - (isMobile ? 240 : 340), 450);
    let w = img.naturalWidth || img.width || 300;
    let h = img.naturalHeight || img.height || 300;

    const scale = Math.min(maxW / w, maxH / h);
    w = Math.round(w * scale);
    h = Math.round(h * scale);

    canvas.width = w;
    canvas.height = h;
    drawCanvas.width = w;
    drawCanvas.height = h;

    const ctx = canvas.getContext('2d');
    const dCtx = drawCanvas.getContext('2d');
    if (ctx && dCtx) {
      ctx.drawImage(img, 0, 0, w, h);
      dCtx.clearRect(0, 0, w, h);
    }
    setActiveFilter('none');
    setEditMode('none');
  };

  if (files.length === 0) return null;

  const currentFile = files[activeIndex];
  const currentPreview = previews[activeIndex];
  if (!currentFile) return null;

  const isImage = currentFile.type.startsWith('image/');
  const isVideo = currentFile.type.startsWith('video/');

  const handlePrev = (e: React.MouseEvent) => {
    e.stopPropagation();
    setActiveIndex((prev) => (prev === 0 ? files.length - 1 : prev - 1));
  };

  const handleNext = (e: React.MouseEvent) => {
    e.stopPropagation();
    setActiveIndex((prev) => (prev === files.length - 1 ? 0 : prev + 1));
  };

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = files.filter((_, idx) => idx !== activeIndex);
    onChange(updated);
  };

  const handleMediaClick = () => {
    if (isImage) {
      setEditingIndex(activeIndex);
    }
  };

  // Drawing Canvas Handlers
  const startDrawing = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (editMode !== 'draw') return;
    const canvas = drawingCanvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.strokeStyle = drawColor;
    ctx.lineWidth = brushSize;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(x, y);
    isDrawingRef.current = true;
  };

  const draw = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawingRef.current || editMode !== 'draw') return;
    const canvas = drawingCanvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const stopDrawing = () => {
    isDrawingRef.current = false;
  };

  // Filter application
  const applyFilter = (filterName: string) => {
    setActiveFilter(filterName);
    const canvas = canvasRef.current;
    const img = originalImageRef.current;
    if (!canvas || !img) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const data = imgData.data;

    for (let i = 0; i < data.length; i += 4) {
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];

      if (filterName === 'chrome') {
        data[i] = Math.min(255, r * 1.2);
        data[i + 1] = Math.min(255, g * 0.9);
        data[i + 2] = Math.min(255, b * 0.8);
      } else if (filterName === 'mono') {
        const grey = 0.299 * r + 0.587 * g + 0.114 * b;
        data[i] = grey;
        data[i + 1] = grey;
        data[i + 2] = grey;
      } else if (filterName === 'bw') {
        const grey = 0.299 * r + 0.587 * g + 0.114 * b;
        const bw = grey > 128 ? 255 : 0;
        data[i] = bw;
        data[i + 1] = bw;
        data[i + 2] = bw;
      } else if (filterName === 'fade') {
        data[i] = Math.min(255, r * 0.9 + 30);
        data[i + 1] = Math.min(255, g * 0.9 + 30);
        data[i + 2] = Math.min(255, b * 0.9 + 30);
      } else if (filterName === 'warm') {
        data[i] = Math.min(255, r + 40);
        data[i + 2] = Math.max(0, b - 20);
      } else if (filterName === 'cool') {
        data[i + 2] = Math.min(255, b + 40);
        data[i] = Math.max(0, r - 20);
      }
    }
    ctx.putImageData(imgData, 0, 0);
  };

  // Crop Interaction Handlers
  const handleCropMouseDown = (e: React.MouseEvent, handle: string) => {
    e.stopPropagation();
    const rect = e.currentTarget.parentElement?.getBoundingClientRect();
    if (!rect) return;
    isResizingCropRef.current = handle;
    dragStartRef.current = {
      x: e.clientX,
      y: e.clientY,
      boxX: cropBox.x,
      boxY: cropBox.y,
      boxW: cropBox.w,
      boxH: cropBox.h,
    };
  };

  const handleCropMouseMove = (e: React.MouseEvent) => {
    if (!isResizingCropRef.current) return;
    const container = e.currentTarget.getBoundingClientRect();
    const dx = ((e.clientX - dragStartRef.current.x) / container.width) * 100;
    const dy = ((e.clientY - dragStartRef.current.y) / container.height) * 100;

    const start = dragStartRef.current;
    if (isResizingCropRef.current === 'move') {
      const newX = Math.max(0, Math.min(100 - start.boxW, start.boxX + dx));
      const newY = Math.max(0, Math.min(100 - start.boxH, start.boxY + dy));
      setCropBox((prev) => ({ ...prev, x: newX, y: newY }));
    } else {
      let w = start.boxW;
      let h = start.boxH;
      let x = start.boxX;
      let y = start.boxY;

      if (isResizingCropRef.current.includes('r'))
        w = Math.max(20, Math.min(100 - x, start.boxW + dx));
      if (isResizingCropRef.current.includes('b'))
        h = Math.max(20, Math.min(100 - y, start.boxH + dy));
      if (isResizingCropRef.current.includes('l')) {
        const potentialW = start.boxW - dx;
        if (potentialW >= 20) {
          x = Math.max(0, start.boxX + dx);
          w = start.boxW + (start.boxX - x);
        }
      }
      if (isResizingCropRef.current.includes('t')) {
        const potentialH = start.boxH - dy;
        if (potentialH >= 20) {
          y = Math.max(0, start.boxY + dy);
          h = start.boxH + (start.boxY - y);
        }
      }
      setCropBox({ x, y, w, h });
    }
  };

  const handleCropMouseUp = () => {
    isResizingCropRef.current = null;
  };

  // Crop Touch Handlers for Mobile Devices
  const handleCropTouchStart = (e: React.TouchEvent, handle: string) => {
    e.stopPropagation();
    const touch = e.touches[0];
    const rect = e.currentTarget.parentElement?.getBoundingClientRect();
    if (!rect) return;
    isResizingCropRef.current = handle;
    dragStartRef.current = {
      x: touch.clientX,
      y: touch.clientY,
      boxX: cropBox.x,
      boxY: cropBox.y,
      boxW: cropBox.w,
      boxH: cropBox.h,
    };
  };

  const handleCropTouchMove = (e: React.TouchEvent) => {
    if (!isResizingCropRef.current) return;
    const touch = e.touches[0];
    const container = e.currentTarget.getBoundingClientRect();
    const dx =
      ((touch.clientX - dragStartRef.current.x) / container.width) * 100;
    const dy =
      ((touch.clientY - dragStartRef.current.y) / container.height) * 100;

    const start = dragStartRef.current;
    if (isResizingCropRef.current === 'move') {
      const newX = Math.max(0, Math.min(100 - start.boxW, start.boxX + dx));
      const newY = Math.max(0, Math.min(100 - start.boxH, start.boxY + dy));
      setCropBox((prev) => ({ ...prev, x: newX, y: newY }));
    } else {
      let w = start.boxW;
      let h = start.boxH;
      let x = start.boxX;
      let y = start.boxY;

      if (isResizingCropRef.current.includes('r'))
        w = Math.max(20, Math.min(100 - x, start.boxW + dx));
      if (isResizingCropRef.current.includes('b'))
        h = Math.max(20, Math.min(100 - y, start.boxH + dy));
      if (isResizingCropRef.current.includes('l')) {
        const potentialW = start.boxW - dx;
        if (potentialW >= 20) {
          x = Math.max(0, start.boxX + dx);
          w = start.boxW + (start.boxX - x);
        }
      }
      if (isResizingCropRef.current.includes('t')) {
        const potentialH = start.boxH - dy;
        if (potentialH >= 20) {
          y = Math.max(0, start.boxY + dy);
          h = start.boxH + (start.boxY - y);
        }
      }
      setCropBox({ x, y, w, h });
    }
  };

  // Drawing Touch Handlers for Mobile Devices
  const startDrawingTouch = (e: React.TouchEvent<HTMLCanvasElement>) => {
    if (editMode !== 'draw') return;
    const touch = e.touches[0];
    const canvas = drawingCanvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.strokeStyle = drawColor;
    ctx.lineWidth = brushSize;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(x, y);
    isDrawingRef.current = true;
  };

  const drawTouch = (e: React.TouchEvent<HTMLCanvasElement>) => {
    if (!isDrawingRef.current || editMode !== 'draw') return;
    const touch = e.touches[0];
    const canvas = drawingCanvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const performCrop = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const px = (cropBox.x / 100) * canvas.width;
    const py = (cropBox.y / 100) * canvas.height;
    const pw = (cropBox.w / 100) * canvas.width;
    const ph = (cropBox.h / 100) * canvas.height;

    const croppedData = ctx.getImageData(px, py, pw, ph);

    canvas.width = pw;
    canvas.height = ph;
    ctx.putImageData(croppedData, 0, 0);

    const drawCanvas = drawingCanvasRef.current;
    if (drawCanvas) {
      drawCanvas.width = pw;
      drawCanvas.height = ph;
      drawCanvas.getContext('2d')?.clearRect(0, 0, pw, ph);
    }

    // Set cropped image as the new baseline so filters and reset work on the cropped result
    const croppedUrl = canvas.toDataURL('image/jpeg');
    const croppedImg = new Image();
    croppedImg.src = croppedUrl;
    croppedImg.onload = () => {
      originalImageRef.current = croppedImg;
    };

    setEditMode('none');
    setCropBox({ x: 10, y: 10, w: 80, h: 80 });
  };

  const saveEditedImage = () => {
    const canvas = canvasRef.current;
    const drawCanvas = drawingCanvasRef.current;
    if (!canvas || !drawCanvas || editingIndex === null) return;

    // Merge drawing into image canvas
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.drawImage(drawCanvas, 0, 0);
    }

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        const editedFile = new File([blob], files[editingIndex].name, {
          type: 'image/jpeg',
        });
        const updatedFiles = [...files];
        updatedFiles[editingIndex] = editedFile;
        onChange(updatedFiles);
        setEditingIndex(null);
      },
      'image/jpeg',
      0.9,
    );
  };

  const editorModal =
    editingIndex !== null
      ? createPortal(
          <div
            className="fixed inset-0 z-[10000] flex items-center justify-center p-2 sm:p-4 bg-black/60 dark:bg-black/80 backdrop-blur-md text-base-content dark:text-white"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="w-full max-w-sm sm:max-w-xl md:max-w-3xl bg-base-100/95 dark:bg-neutral-900 border border-black/10 dark:border-white/15 rounded-2xl sm:rounded-3xl shadow-[0_24px_48px_-12px_rgba(0,0,0,0.4)] dark:shadow-[0_32px_64px_-16px_rgba(0,0,0,0.6)] flex flex-col overflow-hidden h-[540px] max-h-[82dvh] sm:h-[580px] sm:max-h-[85vh] backdrop-blur-2xl">
              {/* Header */}
              <div className="flex items-center justify-between px-3.5 sm:px-5 py-2.5 sm:py-3.5 border-b border-base-300 dark:border-white/10 select-none bg-base-100 dark:bg-neutral-900 shrink-0">
                <span className="text-xs font-black uppercase tracking-widest flex items-center gap-2 text-base-content dark:text-white">
                  <Palette size={14} className="text-[#1D4ED8]" /> Edit Image
                </span>
                <button
                  onClick={() => setEditingIndex(null)}
                  className="btn btn-ghost btn-xs btn-circle bg-base-300/60 dark:bg-white/10 hover:bg-base-300 dark:hover:bg-white/20 text-base-content dark:text-white cursor-pointer"
                >
                  <X size={16} />
                </button>
              </div>

              {/* Content Body */}
              <div className="flex-1 min-h-0 overflow-y-auto p-3 sm:p-4 flex flex-col items-center gap-3">
                {/* Canvas Workspace Area */}
                <div className="w-full flex items-center justify-center relative p-1 overflow-hidden min-h-[160px] sm:min-h-[200px]">
                  <div className="relative border border-base-300 dark:border-white/10 bg-base-200/50 dark:bg-black/40 rounded-xl overflow-hidden shadow-inner flex items-center justify-center">
                    <canvas
                      ref={canvasRef}
                      className="block select-none max-w-full max-h-[240px] sm:max-h-[340px] object-contain"
                    />
                    <canvas
                      ref={drawingCanvasRef}
                      onMouseDown={startDrawing}
                      onMouseMove={draw}
                      onMouseUp={stopDrawing}
                      onMouseLeave={stopDrawing}
                      onTouchStart={startDrawingTouch}
                      onTouchMove={drawTouch}
                      onTouchEnd={stopDrawing}
                      className={`absolute inset-0 block select-none ${editMode === 'draw' ? 'cursor-crosshair pointer-events-auto' : 'pointer-events-none'}`}
                    />

                    {/* Interactive Crop Grid Overlay */}
                    {editMode === 'crop' && (
                      <div
                        onMouseMove={handleCropMouseMove}
                        onMouseUp={handleCropMouseUp}
                        onMouseLeave={handleCropMouseUp}
                        onTouchMove={handleCropTouchMove}
                        onTouchEnd={handleCropMouseUp}
                        className="absolute inset-0 select-none cursor-default"
                      >
                        <div
                          style={{
                            left: `${cropBox.x}%`,
                            top: `${cropBox.y}%`,
                            width: `${cropBox.w}%`,
                            height: `${cropBox.h}%`,
                          }}
                          className="absolute border-2 border-dashed border-[#1D4ED8] bg-black/20 shadow-[0_0_0_9999px_rgba(0,0,0,0.5)]"
                        >
                          {/* Draggable central handle */}
                          <div
                            onMouseDown={(e) => handleCropMouseDown(e, 'move')}
                            onTouchStart={(e) =>
                              handleCropTouchStart(e, 'move')
                            }
                            className="absolute inset-0 cursor-move"
                          />

                          {/* Resize handles */}
                          <div
                            onMouseDown={(e) => handleCropMouseDown(e, 'tl')}
                            onTouchStart={(e) => handleCropTouchStart(e, 'tl')}
                            className="absolute -top-1 -left-1 w-3.5 h-3.5 bg-white border border-[#1D4ED8] rounded-full cursor-nwse-resize"
                          />
                          <div
                            onMouseDown={(e) => handleCropMouseDown(e, 'tr')}
                            onTouchStart={(e) => handleCropTouchStart(e, 'tr')}
                            className="absolute -top-1 -right-1 w-3.5 h-3.5 bg-white border border-[#1D4ED8] rounded-full cursor-nesw-resize"
                          />
                          <div
                            onMouseDown={(e) => handleCropMouseDown(e, 'bl')}
                            onTouchStart={(e) => handleCropTouchStart(e, 'bl')}
                            className="absolute -bottom-1 -left-1 w-3.5 h-3.5 bg-white border border-[#1D4ED8] rounded-full cursor-nesw-resize"
                          />
                          <div
                            onMouseDown={(e) => handleCropMouseDown(e, 'br')}
                            onTouchStart={(e) => handleCropTouchStart(e, 'br')}
                            className="absolute -bottom-1 -right-1 w-3.5 h-3.5 bg-white border border-[#1D4ED8] rounded-full cursor-nwse-resize"
                          />
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* Sub-modes controller toolbars */}
                <div className="bg-base-200/70 dark:bg-neutral-800/80 border border-base-300 dark:border-white/10 rounded-2xl p-3 flex flex-col gap-3 w-full select-none">
                  {editMode === 'crop' && (
                    <div className="flex items-center justify-between gap-2 select-none">
                      <span className="text-[9px] sm:text-[10px] uppercase font-black tracking-wider text-base-content/60 dark:text-white/50 truncate">
                        Crop Selection
                      </span>
                      <div className="flex gap-1.5">
                        <button
                          onClick={() => setEditMode('none')}
                          className="btn btn-ghost btn-xs text-base-content dark:text-white text-[10px] h-7 min-h-0 px-2 rounded-lg cursor-pointer"
                        >
                          Cancel
                        </button>
                        <button
                          onClick={performCrop}
                          className="btn btn-xs bg-[#1D4ED8] hover:bg-blue-800 text-white rounded-lg px-2.5 h-7 min-h-0 text-[10px] flex gap-1 items-center font-bold cursor-pointer"
                        >
                          <Check size={10} /> Apply
                        </button>
                      </div>
                    </div>
                  )}

                  {editMode === 'draw' && (
                    <div className="flex flex-col gap-2">
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] uppercase font-black tracking-wider text-base-content/60 dark:text-white/50">
                          Brush Options
                        </span>
                        <button
                          onClick={() =>
                            drawingCanvasRef.current
                              ?.getContext('2d')
                              ?.clearRect(
                                0,
                                0,
                                drawingCanvasRef.current.width,
                                drawingCanvasRef.current.height,
                              )
                          }
                          className="btn btn-ghost btn-xs text-red-500 dark:text-red-400 hover:text-red-600 dark:hover:text-red-300 cursor-pointer"
                        >
                          Clear Draw
                        </button>
                      </div>
                      <div className="flex items-center justify-between gap-3 flex-wrap">
                        <div className="flex gap-1.5">
                          {[
                            '#1D4ED8',
                            '#EF4444',
                            '#10B981',
                            '#F59E0B',
                            '#FFFFFF',
                            '#000000',
                          ].map((c) => (
                            <button
                              key={c}
                              onClick={() => setDrawColor(c)}
                              style={{ backgroundColor: c }}
                              className={`w-6 h-6 rounded-full border-2 transition-all cursor-pointer ${drawColor === c ? 'border-[#1D4ED8] scale-110 shadow-xs' : 'border-base-300 dark:border-transparent'}`}
                            />
                          ))}
                        </div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-[9px] uppercase font-bold text-base-content/50 dark:text-white/40">
                            Size
                          </span>
                          <input
                            type="range"
                            min="2"
                            max="15"
                            value={brushSize}
                            onChange={(e) =>
                              setBrushSize(parseInt(e.target.value))
                            }
                            className="range range-xs w-20 accent-[#1D4ED8]"
                          />
                        </div>
                      </div>
                    </div>
                  )}

                  {editMode === 'filter' && (
                    <div className="flex flex-col gap-1.5 relative">
                      <span className="text-[10px] uppercase font-black tracking-wider text-base-content/60 dark:text-white/50">
                        Choose Filter
                      </span>

                      <div className="relative">
                        <button
                          type="button"
                          onClick={() =>
                            setFilterDropdownOpen(!filterDropdownOpen)
                          }
                          className="w-full flex items-center justify-between px-3.5 py-2 rounded-xl bg-base-100 dark:bg-neutral-900 border border-base-300 dark:border-white/10 hover:border-base-400 dark:hover:border-white/20 text-base-content dark:text-white font-bold text-xs cursor-pointer transition-all"
                        >
                          <span>
                            {activeFilter === 'none' && 'Normal'}
                            {activeFilter === 'chrome' && 'Vibrant'}
                            {activeFilter === 'mono' && 'Grayscale'}
                            {activeFilter === 'bw' && 'B&W (High Contrast)'}
                            {activeFilter === 'fade' && 'Fade'}
                            {activeFilter === 'warm' && 'Warm'}
                            {activeFilter === 'cool' && 'Cool'}
                          </span>
                          <ChevronDown
                            size={14}
                            className={`transition-transform duration-200 text-base-content/50 dark:text-white/60 ${filterDropdownOpen ? 'rotate-180' : ''}`}
                          />
                        </button>

                        {filterDropdownOpen && (
                          <>
                            <div
                              className="fixed inset-0 z-40"
                              onClick={() => setFilterDropdownOpen(false)}
                            />
                            <div className="absolute bottom-full mb-2 left-0 right-0 z-50 bg-base-100 dark:bg-neutral-900 border border-base-300 dark:border-white/15 rounded-xl shadow-2xl p-1.5 flex flex-col gap-0.5 max-h-48 overflow-y-auto backdrop-blur-xl">
                              {[
                                { value: 'none', label: 'Normal' },
                                { value: 'chrome', label: 'Vibrant' },
                                { value: 'mono', label: 'Grayscale' },
                                { value: 'bw', label: 'B&W (High Contrast)' },
                                { value: 'fade', label: 'Fade' },
                                { value: 'warm', label: 'Warm' },
                                { value: 'cool', label: 'Cool' },
                              ].map((f) => (
                                <button
                                  key={f.value}
                                  type="button"
                                  onClick={() => {
                                    applyFilter(f.value);
                                    setFilterDropdownOpen(false);
                                  }}
                                  className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-bold text-left transition-colors duration-150 cursor-pointer ${
                                    activeFilter === f.value
                                      ? 'bg-[#1D4ED8] text-white shadow-xs'
                                      : 'text-base-content dark:text-white/80 hover:bg-[#1D4ED8] hover:text-white'
                                  }`}
                                >
                                  <span>{f.label}</span>
                                  {activeFilter === f.value && (
                                    <Check
                                      size={13}
                                      className="text-white shrink-0"
                                    />
                                  )}
                                </button>
                              ))}
                            </div>
                          </>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Editor Action Bar Mode Selectors */}
                  <div className="flex items-center justify-around border-t border-base-300 dark:border-white/10 pt-2.5 gap-1">
                    <button
                      onClick={() =>
                        setEditMode(editMode === 'crop' ? 'none' : 'crop')
                      }
                      className={`flex flex-col items-center justify-center gap-1 py-1.5 px-3 rounded-xl text-[9px] font-black uppercase tracking-wider cursor-pointer transition-all duration-200 ${
                        editMode === 'crop'
                          ? 'bg-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                          : 'text-base-content/70 dark:text-white/60 hover:text-base-content dark:hover:text-white hover:bg-base-300/40 dark:hover:bg-white/5'
                      }`}
                    >
                      <Crop size={14} /> Crop
                    </button>
                    <button
                      onClick={() =>
                        setEditMode(editMode === 'draw' ? 'none' : 'draw')
                      }
                      className={`flex flex-col items-center justify-center gap-1 py-1.5 px-3 rounded-xl text-[9px] font-black uppercase tracking-wider cursor-pointer transition-all duration-200 ${
                        editMode === 'draw'
                          ? 'bg-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                          : 'text-base-content/70 dark:text-white/60 hover:text-base-content dark:hover:text-white hover:bg-base-300/40 dark:hover:bg-white/5'
                      }`}
                    >
                      <Palette size={14} /> Draw
                    </button>
                    <button
                      onClick={() =>
                        setEditMode(editMode === 'filter' ? 'none' : 'filter')
                      }
                      className={`flex flex-col items-center justify-center gap-1 py-1.5 px-3 rounded-xl text-[9px] font-black uppercase tracking-wider cursor-pointer transition-all duration-200 ${
                        editMode === 'filter'
                          ? 'bg-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                          : 'text-base-content/70 dark:text-white/60 hover:text-base-content dark:hover:text-white hover:bg-base-300/40 dark:hover:bg-white/5'
                      }`}
                    >
                      <Scissors size={14} /> Filters
                    </button>
                    <button
                      onClick={() =>
                        originalImageRef.current &&
                        resetCanvas(originalImageRef.current)
                      }
                      className="flex flex-col items-center justify-center gap-1 py-1.5 px-3 rounded-xl text-[9px] font-black uppercase tracking-wider text-red-500 dark:text-red-400 hover:text-red-600 dark:hover:text-red-300 hover:bg-red-500/10 cursor-pointer transition-all duration-200"
                    >
                      <Undo size={14} /> Reset
                    </button>
                  </div>
                </div>
              </div>

              {/* Footer Actions */}
              <div className="flex items-center justify-end gap-2 px-3.5 sm:px-5 py-2.5 sm:py-3 border-t border-base-300 dark:border-white/10 bg-base-100 dark:bg-neutral-900 shrink-0 select-none">
                <button
                  onClick={() => setEditingIndex(null)}
                  className="btn btn-xs sm:btn-sm btn-ghost text-base-content/70 dark:text-white/70 hover:text-base-content dark:hover:text-white rounded-lg sm:rounded-xl px-3 sm:px-4 font-bold text-[11px] sm:text-xs uppercase tracking-wider cursor-pointer h-8 sm:h-9"
                >
                  Cancel
                </button>
                <button
                  onClick={saveEditedImage}
                  className="btn btn-xs sm:btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white rounded-lg sm:rounded-xl px-4 sm:px-5 shadow-lg shadow-[#1D4ED8]/25 flex gap-1.5 items-center font-bold text-[10px] sm:text-[11px] uppercase tracking-wider cursor-pointer h-8 sm:h-9"
                >
                  <Check size={13} /> Save Edits
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )
      : null;

  return (
    <div className="relative w-full sm:h-[220px] h-[170px] rounded-xl sm:rounded-2xl overflow-hidden border border-base-content/10 bg-base-200/50 shadow-md group/carousel">
      <div
        className="w-full h-full cursor-pointer relative flex items-center justify-center bg-black/5"
        onClick={handleMediaClick}
        title="Click to edit/draw/crop image"
      >
        {isImage && currentPreview && (
          <img
            src={currentPreview}
            alt="Preview"
            className="w-full h-full object-cover select-none"
          />
        )}
        {isVideo && currentPreview && (
          <video
            src={currentPreview}
            className="w-full h-full object-cover select-none"
            controls={false}
            muted
            playsInline
            autoPlay
            loop
          />
        )}
        {!isImage && !isVideo && (
          <div className="flex flex-col items-center justify-center p-3 sm:p-4 text-center">
            <Paperclip
              size={24}
              className="opacity-40 mb-1.5 sm:mb-2 sm:size-8"
            />
            <span className="text-[11px] sm:text-xs font-bold opacity-70 truncate max-w-[80%]">
              {currentFile.name}
            </span>
          </div>
        )}

        {isImage && (
          <div className="absolute inset-0 bg-black/45 opacity-0 hover:opacity-100 transition-opacity duration-200 flex flex-col items-center justify-center gap-1.5 text-white">
            <span className="text-[10px] sm:text-xs font-black uppercase tracking-widest bg-[#1D4ED8] text-white px-2.5 sm:px-3 py-1 sm:py-1.5 rounded-full select-none notranslate shadow-lg">
              Click to Edit (Crop/Draw/Filter)
            </span>
          </div>
        )}
      </div>

      {isImage && (
        <button
          type="button"
          onClick={handleMediaClick}
          className="absolute bottom-2 left-2 sm:bottom-2.5 sm:left-2.5 z-10 flex items-center gap-1 bg-[#1D4ED8] hover:bg-blue-800 border border-blue-400/40 text-white shadow-[0_0_12px_rgba(29,78,216,0.5)] px-2 sm:px-2.5 py-0.5 rounded-md sm:rounded-lg text-[8.5px] sm:text-[9px] font-black uppercase tracking-wider select-none notranslate cursor-pointer transition-all duration-200"
        >
          <Palette size={10} /> Edit
        </button>
      )}

      {files.length > 1 && (
        <>
          <button
            type="button"
            onClick={handlePrev}
            className="absolute left-2 sm:left-2.5 top-1/2 -translate-y-1/2 w-6 h-6 sm:w-8 sm:h-8 rounded-full bg-black/60 hover:bg-black/80 backdrop-blur-md text-white flex items-center justify-center transition-all shadow-md z-10 border border-white/5 cursor-pointer"
          >
            <ChevronLeft size={14} className="sm:size-4" />
          </button>
          <button
            type="button"
            onClick={handleNext}
            className="absolute right-2 sm:right-2.5 top-1/2 -translate-y-1/2 w-6 h-6 sm:w-8 sm:h-8 rounded-full bg-black/60 hover:bg-black/80 backdrop-blur-md text-white flex items-center justify-center transition-all shadow-md z-10 border border-white/5 cursor-pointer"
          >
            <ChevronRight size={14} className="sm:size-4" />
          </button>
        </>
      )}

      <button
        type="button"
        onClick={handleRemove}
        className="absolute top-2 right-2 sm:top-2.5 sm:right-2.5 w-6 h-6 sm:w-7 sm:h-7 rounded-full bg-black/60 hover:bg-red-650 backdrop-blur-md text-white flex items-center justify-center transition-all shadow-md z-10 border border-white/5 cursor-pointer"
        title="Remove current item"
      >
        <X size={12} className="sm:size-3.5" />
      </button>

      <div className="absolute bottom-2 sm:bottom-2.5 left-1/2 -translate-x-1/2 px-2 sm:px-2.5 py-0.5 rounded-full bg-black/60 backdrop-blur-md text-white text-[8px] sm:text-[9px] font-black uppercase tracking-wider border border-white/5 select-none pointer-events-none">
        {activeIndex + 1} / {files.length}
      </div>

      {editorModal}
    </div>
  );
}

// ─── MEDIA UPLOAD ZONE ────────────────────────────────────────────────────────
function MediaUploadZone({
  accent: _accent = 'blue',
  files,
  onChange,
}: {
  accent?: 'blue' | 'green';
  files: File[];
  onChange: (files: File[]) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const handleFiles = async (incoming: FileList | null) => {
    if (!incoming) return;

    const validFiles: File[] = [];
    let hasTooLongVideo = false;

    for (let i = 0; i < incoming.length; i++) {
      const file = incoming[i];
      if (file.type.startsWith('video/')) {
        try {
          const duration = await new Promise<number>((resolve, reject) => {
            const video = document.createElement('video');
            video.preload = 'metadata';
            video.onloadedmetadata = () => {
              window.URL.revokeObjectURL(video.src);
              resolve(video.duration);
            };
            video.onerror = () => reject();
            video.src = URL.createObjectURL(file);
          });

          if (duration > 300) {
            hasTooLongVideo = true;
            continue;
          }
        } catch (e) {
          console.error('Error reading video duration:', e);
        }
      }
      validFiles.push(file);
    }

    if (hasTooLongVideo) {
      showToast.error(
        'check the file size it must be lesser than equal to 5 min',
      );
    }

    if (validFiles.length > 0) {
      const arr = validFiles.slice(0, 4 - files.length);
      onChange([...files, ...arr].slice(0, 4));
    }
  };

  return (
    <div className="flex flex-col gap-2.5 sm:gap-3">
      {files.length < 4 && (
        <div
          className={`relative flex flex-col items-center justify-center gap-1.5 sm:gap-2 p-3 sm:p-4 rounded-xl sm:rounded-2xl border-2 border-dashed border-base-300 hover:border-[#1D4ED8]/60 bg-base-200/30 hover:bg-base-200/60 cursor-pointer transition-all duration-200 group ${dragging ? 'border-[#1D4ED8] bg-[#1D4ED8]/5' : ''}`}
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            handleFiles(e.dataTransfer.files);
          }}
        >
          <div className="p-1.5 sm:p-2 rounded-lg sm:rounded-xl bg-[#1D4ED8]/10 text-[#1D4ED8] border border-[#1D4ED8]/20 group-hover:scale-105 transition-transform shadow-xs">
            <ImagePlus size={16} className="sm:size-[18px]" />
          </div>

          <div className="text-center">
            <p className="text-[9.5px] sm:text-[10px] font-black uppercase tracking-wider text-base-content">
              Upload Media
            </p>
            <p className="text-[8.5px] sm:text-[9px] font-semibold text-base-content/50 uppercase tracking-tight mt-0.5">
              Images, video (max 5m), or files
            </p>
          </div>

          <input
            ref={inputRef}
            type="file"
            multiple
            accept="image/*,video/*,.pdf,.doc,.docx"
            className="hidden"
            onChange={(e) => handleFiles(e.target.files)}
          />
        </div>
      )}

      <MediaCarousel files={files} onChange={onChange} />
    </div>
  );
}

// ─── STATUS BANNER ─────────────────────────────────────────────────────────────
function StatusBanner({
  status,
  message,
}: {
  status: 'error' | 'success' | 'network';
  message: string;
}) {
  const map = {
    error: {
      bg: 'bg-red-500/10 border-red-500/30 text-red-400',
      icon: <X size={14} />,
    },
    success: {
      bg: 'bg-green-500/10 border-green-500/30 text-green-400',
      icon: <CheckCircle2 size={14} />,
    },
    network: {
      bg: 'bg-yellow-500/10 border-yellow-500/30 text-[#1D4EED]',
      icon: <WifiOff size={14} />,
    },
  };
  const s = map[status];
  return (
    <motion.div
      initial={{ opacity: 0, y: -6 }}
      animate={{ opacity: 1, y: 0 }}
      className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-xs font-medium ${s.bg}`}
    >
      {s.icon}
      <span>{message}</span>
    </motion.div>
  );
}

// ─── POST FORM (Civic issue + regular post, wired to backend) ─────────────────
function PostForm({
  onClose,
  communityId,
  onPostCreated,
}: {
  onClose: () => void;
  communityId?: number;
  onPostCreated?: (post: any) => void;
}) {
  const { data: currentUser } = useCurrentUser();
  const createPostMutation = useCreatePost();

  const [content, setContent] = useState('');
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [targetPincode, setTargetPincode] = useState('');
  const [isReportingIssue, setIsReportingIssue] = useState(false);
  const [isNeighborhoodQuestion, setIsNeighborhoodQuestion] = useState(false);
  const [pincodeDetails, setPincodeDetails] = useState<string | null>(null);
  const [fetchingPincode, setFetchingPincode] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<{
    type: 'error' | 'network';
    msg: string;
  } | null>(null);

  // Smart Pre-Submit Warnings duplicate handling state
  const [showDuplicateModal, setShowDuplicateModal] = useState(false);
  const [duplicatePostData, setDuplicatePostData] = useState<any>(null);
  const [upvoting, setUpvoting] = useState(false);

  // Pre-fetch active posts cache
  const [cachedActivePosts, setCachedActivePosts] = useState<any[] | null>(
    null,
  );
  const preFetchPromiseRef = useRef<Promise<ApiResult> | null>(null);

  // ── Suggestions ──
  const [mentionSearch, setMentionSearch] = useState(false);
  const [mentionQuery, setMentionQuery] = useState('');
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // ── Validation ──
  const validate = () => {
    if (!content.trim()) {
      setError({
        type: 'error',
        msg: 'Please write something before posting.',
      });
      return false;
    }
    // Character length validation rules matched with the backend
    if (isReportingIssue) {
      if (content.trim().length < 3) {
        setError({
          type: 'error',
          msg: 'Civic issue description must be at least 3 characters long.',
        });
        return false;
      }
      if (content.length > 2000) {
        setError({
          type: 'error',
          msg: 'Civic issue description cannot exceed 2000 characters.',
        });
        return false;
      }
    } else {
      if (content.length > 3000) {
        setError({
          type: 'error',
          msg: 'Post content cannot exceed 3000 characters.',
        });
        return false;
      }
    }
    if (isReportingIssue && !targetPincode.trim()) {
      setError({
        type: 'error',
        msg: 'Pincode is required when reporting an issue.',
      });
      return false;
    }
    if (isReportingIssue && !/^\d{6}$/.test(targetPincode.trim())) {
      setError({ type: 'error', msg: 'Please enter a valid 6-digit pincode.' });
      return false;
    }
    return true;
  };

  // ── Submit ──
  const handlePost = async (force: boolean = false) => {
    setError(null);
    if (!validate()) return;
    setLoading(true);

    const shouldForce = force === true;

    try {
      // Client-Side Duplicate Checking
      if (isReportingIssue && !shouldForce) {
        let posts: any[] = [];
        if (cachedActivePosts) {
          posts = cachedActivePosts;
        } else if (preFetchPromiseRef.current) {
          const res = await preFetchPromiseRef.current;
          if (res.ok && Array.isArray(res.data)) {
            posts = res.data;
          }
        } else {
          const res = await apiGetActivePosts(40);
          if (res.ok && Array.isArray(res.data)) {
            posts = res.data;
          }
        }

        const pin = targetPincode.trim();
        // Filter active posts targeting this specific pincode and ensure they are government/civic type posts
        const activePincodePosts = posts.filter((p: any) => {
          const statusOk =
            p.status && String(p.status).toUpperCase() === 'ACTIVE';
          const pincodesList = Array.isArray(p.targetPincodes)
            ? p.targetPincodes
            : [];
          const pincodeOk =
            pincodesList.includes(pin) ||
            p.userPincode === pin ||
            p.targetPincode === pin;
          const isGovtType =
            p.broadcastScope !== undefined && p.broadcastScope !== null;
          return statusOk && pincodeOk && isGovtType;
        });

        let duplicateFound: any = null;
        for (const post of activePincodePosts) {
          const similarity = calculateSimilarity(
            content.trim(),
            post.content || '',
          );
          if (similarity >= 0.6) {
            duplicateFound = post;
            break;
          }
        }

        if (duplicateFound) {
          // Set duplicate post data and show warning modal
          setDuplicatePostData(duplicateFound);
          setShowDuplicateModal(true);
          setLoading(false);
          return;
        }
      }

      const idempotencyKey = generateUUID();
      const res = await createPostMutation.mutateAsync({
        isReportingIssue,
        content: content.trim(),
        targetPincode: targetPincode.trim(),
        files,
        communityId,
        forceSubmit: isReportingIssue ? true : undefined,
        idempotencyKey,
        category: isNeighborhoodQuestion ? 'NEIGHBORHOOD_QUESTION' : 'GENERAL',
        currentUser: {
          username: currentUser?.username || 'unknown',
          actualUsername: currentUser?.actualUsername,
          profileImage: currentUser?.profileImage || null,
        },
      });

      const syncedData = res?.data ?? res;
      if (
        syncedData?.status === 'PENDING_APPROVAL' ||
        syncedData?.isPendingApproval
      ) {
        showToast.success(
          'Your post has been submitted and is pending moderator approval.',
        );
      } else {
        showToast.success('Post created successfully!');
        if (onPostCreated) onPostCreated(syncedData);
        window.dispatchEvent(
          new CustomEvent('postCreated', {
            detail: { post: syncedData, communityId },
          }),
        );
      }

      setSubmitted(true);
      onClose();
    } catch (e: any) {
      const isNetwork =
        e instanceof TypeError && e.message?.toLowerCase().includes('fetch');
      setError({
        type: isNetwork ? 'network' : 'error',
        msg: isNetwork
          ? 'Server unreachable — please check your connection.'
          : e?.response?.data?.message ||
            e?.message ||
            'Unexpected error. Please try again.',
      });
    } finally {
      setLoading(false);
    }
  };

  // Upvotes the duplicate issue and cancels the current post creation
  const handleUpvoteDuplicate = async () => {
    if (!duplicatePostData) return;
    setUpvoting(true);
    setError(null);
    try {
      const res = await fetch(
        apiUrl(`/api/interactions/posts/${duplicatePostData.id}/like`),
        {
          method: 'POST',
          headers: authHeaders(),
        },
      );
      if (!res.ok) {
        const json = await res.json().catch(() => ({}));
        throw new Error(
          json?.error ?? json?.message ?? 'Failed to upvote duplicate post.',
        );
      }
      showToast.success("Upvoted! You've joined this issue report.");
      setShowDuplicateModal(false);
      onClose(); // Close the main create post modal
    } catch (e: any) {
      setError({
        type: 'error',
        msg: e.message || 'Failed to upvote duplicate issue. Please try again.',
      });
    } finally {
      setUpvoting(false);
    }
  };

  // ── Mentions ──
  const handleContentChange = (val: string) => {
    setContent(val);
    setError(null);

    if (!isReportingIssue) {
      setMentionSearch(false);
      return;
    }

    const cursor = textareaRef.current?.selectionStart ?? 0;
    const textBefore = val.slice(0, cursor);
    const lastAtPos = textBefore.lastIndexOf('@');

    if (lastAtPos !== -1) {
      const queryText = textBefore.slice(lastAtPos + 1);
      // Ensure no spaces between @ and cursor
      if (!queryText.includes(' ')) {
        setMentionSearch(true);
        setMentionQuery(queryText);
        setSelectedIndex(0);
        return;
      }
    }
    setMentionSearch(false);
  };

  const insertMention = (user: any) => {
    const cursor = textareaRef.current?.selectionStart ?? 0;
    const textBefore = content.slice(0, cursor);
    const textAfter = content.slice(cursor);
    const lastAtPos = textBefore.lastIndexOf('@');

    const newContent =
      textBefore.slice(0, lastAtPos) + '@' + user.username + ' ' + textAfter;
    setContent(newContent);
    setMentionSearch(false);
    textareaRef.current?.focus();
  };

  const applyFormatting = (formatType: 'bold' | 'italic' | 'mono') => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = content.substring(start, end);

    if (!selectedText) return;

    interface DecodedChar {
      char: string;
      bold: boolean;
      italic: boolean;
      mono: boolean;
    }

    const decodeChar = (codePoint: number): DecodedChar => {
      // Bold Italic
      if (codePoint >= 0x1d468 && codePoint <= 0x1d481)
        return {
          char: String.fromCharCode(codePoint - 0x1d468 + 65),
          bold: true,
          italic: true,
          mono: false,
        };
      if (codePoint >= 0x1d482 && codePoint <= 0x1d49b)
        return {
          char: String.fromCharCode(codePoint - 0x1d482 + 97),
          bold: true,
          italic: true,
          mono: false,
        };

      // Bold
      if (codePoint >= 0x1d400 && codePoint <= 0x1d419)
        return {
          char: String.fromCharCode(codePoint - 0x1d400 + 65),
          bold: true,
          italic: false,
          mono: false,
        };
      if (codePoint >= 0x1d41a && codePoint <= 0x1d433)
        return {
          char: String.fromCharCode(codePoint - 0x1d41a + 97),
          bold: true,
          italic: false,
          mono: false,
        };
      if (codePoint >= 0x1d7ce && codePoint <= 0x1d7d7)
        return {
          char: String.fromCharCode(codePoint - 0x1d7ce + 48),
          bold: true,
          italic: false,
          mono: false,
        };

      // Italic
      if (codePoint >= 0x1d434 && codePoint <= 0x1d44d)
        return {
          char: String.fromCharCode(codePoint - 0x1d434 + 65),
          bold: false,
          italic: true,
          mono: false,
        };
      if (codePoint >= 0x1d44e && codePoint <= 0x1d467)
        return {
          char: String.fromCharCode(codePoint - 0x1d44e + 97),
          bold: false,
          italic: true,
          mono: false,
        };
      if (codePoint === 0x210e)
        return { char: 'h', bold: false, italic: true, mono: false };

      // Monospace
      if (codePoint >= 0x1d670 && codePoint <= 0x1d689)
        return {
          char: String.fromCharCode(codePoint - 0x1d670 + 65),
          bold: false,
          italic: false,
          mono: true,
        };
      if (codePoint >= 0x1d68a && codePoint <= 0x1d6a3)
        return {
          char: String.fromCharCode(codePoint - 0x1d68a + 97),
          bold: false,
          italic: false,
          mono: true,
        };
      if (codePoint >= 0x1d7f6 && codePoint <= 0x1d7ff)
        return {
          char: String.fromCharCode(codePoint - 0x1d7f6 + 48),
          bold: false,
          italic: false,
          mono: true,
        };

      // Normal
      return {
        char: String.fromCodePoint(codePoint),
        bold: false,
        italic: false,
        mono: false,
      };
    };

    const encodeChar = (
      char: string,
      bold: boolean,
      italic: boolean,
      mono: boolean,
    ): string => {
      const code = char.charCodeAt(0);

      if (mono) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d670);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d68a);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7f6);
        return char;
      }

      if (bold && italic) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d468);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d482);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7ce);
        return char;
      }

      if (bold) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d400);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d41a);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7ce);
        return char;
      }

      if (italic) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d434);
        if (code === 104) return 'ℎ';
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d44e);
        return char;
      }

      return char;
    };

    const decoded = Array.from(selectedText).map((char) => {
      const cp = char.codePointAt(0) || 0;
      return decodeChar(cp);
    });

    const canApplyBold = decoded.some(
      (c) => /[A-Za-z0-9]/.test(c.char) && !c.bold,
    );
    const canApplyItalic = decoded.some(
      (c) => /[A-Za-z]/.test(c.char) && !c.italic,
    );
    const canApplyMono = decoded.some(
      (c) => /[A-Za-z0-9]/.test(c.char) && !c.mono,
    );

    let formatted = '';

    if (formatType === 'bold') {
      const turnOn = canApplyBold;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z0-9]/.test(c.char)) {
            return encodeChar(c.char, turnOn, c.italic, false);
          }
          return c.char;
        })
        .join('');
    } else if (formatType === 'italic') {
      const turnOn = canApplyItalic;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z]/.test(c.char)) {
            return encodeChar(c.char, c.bold, turnOn, false);
          }
          return c.char;
        })
        .join('');
    } else if (formatType === 'mono') {
      const turnOn = canApplyMono;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z0-9]/.test(c.char)) {
            return encodeChar(c.char, false, false, turnOn);
          }
          return c.char;
        })
        .join('');
    }

    const newContent =
      content.substring(0, start) + formatted + content.substring(end);
    setContent(newContent);

    // Refocus and restore selection
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(start, start + formatted.length);
    }, 50);
  };

  const fetchSuggestions = async (q: string) => {
    if (q.length < 2) {
      setSuggestions([]);
      return;
    }
    try {
      const res = await fetch(
        apiUrl(
          `/api/user-tagging/suggestions?query=${encodeURIComponent(q)}&limit=5`,
        ),
        {
          headers: authHeaders(),
        },
      );
      if (!res.ok) {
        console.error('[Mentions] API Error:', res.status);
        throw new Error();
      }
      const json = await res.json();
      const list = json.data?.data ?? json.data ?? [];

      let raw = Array.isArray(list) ? list : [];
      // Conditional filtering
      if (isReportingIssue) {
        raw = raw.filter((u: any) => u.role === 'ROLE_DEPARTMENT');
      }
      setSuggestions(raw);
    } catch (err) {
      console.error('[Mentions] Fetch failed:', err);
      setSuggestions([]);
    }
  };

  // Debounced fetch
  const timerRef = useRef<any>(null);
  useEffect(() => {
    if (mentionSearch) {
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => fetchSuggestions(mentionQuery), 200);
    }
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [mentionSearch, mentionQuery]);

  useEffect(() => {
    if (isReportingIssue) {
      if (/^\d{6}$/.test(targetPincode.trim())) {
        const promise = apiGetActivePosts(40);
        preFetchPromiseRef.current = promise;
        promise
          .then((res) => {
            if (res.ok && Array.isArray(res.data)) {
              setCachedActivePosts(res.data);
            }
          })
          .catch((err) => {
            console.error('Error pre-fetching active posts:', err);
          });
      }
    } else {
      setCachedActivePosts(null);
      preFetchPromiseRef.current = null;
    }
  }, [targetPincode, isReportingIssue]);

  // Fetch place name for pincode (Scenario B / Civic Issue)
  const pincodeDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (pincodeDebounceRef.current) {
      clearTimeout(pincodeDebounceRef.current);
    }

    if (isReportingIssue && /^[1-9]\d{5}$/.test(targetPincode)) {
      setFetchingPincode(true);
      setPincodeDetails(null);

      pincodeDebounceRef.current = setTimeout(async () => {
        try {
          const res = await axiosInstance.get(`/api/pincode/${targetPincode}`);
          const json = res.data;
          const isWrapped =
            json?.success !== undefined && json?.data !== undefined;
          const data = isWrapped ? json.data : json;

          if (
            data &&
            (data.state || data.city || data.district || data.areaName)
          ) {
            const cityDistrict = data.city || data.district || '';
            const state = data.state || '';
            const area = data.areaName || '';
            const parts = [area, cityDistrict, state].filter(Boolean);
            const locationStr = parts.join(', ');
            setPincodeDetails(locationStr || 'Verified Location');
          } else {
            setPincodeDetails('Location not found');
          }
        } catch {
          setPincodeDetails('Location not found');
        } finally {
          setFetchingPincode(false);
        }
      }, 250);
    } else {
      setPincodeDetails(null);
      setFetchingPincode(false);
    }

    return () => {
      if (pincodeDebounceRef.current) {
        clearTimeout(pincodeDebounceRef.current);
      }
    };
  }, [targetPincode, isReportingIssue]);

  if (submitted) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-6 text-center">
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          <CheckCircle2 size={48} className="text-[#1D4ED8]" />
        </motion.div>
        <p className="text-base-content font-bold text-lg">
          {isReportingIssue ? 'Issue Reported!' : 'Post Published!'}
        </p>
        <p className="text-base-content/50 text-sm">
          {isReportingIssue
            ? 'Your report has been submitted to the relevant government department.'
            : 'Your post is now live in the community feed.'}
        </p>
        <div className="flex gap-2">
          <button
            className="btn btn-sm bg-[#1D4ED8] hover:bg-[#1D4ED8]/90 text-white"
            onClick={() => {
              setSubmitted(false);
              setContent('');
              setFiles([]);
              setTargetPincode('');
              setPincodeDetails(null);
              setError(null);
              setCachedActivePosts(null);
              preFetchPromiseRef.current = null;
            }}
          >
            Post Again
          </button>
          <button className="btn btn-sm btn-ghost" onClick={onClose}>
            Done
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col justify-between h-full relative">
      <div className="flex-1 min-h-0 overflow-y-auto p-3.5 sm:p-5 space-y-2.5 sm:space-y-3">
        <AnimatePresence>
          {error && <StatusBanner status={error.type} message={error.msg} />}
        </AnimatePresence>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 sm:gap-5">
          <div className="space-y-2.5 sm:space-y-3">
            {!communityId && (
              <div className="grid grid-cols-2 gap-2">
                {/* Report Issue Button */}
                <button
                  type="button"
                  onClick={() => {
                    const next = !isReportingIssue;
                    setIsReportingIssue(next);
                    if (next) setIsNeighborhoodQuestion(false);
                    setError(null);
                  }}
                  className={`flex items-center justify-center gap-1.5 sm:gap-2 py-2 sm:py-2.5 px-2.5 sm:px-3 rounded-xl border transition-all duration-200 text-[9px] sm:text-[10px] font-black uppercase tracking-wider cursor-pointer select-none ${
                    isReportingIssue
                      ? 'bg-[#1D4ED8] border-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                      : 'bg-base-200/40 border-base-content/10 dark:border-white/10 hover:border-base-content/20 text-base-content/70 hover:text-base-content'
                  }`}
                >
                  <AlertTriangle
                    size={13}
                    className={
                      isReportingIssue ? 'text-white' : 'text-base-content/60'
                    }
                  />
                  <span>Report Issue</span>
                </button>

                {/* Ask Locally Button */}
                <button
                  type="button"
                  onClick={() => {
                    const next = !isNeighborhoodQuestion;
                    setIsNeighborhoodQuestion(next);
                    if (next) setIsReportingIssue(false);
                    setError(null);
                  }}
                  className={`flex items-center justify-center gap-1.5 sm:gap-2 py-2 sm:py-2.5 px-2.5 sm:px-3 rounded-xl border transition-all duration-200 text-[9px] sm:text-[10px] font-black uppercase tracking-wider cursor-pointer select-none ${
                    isNeighborhoodQuestion
                      ? 'bg-[#1D4ED8] border-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                      : 'bg-base-200/40 border-base-content/10 dark:border-white/10 hover:border-base-content/20 text-base-content/70 hover:text-base-content'
                  }`}
                >
                  <BarChart2
                    size={13}
                    className={
                      isNeighborhoodQuestion
                        ? 'text-white'
                        : 'text-base-content/60'
                    }
                  />
                  <span>Ask Locally</span>
                </button>
              </div>
            )}

            <AnimatePresence>
              {(isReportingIssue || isNeighborhoodQuestion) && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <div className="flex flex-col gap-1">
                    <label
                      className={`text-[9px] sm:text-[10px] font-black uppercase tracking-widest flex items-center gap-1 ${
                        isReportingIssue
                          ? 'text-red-600 dark:text-red-400'
                          : 'text-[#1D4ED8] dark:text-blue-400'
                      }`}
                    >
                      <MdLocationOn
                        size={12}
                        className={
                          isReportingIssue
                            ? 'text-red-600 dark:text-red-400'
                            : 'text-[#1D4ED8] dark:text-blue-400'
                        }
                      />
                      {isReportingIssue
                        ? 'Area Pincode'
                        : 'Target Neighborhood Pincode (Optional)'}
                      {isReportingIssue && (
                        <span className="text-error">*</span>
                      )}
                    </label>
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      placeholder={
                        isReportingIssue
                          ? 'e.g. 400001 (Mumbai)'
                          : 'e.g. 400001 (leave empty for your local area)'
                      }
                      className="input input-xs sm:input-sm focus:border-[#1D4ED8] focus:ring-2 focus:ring-[#1D4ED8]/15 focus:outline-none w-full bg-base-100/80 border border-base-content/15 dark:border-white/15 rounded-xl font-medium text-xs sm:text-sm text-base-content h-8 sm:h-9"
                      value={targetPincode}
                      onChange={(e) => {
                        setTargetPincode(e.target.value.replace(/\D/g, ''));
                        setError(null);
                      }}
                    />
                    <div className="text-[8.5px] sm:text-[9px] min-h-[14px] mt-0.5">
                      {fetchingPincode ? (
                        <span className="flex items-center gap-1 text-base-content/60 font-bold uppercase tracking-tight">
                          <Loader2
                            size={10}
                            className="animate-spin text-[#1D4ED8] dark:text-blue-400"
                          />
                          <span>Fetching location details...</span>
                        </span>
                      ) : pincodeDetails ? (
                        pincodeDetails === 'Location not found' ||
                        pincodeDetails === 'Invalid location' ? (
                          <span className="flex items-center gap-1 text-red-500 dark:text-red-400 font-semibold uppercase tracking-tight">
                            <AlertTriangle
                              size={10}
                              className="text-red-500 dark:text-red-400 shrink-0"
                            />
                            <span>{pincodeDetails}</span>
                          </span>
                        ) : (
                          <span className="flex items-center gap-1 text-emerald-600 dark:emerald-400 font-bold uppercase tracking-tight">
                            <CheckCircle2
                              size={10}
                              className="text-emerald-600 dark:text-emerald-400 shrink-0"
                            />
                            <span>{pincodeDetails}</span>
                          </span>
                        )
                      ) : (
                        <span className="text-base-content/40 font-bold uppercase tracking-tight">
                          {isReportingIssue
                            ? '6-digit Indian pincode — used for local targeting'
                            : 'Ask neighbors in a specific pincode or leave blank to ask your home area'}
                        </span>
                      )}
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="relative">
              <label className="block text-[9.5px] sm:text-[10px] uppercase font-black tracking-widest text-base-content/80 mb-1">
                {isReportingIssue
                  ? 'Issue Description'
                  : isNeighborhoodQuestion
                    ? 'Question Details'
                    : 'Post Content'}{' '}
                <span className="text-error">*</span>
              </label>
              <textarea
                ref={textareaRef}
                placeholder={
                  isReportingIssue
                    ? 'Describe the civic issue in detail...'
                    : isNeighborhoodQuestion
                      ? 'Ask your neighbors a question (e.g. Best broadband in this area? Any recommended domestic help or pediatrician?)...'
                      : "What's on your mind? Share thoughts, updates, or news..."
                }
                className="textarea transition-all w-full resize-none text-xs sm:text-sm rounded-xl sm:rounded-2xl min-h-[90px] sm:min-h-[140px] text-base-content leading-relaxed font-medium bg-base-100/80 border border-base-content/15 dark:border-white/15 focus:border-[#1D4ED8] focus:ring-2 focus:ring-[#1D4ED8]/15 focus:outline-none p-2.5 sm:p-3"
                value={content}
                onChange={(e) => handleContentChange(e.target.value)}
                onKeyDown={(e) => {
                  if (mentionSearch && suggestions.length > 0) {
                    if (e.key === 'ArrowDown') {
                      e.preventDefault();
                      setSelectedIndex(
                        (prev) => (prev + 1) % suggestions.length,
                      );
                    } else if (e.key === 'ArrowUp') {
                      e.preventDefault();
                      setSelectedIndex(
                        (prev) =>
                          (prev - 1 + suggestions.length) % suggestions.length,
                      );
                    } else if (e.key === 'Enter' || e.key === 'Tab') {
                      e.preventDefault();
                      insertMention(suggestions[selectedIndex]);
                    } else if (e.key === 'Escape') {
                      setMentionSearch(false);
                    }
                  }
                }}
              />
              {/* Rich formatting toolbar */}
              <div className="flex items-center gap-1 sm:gap-1.5 px-2 sm:px-3 py-1 sm:py-1.5 bg-base-200/50 border border-base-300 rounded-lg sm:rounded-xl mt-1.5 select-none flex-wrap">
                <button
                  type="button"
                  onClick={() => applyFormatting('bold')}
                  title="Bold"
                  className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
                >
                  <RiBold size={13} className="sm:size-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => applyFormatting('italic')}
                  title="Italic"
                  className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
                >
                  <RiItalic size={13} className="sm:size-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => applyFormatting('mono')}
                  title="Monospace"
                  className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
                >
                  <RiCodeLine size={13} className="sm:size-3.5" />
                </button>
                <div className="w-[1px] h-3.5 bg-base-content/10 mx-0.5" />
                <button
                  type="button"
                  onClick={() => {
                    const textarea = textareaRef.current;
                    if (!textarea) return;
                    const start = textarea.selectionStart;
                    const newContent =
                      content.substring(0, start) +
                      '#' +
                      content.substring(start);
                    setContent(newContent);
                    setTimeout(() => {
                      textarea.focus();
                      textarea.setSelectionRange(start + 1, start + 1);
                    }, 50);
                  }}
                  title="Hashtag"
                  className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors font-mono font-bold text-xs cursor-pointer"
                >
                  <RiHashtag size={13} className="sm:size-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const textarea = textareaRef.current;
                    if (!textarea) return;
                    const start = textarea.selectionStart;
                    const newContent =
                      content.substring(0, start) +
                      '@' +
                      content.substring(start);
                    setContent(newContent);
                    handleContentChange(newContent);
                    setTimeout(() => {
                      textarea.focus();
                      textarea.setSelectionRange(start + 1, start + 1);
                    }, 50);
                  }}
                  title="Mention"
                  className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors font-mono font-bold text-xs cursor-pointer"
                >
                  <RiAtLine size={13} className="sm:size-3.5" />
                </button>
                <div className="relative">
                  <button
                    type="button"
                    onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                    title="Insert Emoji"
                    className={`btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 rounded-md sm:rounded-lg transition-colors cursor-pointer ${showEmojiPicker ? 'text-[#1D4ED8] bg-[#1D4ED8]/10' : 'text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10'}`}
                  >
                    <RiEmotionHappyLine size={13} className="sm:size-3.5" />
                  </button>
                  {showEmojiPicker && (
                    <>
                      <div
                        className="fixed inset-0 z-40"
                        onClick={() => setShowEmojiPicker(false)}
                      />
                      <div className="absolute left-0 bottom-full mb-1.5 z-50 p-1.5 bg-base-100 border border-base-300 rounded-xl shadow-xl flex gap-1 items-center">
                        {[
                          '🔥',
                          '👍',
                          '❤️',
                          '🙌',
                          '💡',
                          '⚠️',
                          '🚨',
                          '✅',
                          '🎉',
                          '👀',
                        ].map((emoji) => (
                          <button
                            key={emoji}
                            type="button"
                            onClick={() => {
                              const textarea = textareaRef.current;
                              if (!textarea) return;
                              const start = textarea.selectionStart;
                              const end = textarea.selectionEnd;
                              const newContent =
                                content.substring(0, start) +
                                emoji +
                                content.substring(end);
                              setContent(newContent);
                              setShowEmojiPicker(false);
                              setTimeout(() => {
                                textarea.focus();
                                textarea.setSelectionRange(
                                  start + emoji.length,
                                  start + emoji.length,
                                );
                              }, 50);
                            }}
                            className="btn btn-ghost btn-xs w-7 h-7 p-0 text-sm hover:scale-125 transition-transform"
                          >
                            {emoji}
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* Mention Auto-complete Dropdown */}
              <AnimatePresence>
                {mentionSearch && suggestions.length > 0 && (
                  <motion.div
                    initial={{ opacity: 0, y: 5 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: 5 }}
                    className="absolute left-0 right-0 top-full mt-1.5 z-50 bg-base-100 dark:bg-base-200 border border-base-300 dark:border-white/10 rounded-xl shadow-2xl overflow-hidden max-h-48 overflow-y-auto divide-y divide-base-200 dark:divide-base-300"
                  >
                    {suggestions.map((u, idx) => (
                      <button
                        key={u.username}
                        type="button"
                        onMouseDown={(e) => {
                          e.preventDefault(); // prevent blur
                          insertMention(u);
                        }}
                        className={`w-full px-3 py-2 text-left flex items-center gap-2.5 transition-colors cursor-pointer ${
                          idx === selectedIndex
                            ? 'bg-[#1D4ED8]/10 text-[#1D4ED8] dark:bg-blue-900/30 dark:text-blue-300 font-bold'
                            : 'hover:bg-base-200/60 text-base-content font-medium'
                        }`}
                      >
                        <div className="w-5 h-5 rounded-full overflow-hidden shrink-0 border border-base-300">
                          {u.profileImage ? (
                            <img
                              src={u.profileImage}
                              alt={u.username}
                              className="w-full h-full object-cover"
                            />
                          ) : (
                            <div className="w-full h-full bg-[#1D4ED8]/20 flex items-center justify-center text-[10px] text-[#1D4ED8] font-bold">
                              {u.username[0].toUpperCase()}
                            </div>
                          )}
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="text-xs truncate">
                            {u.name || u.username}
                          </span>
                          <span className="text-[10px] text-base-content/50 truncate">
                            @{u.username}
                          </span>
                        </div>
                      </button>
                    ))}
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* ── Character Count Banner ── */}
            <div className="flex justify-between items-center text-[8.5px] sm:text-[9px] px-1 text-base-content/50 -mt-0.5 font-bold uppercase tracking-wider">
              <span>
                {isReportingIssue
                  ? 'Requirement: Min 3, max 2000 chars'
                  : 'Requirement: Max 3000 chars'}
              </span>
              <span
                className={
                  content.length > (isReportingIssue ? 2000 : 3000)
                    ? 'text-error font-black'
                    : ''
                }
              >
                {content.length}/{isReportingIssue ? 2000 : 3000}
              </span>
            </div>
          </div>

          <div className="flex flex-col justify-start space-y-2.5 sm:space-y-3">
            <div>
              <label className="block text-[9.5px] sm:text-[10px] uppercase font-black tracking-widest text-base-content/80 mb-1 flex items-center gap-1">
                <RiAttachment2 size={12} /> Media Attachment{' '}
                <span className="text-base-content/40 font-bold ml-1 tracking-normal">
                  (optional)
                </span>
              </label>
              <MediaUploadZone
                accent="blue"
                files={files}
                onChange={setFiles}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="shrink-0 px-3.5 sm:px-5 py-2.5 sm:py-3 border-t border-base-content/10 bg-base-100 flex items-center justify-end gap-2">
        <button
          onClick={onClose}
          className="btn btn-xs sm:btn-sm btn-ghost rounded-lg sm:rounded-xl px-3 sm:px-4 text-[11px] sm:text-xs font-bold uppercase tracking-wider text-base-content/70 hover:text-base-content h-8 sm:h-9"
          disabled={loading}
        >
          Cancel
        </button>
        <button
          disabled={loading}
          className={`btn btn-xs sm:btn-sm text-white min-w-[100px] sm:min-w-[130px] rounded-lg sm:rounded-xl font-bold uppercase tracking-wider text-[10px] sm:text-[11px] transition-all duration-300 bg-blue-700 hover:bg-blue-800 shadow-lg shadow-blue-700/20 border-none h-8 sm:h-9 cursor-pointer ${
            loading ? 'opacity-70 cursor-not-allowed' : ''
          }`}
          onClick={() => handlePost(false)}
        >
          {loading ? (
            <span className="flex items-center gap-1.5">
              <Loader2 size={13} className="animate-spin" />
              <span className="hidden sm:inline">Submitting...</span>
              <span className="sm:hidden">...</span>
            </span>
          ) : (
            'Post'
          )}
        </button>
      </div>

      {/* Smart Pre-Submit Warning Modal Overlay */}
      <AnimatePresence>
        {showDuplicateModal && duplicatePostData && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="absolute inset-0 bg-base-100/98 backdrop-blur-sm z-50 flex flex-col justify-between p-5 rounded-2xl overflow-y-auto"
          >
            <div className="flex flex-col gap-3.5">
              <div className="flex items-start gap-3 bg-warning/10 border border-warning/30 p-3 rounded-xl text-warning">
                <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
                <div>
                  <h3 className="text-xs font-black uppercase tracking-wider">
                    Wait! Is this issue already reported?
                  </h3>
                  <p className="text-[10px] opacity-80 leading-normal font-medium mt-0.5">
                    We found a very similar issue recently reported in your
                    area. Please check if this matches your report:
                  </p>
                </div>
              </div>

              {/* Duplicate Post Card Preview */}
              <div className="border border-base-content/10 bg-base-200/50 rounded-xl p-3.5 flex flex-col gap-2">
                <div className="flex items-center justify-between text-[9px] font-bold uppercase tracking-wider opacity-60">
                  <span>
                    Pincode:{' '}
                    {duplicatePostData.targetPincodes?.[0] ||
                      duplicatePostData.targetPincode ||
                      targetPincode}
                  </span>
                  <span>
                    {duplicatePostData.createdAt || duplicatePostData.timestamp
                      ? new Date(
                          duplicatePostData.createdAt ||
                            duplicatePostData.timestamp,
                        ).toLocaleString(undefined, {
                          dateStyle: 'medium',
                          timeStyle: 'short',
                        })
                      : 'Recently reported'}
                  </span>
                </div>
                <p className="text-xs text-base-content leading-relaxed whitespace-pre-wrap font-medium">
                  {duplicatePostData.content}
                </p>
              </div>
            </div>

            <div className="flex flex-col gap-2.5 pt-4 border-t border-base-content/5 mt-4">
              <button
                disabled={upvoting}
                onClick={handleUpvoteDuplicate}
                className="btn btn-sm bg-green-500 hover:bg-green-600 text-white w-full rounded-xl transition-all font-bold"
              >
                {upvoting ? (
                  <span className="flex items-center gap-1.5 justify-center">
                    <Loader2 size={13} className="animate-spin" /> Upvoting...
                  </span>
                ) : (
                  'Yes, "Me Too!" (Upvote)'
                )}
              </button>

              <div className="flex items-center gap-2">
                <button
                  disabled={upvoting}
                  onClick={() => {
                    setShowDuplicateModal(false);
                    handlePost(true); // retry with forceSubmit = true
                  }}
                  className="btn btn-sm btn-outline btn-ghost flex-1 text-[11px] font-black uppercase tracking-wider rounded-xl"
                >
                  No, Post Anyway
                </button>
                <button
                  disabled={upvoting}
                  onClick={() => {
                    setShowDuplicateModal(false);
                    setDuplicatePostData(null);
                  }}
                  className="btn btn-sm btn-ghost flex-1 text-[11px] font-black uppercase tracking-wider rounded-xl text-base-content/50"
                >
                  Go Back
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ─── POLL FORM (wired to backend) ─────────────────────────────────────────────
function PollForm({
  communityId,
  onClose,
  onPostCreated,
}: {
  communityId?: number;
  onClose: () => void;
  onPostCreated?: (post: any) => void;
}) {
  const [pollQuestion, setPollQuestion] = useState('');
  const [options, setOptions] = useState(['', '']);
  const [errors, setErrors] = useState<Record<string, string | boolean>>({});
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [allowMultipleVotes, setAllowMultipleVotes] = useState(false);
  const [expiresIn, setExpiresIn] = useState('1d');
  const [files, setFiles] = useState<File[]>([]);
  const { data: currentUser } = useCurrentUser();
  const createPollMutation = useCreatePoll();

  const [isNeighborhoodQuestion, setIsNeighborhoodQuestion] = useState(false);

  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [activeField, setActiveField] = useState<{
    type: 'question' | 'option';
    index?: number;
  } | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const updateOption = (i: number, val: string) => {
    const u = [...options];
    u[i] = val;
    setOptions(u);
  };
  const addOption = () => {
    if (options.length < 4) setOptions([...options, '']);
  };
  const removeOption = (i: number) => {
    if (options.length <= 2) return;
    setOptions(options.filter((_, idx) => idx !== i));
  };

  const validate = () => {
    const errs: Record<string, string | boolean> = {};
    if (!pollQuestion.trim()) {
      errs.pollQuestion = 'Poll question is required';
    } else if (pollQuestion.length > 500) {
      errs.pollQuestion = 'Poll question cannot exceed 500 characters';
    }
    options.forEach((o, i) => {
      if (!o.trim()) errs[`opt${i}`] = true;
    });

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handlePost = async () => {
    if (!validate()) return;
    setApiError(null);
    setLoading(true);
    try {
      const payload = {
        question: pollQuestion.trim(),
        options: options.filter((o) => o.trim()),
        expiresIn,
        allowMultipleVotes,
        showResultsBeforeExpiry: true,
        communityId,
        category: isNeighborhoodQuestion ? 'NEIGHBORHOOD_QUESTION' : 'GENERAL',
      };

      const idempotencyKey = generateUUID();
      const res = await createPollMutation.mutateAsync({
        payload,
        files,
        idempotencyKey,
        currentUser: {
          username: currentUser?.username || 'unknown',
          actualUsername: currentUser?.actualUsername,
          profileImage: currentUser?.profileImage || null,
        },
      });

      const syncedData = res?.data ?? res;
      if (
        syncedData?.status === 'PENDING_APPROVAL' ||
        syncedData?.isPendingApproval
      ) {
        showToast.success(
          'Your post has been submitted and is pending moderator approval.',
        );
      } else {
        showToast.success('Poll post created successfully!');
        const augmentedPoll = {
          ...syncedData,
          variant: 'poll',
          actualUsername: currentUser?.actualUsername,
          username: currentUser?.actualUsername ?? currentUser?.username,
          userDisplayName: currentUser?.actualUsername ?? currentUser?.username,
          userProfileImage: currentUser?.profileImage,
          poll: syncedData,
        };
        if (onPostCreated) onPostCreated(augmentedPoll);
        window.dispatchEvent(
          new CustomEvent('postCreated', {
            detail: { post: augmentedPoll, communityId },
          }),
        );
      }

      setSubmitted(true);
      onClose();
    } catch (e: any) {
      const isNet =
        e instanceof TypeError && e.message?.toLowerCase().includes('fetch');
      setApiError(
        isNet
          ? 'Server unreachable — please check your connection.'
          : e?.response?.data?.message ||
              e?.message ||
              'Server error. Please try again.',
      );
    } finally {
      setLoading(false);
    }
  };

  const applyPollFormatting = (formatType: 'bold' | 'italic' | 'mono') => {
    const field = activeField || { type: 'question' };
    let element: HTMLTextAreaElement | HTMLInputElement | null = null;
    let currentText = '';

    if (field.type === 'question') {
      element = textareaRef.current;
      currentText = pollQuestion;
    } else if (field.type === 'option' && typeof field.index === 'number') {
      element = document.getElementById(
        `poll-opt-input-${field.index}`,
      ) as HTMLInputElement;
      currentText = options[field.index] || '';
    }

    if (!element) return;

    const start = element.selectionStart ?? 0;
    const end = element.selectionEnd ?? 0;
    const selectedText = currentText.substring(start, end);

    if (!selectedText) return;

    interface DecodedChar {
      char: string;
      bold: boolean;
      italic: boolean;
      mono: boolean;
    }

    const decodeChar = (codePoint: number): DecodedChar => {
      // Bold Italic
      if (codePoint >= 0x1d468 && codePoint <= 0x1d481)
        return {
          char: String.fromCharCode(codePoint - 0x1d468 + 65),
          bold: true,
          italic: true,
          mono: false,
        };
      if (codePoint >= 0x1d482 && codePoint <= 0x1d49b)
        return {
          char: String.fromCharCode(codePoint - 0x1d482 + 97),
          bold: true,
          italic: true,
          mono: false,
        };

      // Bold
      if (codePoint >= 0x1d400 && codePoint <= 0x1d419)
        return {
          char: String.fromCharCode(codePoint - 0x1d400 + 65),
          bold: true,
          italic: false,
          mono: false,
        };
      if (codePoint >= 0x1d41a && codePoint <= 0x1d433)
        return {
          char: String.fromCharCode(codePoint - 0x1d41a + 97),
          bold: true,
          italic: false,
          mono: false,
        };
      if (codePoint >= 0x1d7ce && codePoint <= 0x1d7d7)
        return {
          char: String.fromCharCode(codePoint - 0x1d7ce + 48),
          bold: true,
          italic: false,
          mono: false,
        };

      // Italic
      if (codePoint >= 0x1d434 && codePoint <= 0x1d44d)
        return {
          char: String.fromCharCode(codePoint - 0x1d434 + 65),
          bold: false,
          italic: true,
          mono: false,
        };
      if (codePoint >= 0x1d44e && codePoint <= 0x1d467)
        return {
          char: String.fromCharCode(codePoint - 0x1d44e + 97),
          bold: false,
          italic: true,
          mono: false,
        };
      if (codePoint === 0x210e)
        return { char: 'h', bold: false, italic: true, mono: false };

      // Monospace
      if (codePoint >= 0x1d670 && codePoint <= 0x1d689)
        return {
          char: String.fromCharCode(codePoint - 0x1d670 + 65),
          bold: false,
          italic: false,
          mono: true,
        };
      if (codePoint >= 0x1d68a && codePoint <= 0x1d6a3)
        return {
          char: String.fromCharCode(codePoint - 0x1d68a + 97),
          bold: false,
          italic: false,
          mono: true,
        };
      if (codePoint >= 0x1d7f6 && codePoint <= 0x1d7ff)
        return {
          char: String.fromCharCode(codePoint - 0x1d7f6 + 48),
          bold: false,
          italic: false,
          mono: true,
        };

      // Normal
      return {
        char: String.fromCodePoint(codePoint),
        bold: false,
        italic: false,
        mono: false,
      };
    };

    const encodeChar = (
      char: string,
      bold: boolean,
      italic: boolean,
      mono: boolean,
    ): string => {
      const code = char.charCodeAt(0);

      if (mono) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d670);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d68a);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7f6);
        return char;
      }

      if (bold && italic) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d468);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d482);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7ce);
        return char;
      }

      if (bold) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d400);
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d41a);
        if (code >= 48 && code <= 57)
          return String.fromCodePoint(code - 48 + 0x1d7ce);
        return char;
      }

      if (italic) {
        if (code >= 65 && code <= 90)
          return String.fromCodePoint(code - 65 + 0x1d434);
        if (code === 104) return 'ℎ';
        if (code >= 97 && code <= 122)
          return String.fromCodePoint(code - 97 + 0x1d44e);
        return char;
      }

      return char;
    };

    const decoded = Array.from(selectedText).map((char) => {
      const cp = char.codePointAt(0) || 0;
      return decodeChar(cp);
    });

    const canApplyBold = decoded.some(
      (c) => /[A-Za-z0-9]/.test(c.char) && !c.bold,
    );
    const canApplyItalic = decoded.some(
      (c) => /[A-Za-z]/.test(c.char) && !c.italic,
    );
    const canApplyMono = decoded.some(
      (c) => /[A-Za-z0-9]/.test(c.char) && !c.mono,
    );

    let formatted = '';

    if (formatType === 'bold') {
      const turnOn = canApplyBold;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z0-9]/.test(c.char)) {
            return encodeChar(c.char, turnOn, c.italic, false);
          }
          return c.char;
        })
        .join('');
    } else if (formatType === 'italic') {
      const turnOn = canApplyItalic;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z]/.test(c.char)) {
            return encodeChar(c.char, c.bold, turnOn, false);
          }
          return c.char;
        })
        .join('');
    } else if (formatType === 'mono') {
      const turnOn = canApplyMono;
      formatted = decoded
        .map((c) => {
          if (/[A-Za-z0-9]/.test(c.char)) {
            return encodeChar(c.char, false, false, turnOn);
          }
          return c.char;
        })
        .join('');
    }

    const newText =
      currentText.substring(0, start) + formatted + currentText.substring(end);

    if (field.type === 'question') {
      setPollQuestion(newText);
    } else if (field.type === 'option' && typeof field.index === 'number') {
      updateOption(field.index, newText);
    }

    setTimeout(() => {
      element?.focus();
      element?.setSelectionRange(start, start + formatted.length);
    }, 50);
  };

  const insertHashtag = () => {
    const field = activeField || { type: 'question' };
    let element: HTMLTextAreaElement | HTMLInputElement | null = null;
    let currentText = '';

    if (field.type === 'question') {
      element = textareaRef.current;
      currentText = pollQuestion;
    } else if (field.type === 'option' && typeof field.index === 'number') {
      element = document.getElementById(
        `poll-opt-input-${field.index}`,
      ) as HTMLInputElement;
      currentText = options[field.index] || '';
    }

    if (!element) return;
    const start = element.selectionStart ?? 0;
    const newText =
      currentText.substring(0, start) + '#' + currentText.substring(start);

    if (field.type === 'question') {
      setPollQuestion(newText);
    } else if (field.type === 'option' && typeof field.index === 'number') {
      updateOption(field.index, newText);
    }

    setTimeout(() => {
      element?.focus();
      element?.setSelectionRange(start + 1, start + 1);
    }, 50);
  };

  const insertAtSign = () => {
    const field = activeField || { type: 'question' };
    let element: HTMLTextAreaElement | HTMLInputElement | null = null;
    let currentText = '';

    if (field.type === 'question') {
      element = textareaRef.current;
      currentText = pollQuestion;
    } else if (field.type === 'option' && typeof field.index === 'number') {
      element = document.getElementById(
        `poll-opt-input-${field.index}`,
      ) as HTMLInputElement;
      currentText = options[field.index] || '';
    }

    if (!element) return;
    const start = element.selectionStart ?? 0;
    const newText =
      currentText.substring(0, start) + '@' + currentText.substring(start);

    if (field.type === 'question') {
      setPollQuestion(newText);
    } else if (field.type === 'option' && typeof field.index === 'number') {
      updateOption(field.index, newText);
    }

    setTimeout(() => {
      element?.focus();
      element?.setSelectionRange(start + 1, start + 1);
    }, 50);
  };

  const insertEmoji = (emoji: string) => {
    const field = activeField || { type: 'question' };
    let element: HTMLTextAreaElement | HTMLInputElement | null = null;
    let currentText = '';

    if (field.type === 'question') {
      element = textareaRef.current;
      currentText = pollQuestion;
    } else if (field.type === 'option' && typeof field.index === 'number') {
      element = document.getElementById(
        `poll-opt-input-${field.index}`,
      ) as HTMLInputElement;
      currentText = options[field.index] || '';
    }

    if (!element) return;
    const start = element.selectionStart ?? 0;
    const end = element.selectionEnd ?? 0;
    const newText =
      currentText.substring(0, start) + emoji + currentText.substring(end);

    if (field.type === 'question') {
      setPollQuestion(newText);
    } else if (field.type === 'option' && typeof field.index === 'number') {
      updateOption(field.index, newText);
    }

    setShowEmojiPicker(false);
    setTimeout(() => {
      element?.focus();
      element?.setSelectionRange(start + emoji.length, start + emoji.length);
    }, 50);
  };

  if (submitted) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-8 text-center">
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          <CheckCircle2 size={52} className="text-[#1D4ED8]" />
        </motion.div>
        <p className="font-bold text-lg">Poll Posted!</p>
        <p className="text-sm text-base-content/50">
          Your poll is now live in the community feed.
        </p>
        <button
          className="btn btn-sm bg-[#1D4ED8] text-white"
          onClick={() => {
            setSubmitted(false);
            setPollQuestion('');
            setOptions(['', '']);
            setApiError(null);
            setFiles([]);
          }}
        >
          Post Another
        </button>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col justify-between h-full relative">
      <div className="flex-1 min-h-0 overflow-y-auto p-3.5 sm:p-5 space-y-2.5 sm:space-y-3">
        <AnimatePresence>
          {apiError && (
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="flex items-center gap-2 px-3 py-1.5 sm:py-2 rounded-xl border border-red-500/30 bg-red-500/10 text-red-400 text-xs font-medium"
            >
              <X size={13} className="shrink-0" /> <span>{apiError}</span>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 sm:gap-5">
          <div className="space-y-2.5 sm:space-y-3">
            {!communityId && (
              <div className="w-full">
                {/* Ask Locally Button */}
                <button
                  type="button"
                  onClick={() => {
                    setIsNeighborhoodQuestion(!isNeighborhoodQuestion);
                    setApiError(null);
                  }}
                  className={`w-full flex items-center justify-center gap-1.5 sm:gap-2 py-2 sm:py-2.5 px-2.5 sm:px-3 rounded-xl border transition-all duration-200 text-[9px] sm:text-[10px] font-black uppercase tracking-wider cursor-pointer select-none ${
                    isNeighborhoodQuestion
                      ? 'bg-[#1D4ED8] border-[#1D4ED8] text-white shadow-sm shadow-[#1D4ED8]/30'
                      : 'bg-base-200/40 border-base-content/10 dark:border-white/10 hover:border-base-content/20 text-base-content/70 hover:text-base-content'
                  }`}
                >
                  <BarChart2
                    size={13}
                    className={
                      isNeighborhoodQuestion
                        ? 'text-white'
                        : 'text-base-content/60'
                    }
                  />
                  <span>Ask Locally</span>
                </button>
              </div>
            )}

            <div className="relative group">
              <textarea
                ref={textareaRef}
                className={`textarea w-full min-h-[75px] sm:min-h-[95px] bg-base-100/80 border border-base-content/15 dark:border-white/15 focus:border-[#1D4ED8] focus:ring-2 focus:ring-[#1D4ED8]/15 focus:outline-none transition-all duration-200 resize-none text-xs sm:text-sm font-medium rounded-xl sm:rounded-2xl p-2.5 sm:p-3 ${errors.pollQuestion ? 'border-red-500/50' : ''}`}
                placeholder="Ask your poll question..."
                value={pollQuestion}
                onChange={(e) => setPollQuestion(e.target.value)}
                onFocus={() => setActiveField({ type: 'question' })}
              />
            </div>

            {/* Rich formatting toolbar */}
            <div className="flex items-center gap-1 sm:gap-1.5 px-2 sm:px-3 py-1 sm:py-1.5 bg-base-200/50 border border-base-300 rounded-lg sm:rounded-xl select-none flex-wrap">
              <button
                type="button"
                onClick={() => applyPollFormatting('bold')}
                title="Bold"
                className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
              >
                <RiBold size={13} className="sm:size-3.5" />
              </button>
              <button
                type="button"
                onClick={() => applyPollFormatting('italic')}
                title="Italic"
                className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
              >
                <RiItalic size={13} className="sm:size-3.5" />
              </button>
              <button
                type="button"
                onClick={() => applyPollFormatting('mono')}
                title="Monospace"
                className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors cursor-pointer"
              >
                <RiCodeLine size={13} className="sm:size-3.5" />
              </button>
              <div className="w-[1px] h-3.5 bg-base-content/10 mx-0.5" />
              <button
                type="button"
                onClick={insertHashtag}
                title="Hashtag"
                className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors font-mono font-bold text-xs cursor-pointer"
              >
                #
              </button>
              <button
                type="button"
                onClick={insertAtSign}
                title="Mention"
                className="btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10 rounded-md sm:rounded-lg transition-colors font-mono font-bold text-xs cursor-pointer"
              >
                @
              </button>
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                  title="Insert Emoji"
                  className={`btn btn-ghost btn-xs w-6 h-6 sm:w-7 sm:h-7 p-0 rounded-md sm:rounded-lg transition-colors cursor-pointer ${showEmojiPicker ? 'text-[#1D4ED8] bg-[#1D4ED8]/10' : 'text-base-content/70 hover:text-[#1D4ED8] hover:bg-[#1D4ED8]/10'}`}
                >
                  <RiEmotionHappyLine size={13} className="sm:size-3.5" />
                </button>
                {showEmojiPicker && (
                  <>
                    <div
                      className="fixed inset-0 z-40"
                      onClick={() => setShowEmojiPicker(false)}
                    />
                    <div className="absolute left-0 bottom-full mb-1.5 z-50 p-1.5 bg-base-100 border border-base-300 rounded-xl shadow-xl flex gap-1 items-center">
                      {[
                        '😊',
                        '👍',
                        '🔥',
                        '🙌',
                        '💡',
                        '⚠️',
                        '📌',
                        '📢',
                        '👏',
                        '❤️',
                      ].map((emoji) => (
                        <button
                          key={emoji}
                          type="button"
                          onClick={() => insertEmoji(emoji)}
                          className="btn btn-ghost btn-xs w-7 h-7 p-0 text-sm hover:scale-125 transition-transform"
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            </div>

            {/* Character Count */}
            <div className="flex justify-between items-center text-[8.5px] sm:text-[9px] px-1 text-base-content/50 font-bold uppercase tracking-wider">
              <span>Requirement: Max 500 chars</span>
              <span
                className={
                  pollQuestion.length > 500 ? 'text-error font-black' : ''
                }
              >
                {pollQuestion.length}/500
              </span>
            </div>

            {/* Poll Choices (Dynamic List) */}
            <div className="space-y-1.5 sm:space-y-2">
              <div className="flex items-center justify-between">
                <label className="block text-[9.5px] sm:text-[10px] uppercase font-black tracking-widest text-base-content/80 flex items-center gap-1.5">
                  <span>Poll Choices</span>
                  <span className="text-[8px] bg-base-300 px-1.5 py-0.5 rounded-full font-bold">
                    {options.length}/4
                  </span>
                </label>
                <span className="text-[8.5px] sm:text-[9px] text-base-content/40 font-bold uppercase">
                  Min 2 • Max 4
                </span>
              </div>

              {options.map((opt, i) => (
                <div key={i} className="flex items-center gap-1 sm:gap-1.5">
                  <div className="relative flex-1">
                    <input
                      id={`poll-opt-input-${i}`}
                      type="text"
                      className={`input input-xs sm:input-sm w-full bg-base-100/80 border border-base-content/15 dark:border-white/15 focus:border-[#1D4ED8] focus:ring-2 focus:ring-[#1D4ED8]/15 focus:outline-none rounded-xl text-xs sm:text-sm font-medium pl-6 sm:pl-7 h-8 sm:h-9 ${errors[`opt${i}`] ? 'border-red-500/50' : ''}`}
                      placeholder={`Option ${i + 1}`}
                      value={opt}
                      onChange={(e) => updateOption(i, e.target.value)}
                      onFocus={() =>
                        setActiveField({ type: 'option', index: i })
                      }
                      maxLength={100}
                    />
                    <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[9.5px] sm:text-[10px] font-black text-base-content/30 select-none">
                      {i + 1}
                    </span>
                  </div>
                  {options.length > 2 && (
                    <button
                      type="button"
                      className="btn btn-ghost btn-xs btn-circle text-base-content/40 hover:text-error hover:bg-error/10 shrink-0"
                      onClick={() => removeOption(i)}
                    >
                      <X size={12} className="sm:size-3.5" />
                    </button>
                  )}
                </div>
              ))}

              {options.length < 4 && (
                <button
                  type="button"
                  className="w-full py-1.5 border border-dashed border-base-300 hover:border-[#1D4ED8]/50 hover:bg-[#1D4ED8]/5 text-base-content/60 hover:text-[#1D4ED8] rounded-xl text-[9.5px] sm:text-[10px] font-black uppercase tracking-wider flex items-center justify-center gap-1 transition-all duration-200 cursor-pointer"
                  onClick={addOption}
                >
                  <Plus size={11} className="sm:size-3" />
                  <span>Add Option ({4 - options.length} remaining)</span>
                </button>
              )}
            </div>

            {/* ── Duration & Settings Bar (2 Rows) ── */}
            <div className="flex flex-col gap-1.5 sm:gap-2 bg-base-200/50 p-1.5 sm:p-2 rounded-xl sm:rounded-2xl border border-base-300">
              {/* Row 1: Duration Selector */}
              <div className="flex items-center justify-between p-1.5 sm:p-2 rounded-lg sm:rounded-xl bg-base-100/70 border border-base-200/80 gap-2">
                <span className="text-[9px] sm:text-[9.5px] font-black uppercase tracking-wider text-base-content/70 whitespace-nowrap">
                  Duration
                </span>
                <div className="flex items-center gap-1 bg-base-200/80 p-0.5 rounded-lg">
                  {[
                    { val: '1h', label: '1H' },
                    { val: '1d', label: '1D' },
                    { val: '3d', label: '3D' },
                    { val: '7d', label: '7D' },
                    { val: 'always', label: 'Always' },
                  ].map((d) => (
                    <button
                      key={d.val}
                      type="button"
                      onClick={() => setExpiresIn(d.val)}
                      className={`px-1.5 sm:px-2.5 py-0.5 sm:py-1 rounded-md text-[9px] sm:text-[10px] font-black uppercase tracking-tight transition-all duration-150 cursor-pointer ${
                        expiresIn === d.val
                          ? 'bg-[#1D4ED8] text-white shadow-xs'
                          : 'text-base-content/60 hover:text-base-content hover:bg-base-300/60'
                      }`}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Row 2: Multiple Votes Toggle */}
              <div
                onClick={() => setAllowMultipleVotes(!allowMultipleVotes)}
                className="flex items-center justify-between p-2 sm:p-2.5 rounded-lg sm:rounded-xl bg-base-100/70 border border-base-200/80 cursor-pointer select-none hover:border-base-300 transition-colors"
              >
                <span className="text-[9px] sm:text-[9.5px] font-black uppercase tracking-wider text-base-content/70 whitespace-nowrap">
                  Multiple Votes
                </span>
                <div
                  className={`relative w-7 sm:w-8 h-4 sm:h-4.5 rounded-full transition-colors duration-200 border flex items-center px-0.5 ${
                    allowMultipleVotes
                      ? 'bg-black dark:bg-white border-transparent'
                      : 'bg-neutral-200 dark:bg-neutral-800 border-neutral-300 dark:border-neutral-700'
                  }`}
                >
                  <div
                    className={`w-3 sm:w-3.5 h-3 sm:h-3.5 rounded-full shadow-xs transition-transform duration-200 ease-in-out ${
                      allowMultipleVotes
                        ? 'bg-white dark:bg-black translate-x-[12px] sm:translate-x-[14px]'
                        : 'bg-white dark:bg-neutral-400 translate-x-0'
                    }`}
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="flex flex-col justify-start space-y-2.5 sm:space-y-3">
            <div>
              <label className="block text-[9.5px] sm:text-[10px] uppercase font-black tracking-widest text-base-content/80 mb-1 flex items-center gap-1">
                <RiAttachment2 size={12} /> Media Attachment{' '}
                <span className="text-base-content/40 font-bold ml-1 tracking-normal">
                  (optional)
                </span>
              </label>
              <MediaUploadZone
                accent="blue"
                files={files}
                onChange={setFiles}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="shrink-0 px-3.5 sm:px-5 py-2.5 sm:py-3 border-t border-base-content/10 bg-base-100 flex items-center justify-end gap-2">
        <button
          onClick={onClose}
          className="btn btn-xs sm:btn-sm btn-ghost rounded-lg sm:rounded-xl px-3 sm:px-4 text-[11px] sm:text-xs font-bold uppercase tracking-wider text-base-content/70 hover:text-base-content h-8 sm:h-9"
          disabled={loading}
        >
          Cancel
        </button>
        <button
          className="btn btn-xs sm:btn-sm bg-[#1D4ED8] hover:bg-blue-800 text-white min-w-[100px] sm:min-w-[130px] rounded-lg sm:rounded-xl font-bold uppercase tracking-wider text-[10px] sm:text-[11px] shadow-lg shadow-blue-700/20 h-8 sm:h-9 border-none cursor-pointer"
          onClick={handlePost}
          disabled={loading}
        >
          {loading ? (
            <>
              <Loader2 size={13} className="animate-spin" /> Publishing...
            </>
          ) : (
            'Publish Poll'
          )}
        </button>
      </div>
    </div>
  );
}

// ─── MAIN MODAL ───────────────────────────────────────────────────────────────
const CreatePost = ({
  open,
  onClose,
  communityId,
  communityName,
  onPostCreated,
}: Props) => {
  const [type, setType] = useState<PostType>('post');

  const postTypes: { key: PostType; label: string; icon: JSX.Element }[] = [
    {
      key: 'post',
      label: 'Post',
      icon: <FileTypeCorner size={15} className="sm:size-4" />,
    },
    {
      key: 'poll',
      label: 'Poll',
      icon: <BarChart2 size={15} className="sm:size-4" />,
    },
  ];

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[9999] flex items-center justify-center p-2 sm:p-4 bg-black/50 backdrop-blur-sm"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            className="w-full max-w-sm sm:max-w-xl md:max-w-3xl bg-base-100/95 rounded-2xl sm:rounded-3xl border border-black/10 dark:border-white/15 shadow-[0_24px_48px_-12px_rgba(0,0,0,0.4)] h-[540px] max-h-[82dvh] sm:h-[580px] sm:max-h-[85vh] flex flex-col overflow-hidden backdrop-blur-2xl"
            initial={{ scale: 0.97, y: 12 }}
            animate={{ scale: 1, y: 0 }}
            exit={{ scale: 0.97, y: 12 }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="flex items-center justify-between px-3.5 sm:px-5 py-2.5 sm:py-3.5 border-b border-base-content/5 shrink-0 bg-base-100">
              <div>
                <h2 className="font-black text-sm sm:text-base uppercase tracking-tight flex items-center gap-1.5 sm:gap-2 text-base-content">
                  <Plus size={14} className="text-blue-700 sm:size-[15px]" />{' '}
                  {communityId ? `Post to ${communityName}` : 'Create Post'}
                </h2>
                <div className="flex items-center gap-1 sm:gap-1.5 mt-2 bg-base-200/80 p-1 sm:p-1.5 rounded-xl sm:rounded-2xl border border-base-300 w-fit shadow-xs">
                  {postTypes.map((item) => (
                    <button
                      key={item.key}
                      onClick={() => setType(item.key)}
                      className={`flex items-center gap-1.5 sm:gap-2 px-3.5 sm:px-4 py-1.5 sm:py-2 rounded-lg sm:rounded-xl text-xs sm:text-[13px] font-black uppercase tracking-wider transition-all duration-200 cursor-pointer ${
                        type === item.key
                          ? 'bg-[#1D4ED8] text-white shadow-md shadow-[#1D4ED8]/30'
                          : 'text-base-content/60 hover:text-base-content hover:bg-base-300/50'
                      }`}
                    >
                      {item.icon}
                      <span>{item.label}</span>
                    </button>
                  ))}
                </div>
              </div>
              <button
                onClick={onClose}
                className="btn btn-ghost btn-circle btn-xs sm:btn-sm text-base-content/60 hover:text-base-content hover:bg-base-300/50"
              >
                <X size={16} className="sm:size-[18px]" />
              </button>
            </div>

            {/* Body */}
            <div className="flex-1 min-h-0 flex flex-col overflow-hidden bg-base-100">
              {type === 'post' && (
                <PostForm
                  onClose={onClose}
                  communityId={communityId}
                  onPostCreated={onPostCreated}
                />
              )}
              {type === 'poll' && (
                <PollForm
                  communityId={communityId}
                  onClose={onClose}
                  onPostCreated={onPostCreated}
                />
              )}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  );
};

export default CreatePost;
