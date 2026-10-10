type Props = {
  name: string;
  lastMessage?: string;
  isActive?: boolean;
  onClick: () => void;
};

const ConversationItem = ({
  name,
  lastMessage,
  isActive,
  onClick,
}: Props) => {
  return (
    <div
      onClick={onClick}
      className={`cursor-pointer px-3 py-2 ${
        isActive
          ? "bg-blue-700/10"
          : "hover:bg-blue-700/5"
      }`}
    >
      <div className="text-sm font-medium notranslate">{name}</div>
      <p className="text-sm opacity-70 truncate notranslate">
        {lastMessage}
      </p>
    </div>
  );
};

export default ConversationItem;
