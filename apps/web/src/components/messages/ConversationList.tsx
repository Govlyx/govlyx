import ConversationItem from "./ConversationItem";

type Conversation = {
  id: number;
  name: string;
  messages: any[];
};

type Props = {
  conversations: Conversation[];
  activeId: number | null;
  onSelect: (id: number) => void;
};

const ConversationList = ({ conversations, activeId, onSelect }: Props) => {
  return (
    <div className="h-full flex flex-col">

      <div className="p-3 border-b border-base-300">
        <h2 className="text-sm font-semibold">Messages</h2>
      </div>

      <div className="flex-1 overflow-y-auto">
        {conversations.map((conv) => (
          <ConversationItem
            key={conv.id}
            name={conv.name}
            lastMessage={conv.messages.at(-1)?.text}
            isActive={conv.id === activeId}
            onClick={() => onSelect(conv.id)}
          />
        ))}
      </div>

    </div>
  );
};

export default ConversationList;
