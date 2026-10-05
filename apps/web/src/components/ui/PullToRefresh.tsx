import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Loader2, ArrowDown } from 'lucide-react';

export interface PullToRefreshProps {
  children: React.ReactNode;
  onRefresh: () => Promise<any> | void;
  className?: string;
  pullDownThreshold?: number; // Threshold in px to trigger refresh (default 55)
  maxPullDownDistance?: number; // Maximum visual pull distance (default 80)
  disabled?: boolean;
}

export const PullToRefresh: React.FC<PullToRefreshProps> = ({
  children,
  onRefresh,
  className = '',
  pullDownThreshold = 55,
  maxPullDownDistance = 80,
  disabled = false,
}) => {
  const [pullDistance, setPullDistance] = useState<number>(0);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const startYRef = useRef<number>(0);
  const startXRef = useRef<number>(0);
  const canPullRef = useRef<boolean>(false);
  const isRefreshingRef = useRef<boolean>(false);
  const onRefreshRef = useRef(onRefresh);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    isRefreshingRef.current = isRefreshing;
  }, [isRefreshing]);

  // Helper to find the active scrollable parent
  const getScrollParent = useCallback((): HTMLElement | Window => {
    if (!containerRef.current) return window;
    const closestScrollable = containerRef.current.closest<HTMLElement>(
      'main.overflow-y-auto, .overflow-y-auto',
    );
    if (closestScrollable) return closestScrollable;
    return window;
  }, []);

  const getScrollTop = useCallback((): number => {
    const scrollParent = getScrollParent();
    if (scrollParent === window) {
      return window.scrollY || document.documentElement.scrollTop || 0;
    }
    return (scrollParent as HTMLElement).scrollTop || 0;
  }, [getScrollParent]);

  const handleRefresh = useCallback(async () => {
    if (isRefreshingRef.current) return;
    setIsRefreshing(true);
    isRefreshingRef.current = true;
    setPullDistance(50); // Hold at comfortable spinner height

    try {
      if (onRefreshRef.current) {
        await onRefreshRef.current();
      }
    } catch (err) {
      console.error('PullToRefresh error:', err);
    } finally {
      setIsRefreshing(false);
      isRefreshingRef.current = false;
      setPullDistance(0);
    }
  }, []);

  // Touch Event Listeners
  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    if (disabled || isRefreshingRef.current) return;

    const scrollTop = getScrollTop();
    if (scrollTop <= 1) {
      canPullRef.current = true;
      startYRef.current = e.touches[0].clientY;
      startXRef.current = e.touches[0].clientX;
    } else {
      canPullRef.current = false;
    }
  };

  const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    if (!canPullRef.current || disabled || isRefreshingRef.current) return;

    const currentY = e.touches[0].clientY;
    const currentX = e.touches[0].clientX;
    const deltaY = currentY - startYRef.current;
    const deltaX = Math.abs(currentX - startXRef.current);

    // If horizontal swipe is greater than vertical, don't hijack gesture
    if (deltaX > Math.abs(deltaY) && pullDistance === 0) {
      canPullRef.current = false;
      return;
    }

    if (deltaY > 0) {
      // Check again if scroll container is at top
      if (getScrollTop() <= 1) {
        setIsDragging(true);
        // Rubber-band resistance formula: maxDistance * (1 - e^(-deltaY / factor))
        const resistanceFactor = 160;
        const calculatedDistance = Math.min(
          maxPullDownDistance,
          maxPullDownDistance * (1 - Math.exp(-deltaY / resistanceFactor)),
        );

        setPullDistance(calculatedDistance);
        if (e.cancelable) {
          e.preventDefault();
        }
      } else {
        canPullRef.current = false;
        setIsDragging(false);
        setPullDistance(0);
      }
    } else {
      setIsDragging(false);
      setPullDistance(0);
    }
  };

  const handleTouchEnd = () => {
    if (!canPullRef.current || disabled || isRefreshingRef.current) return;

    canPullRef.current = false;
    setIsDragging(false);

    if (pullDistance >= pullDownThreshold) {
      handleRefresh();
    } else {
      setPullDistance(0);
    }
  };

  const progressRatio = Math.min(1, pullDistance / pullDownThreshold);
  const isTriggerable = pullDistance >= pullDownThreshold;

  return (
    <div
      ref={containerRef}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={handleTouchEnd}
      style={{
        overscrollBehaviorY: 'contain',
      }}
      className={`relative w-full ${className}`}
    >
      {/* Pull down indicator container */}
      <div
        className="pointer-events-none absolute left-0 right-0 top-0 z-40 flex items-center justify-center overflow-hidden transition-all"
        style={{
          height: `${Math.max(pullDistance, isRefreshing ? 50 : 0)}px`,
          opacity: pullDistance > 8 || isRefreshing ? 1 : 0,
          transition: isDragging
            ? 'none'
            : 'height 0.3s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.2s ease-out',
        }}
      >
        <div
          className={`flex items-center justify-center gap-2 rounded-full px-3.5 py-1.5 shadow-md border backdrop-blur-md transition-transform duration-200 ${
            isTriggerable || isRefreshing
              ? 'bg-[#1D4ED8] text-white border-blue-600/40 shadow-blue-500/20'
              : 'bg-base-100/90 text-base-content/80 border-base-300 shadow-black/5'
          }`}
          style={{
            transform: `scale(${isRefreshing ? 1 : 0.7 + progressRatio * 0.3})`,
          }}
        >
          {isRefreshing ? (
            <>
              <Loader2 size={16} className="animate-spin text-white" />
              <span className="text-xs font-bold text-white">
                Refreshing feed...
              </span>
            </>
          ) : (
            <>
              <ArrowDown
                size={15}
                className={`transition-transform duration-200 ${
                  isTriggerable ? 'rotate-180 text-white' : ''
                }`}
                style={{
                  transform: isTriggerable
                    ? 'rotate(180deg)'
                    : `rotate(${progressRatio * 180}deg)`,
                }}
              />
              <span className="text-xs font-semibold">
                {isTriggerable ? 'Release to refresh' : 'Pull to refresh'}
              </span>
            </>
          )}
        </div>
      </div>

      {/* Main Content with smooth transform */}
      <div
        style={{
          transform: `translate3d(0, ${pullDistance}px, 0)`,
          transition: isDragging
            ? 'none'
            : 'transform 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        }}
        className="w-full"
      >
        {children}
      </div>
    </div>
  );
};

export default PullToRefresh;
