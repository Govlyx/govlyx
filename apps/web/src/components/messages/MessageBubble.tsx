type MessageBubbleProps = {
  message: string;
  isOwn?: boolean;
  showAvatar?: boolean;
  time?: string;
};

const MessageBubble = ({
  message,
  isOwn,
  showAvatar = true,
  time,
}: MessageBubbleProps) => {
  return (
    <div className={`flex ${isOwn ? "justify-end" : "justify-start"}`}>
      <div className="max-w-[70%]">
        <div
          className={`rounded-xl px-3 py-2 text-sm notranslate ${
            isOwn
              ? "bg-blue-700 text-white"
              : "bg-base-200"
          } ${!showAvatar ? "mt-1" : "mt-3"}`}
        >
          {message}
        </div>

        {time && (
          <div
            className={`mt-1 text-xs opacity-50 ${
              isOwn ? "text-right" : "text-left"
            }`}
          >
            {time}
          </div>
        )}
      </div>
    </div>
  );
};

export default MessageBubble;
