import { useState, useRef, useEffect } from "react";

interface Props {
  src: string;
  alt: string;
  className?: string;
  width?: number;
  onError?: () => void;
}

export default function OptimizedImage({ src, alt, className = "", width = 600, onError }: Props) {
  const [loaded, setLoaded] = useState(false);
  const [inView, setInView] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting) {
          setInView(true);
          observer.disconnect();
        }
      },
      { rootMargin: "300px" }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  if (!src) return null;

  const isCloudinary = src.includes("cloudinary.com") || src.includes("res.cloudinary");
  const thumbUrl = isCloudinary
    ? src.replace("/upload/", "/upload/w_40,q_10,e_blur:1000/")
    : undefined;
  const optimizedSrc = isCloudinary
    ? src.replace("/upload/", `/upload/w_${width},q_auto,f_auto/`)
    : src;

  return (
    <div ref={ref} className={`relative overflow-hidden ${className}`}>
      {thumbUrl && !loaded && (
        <img
          src={thumbUrl}
          alt=""
          className="absolute inset-0 w-full h-full object-cover scale-110 blur-xl"
          aria-hidden
        />
      )}
      {inView && (
        <img
          src={optimizedSrc}
          alt={alt}
          onLoad={() => setLoaded(true)}
          onError={onError}
          className={`w-full h-full object-cover transition-opacity duration-500 ${
            loaded ? "opacity-100" : "opacity-0"
          }`}
        />
      )}
    </div>
  );
}
