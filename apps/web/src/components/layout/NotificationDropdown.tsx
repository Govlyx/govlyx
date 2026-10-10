import { Bell } from "lucide-react";
import { useNavigate } from "react-router-dom";

const NotificationDropdown = ({ unreadCount }: { unreadCount: number, onRefresh: () => void }) => {
  const navigate = useNavigate();

  return (
    <button
      onClick={() => navigate("/notifications")}
      className="btn btn-ghost btn-sm hover:bg-blue-700/10 relative"
      aria-label="Notifications"
    >
      <Bell size={18} />
      {unreadCount > 0 && (
        <span className="absolute -top-1 -right-1 bg-error text-error-content text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
          {unreadCount > 9 ? "9+" : unreadCount}
        </span>
      )}
    </button>
  );
};

export default NotificationDropdown;
