import { useState } from "react";
import ConversationList from "../components/messages/ConversationList";
import ChatWindow from "../components/messages/ChatWindow";

const mockConversations = [
  {
    id: 1,
    name: "Anonymous #4821",
    messages: [
      { id: 1, text: "Tu Merse Pyar Karta Hai Ya nahi?", isOwn: false, time: "10:24 PM" },
      { id: 2, text: "Teri MKC?", isOwn: true, time: "10:25 PM" },
      {
        id: 3,
        text: "Chicken Cooked Well",
        isOwn: false,
        time: "10:26 PM",
      },
    ],
  },
  {
    id: 2,
    name: "Anonymous #2913",
    messages: [
      { id: 1, text: "Hey, are you online?", isOwn: false, time: "10:24 PM" },
      { id: 2, text: "Yes, what's up?", isOwn: true, time: "10:25 PM" },
      {
        id: 3,
        text: "Needed help with a form.",
        isOwn: false,
        time: "10:26 PM",
      },
    ],
  },
];

const Messages = () => {
  const [activeId, setActiveId] = useState<number | null>(null);

  const activeConversation = mockConversations.find((c) => c.id === activeId);

  return (
    <div className="h-full grid grid-cols-12 gap-4">
      {/* LEFT: Conversations */}
      <aside className="col-span-12 md:col-span-4 lg:col-span-3 h-full border-r border-base-300">
        <ConversationList
          conversations={mockConversations}
          activeId={activeId}
          onSelect={setActiveId}
        />
      </aside>

      {/* RIGHT: Chat */}
      <main className="col-span-12 md:col-span-8 lg:col-span-9 h-full">
        <ChatWindow conversation={activeConversation} />
      </main>
    </div>
  );
};

export default Messages;
