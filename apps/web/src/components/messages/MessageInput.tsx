import { Send } from "lucide-react";

const MessageInput = () => {
  return (
    <div className="p-3 bg-base-100 border-t border-base-content/5">
      <div className="flex items-center gap-2 max-w-4xl mx-auto">
        <div className="flex-1 min-w-0 bg-base-200/50 rounded-2xl border border-base-content/5 px-4 py-1.5 transition-all focus-within:ring-2 focus-within:ring-blue-700/20 focus-within:border-blue-700/30">
          <input
            type="text"
            placeholder="Type a message…"
            className="w-full bg-transparent border-none outline-none text-sm placeholder:text-base-content/30 py-1"
          />
        </div>
        <button className="btn btn-circle btn-sm bg-blue-700 border-none text-white hover:bg-blue-800 shadow-lg shadow-blue-700/20 transition-all active:scale-95">
          <Send size={14} className="ml-0.5" />
        </button>
      </div>
    </div>
  );
};

export default MessageInput;
