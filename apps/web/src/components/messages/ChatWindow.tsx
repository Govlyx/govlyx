import { useEffect, useRef } from "react";
import MessageBubble from "./MessageBubble";
import MessageInput from "./MessageInput";
import EmptyState from "../ui/EmptyState";

type Message = {
  id: number;
  text: string;
  isOwn: boolean;
  time: string;
};

type Conversation = {
  name: string;
  messages: Message[];
};

const ChatWindow = ({
  conversation,
}: {
  conversation?: Conversation;
}) => {
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [conversation?.messages.length]);

  if (!conversation) {
    return (
      <div className="h-full flex items-center justify-center">
        <EmptyState
          title="No conversation selected"
          description="Choose a conversation to start chatting."
        />
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">

      {/* Header */}
      <div className="border-b border-base-300 px-4 py-3">
        <h3 className="text-sm font-semibold notranslate">{conversation.name}</h3>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4">
        {conversation.messages.map((msg, index) => {
          const prev = conversation.messages[index - 1];
          const showAvatar = !prev || prev.isOwn !== msg.isOwn;

          return (
            <MessageBubble
              key={msg.id}
              message={msg.text}
              isOwn={msg.isOwn}
              showAvatar={showAvatar}
              time={msg.time}
            />
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <MessageInput />
    </div>
  );
};

export default ChatWindow;
