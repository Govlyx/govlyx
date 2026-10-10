import React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export default function MarkdownRenderer({ content, className = "" }: MarkdownRendererProps) {
  // Pre-process hashtags to markdown link format so they can be styled specially.
  // We match #hashtag and turn it into [#hashtag](hashtag:hashtag)
  const processedContent = React.useMemo(() => {
    if (!content) return "";
    return content.replace(
      /(?<!\S)#([a-zA-Z0-9_\u00C0-\u00FF\u0100-\u017F\u0400-\u04FF]+)/g,
      "[#$1](hashtag:$1)"
    );
  }, [content]);

  return (
    <div className={`markdown-content select-text ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          p: ({ children }) => (
            <p className="whitespace-pre-wrap leading-relaxed mb-2 last:mb-0">
              {children}
            </p>
          ),
          li: ({ children }) => (
            <li className="whitespace-pre-wrap list-disc ml-4">
              {children}
            </li>
          ),
          ul: ({ children }) => (
            <ul className="list-disc my-2">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal my-2">
              {children}
            </ol>
          ),
          a: ({ href, children, ...props }) => {
            if (href?.startsWith("hashtag:")) {
              return (
                <span className="inline-block px-1.5 py-0.5 mx-0.5 rounded-md bg-[#1D4ED8] text-white font-bold text-xs shadow-xs transition-all hover:bg-blue-800 notranslate select-text">
                  {children}
                </span>
              );
            }
            return (
              <a
                href={href}
                className="text-blue-600 hover:underline"
                target="_blank"
                rel="noopener noreferrer"
                {...props}
              >
                {children}
              </a>
            );
          },
        }}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  );
}
