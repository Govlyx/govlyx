import { Flame, Clock, ArrowUp, MapPin } from "lucide-react";
import { useState, type JSX } from "react";

type ScopeTab = "all" | "location" | "following";
type SortTab = "hot" | "new" | "top";

type Props = {
  active: ScopeTab;
};

const FeedFilterBar = (_props: Props) => {
  const [sort, setSort] = useState<SortTab>("hot");
  const [change,setChange] = useState<ScopeTab>("all");

  const leftScope: { key: ScopeTab; label: string }[] = [
    { key: "all", label: "For You" },
    { key: "location", label: "Location" },
    { key: "following", label: "Following" },
  ];

  const sortTabs: { key: SortTab; label: string; icon: JSX.Element }[] = [
    { key: "hot", label: "Hot", icon: <Flame size={16} /> },
    { key: "new", label: "New", icon: <Clock size={16} /> },
    { key: "top", label: "Top", icon: <ArrowUp size={16} /> },
  ];

  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-base-200 p-3">

      {/* LEFT: Feed scope */}
      <div className="flex flex-wrap gap-2">
        {leftScope.map((item) => (
          <button
            key={item.key}
            onClick={() => setChange(item.key)}
            className={`btn btn-sm ${
              change === item.key
                ? "bg-blue-700 text-white"
                : "btn-ghost"
            } focus:outline-none`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {/* CENTER: Pincode filter */}
      <div className="flex items-center gap-2">
        <MapPin size={16} className="opacity-60" />
        <input
          type="text"
          placeholder="Pincode"
          className="input input-sm input-bordered w-28 focus:border-blue-700"
        />
      </div>

      {/* RIGHT: Sort */}
      <div className="flex gap-2">
        {sortTabs.map((item) => (
          <button
            key={item.key}
            onClick={() => setSort(item.key)}
            className={`btn btn-sm gap-1 ${
              sort === item.key
                ? "bg-blue-700 text-white"
                : "btn-ghost"
            } focus:outline-none`}
          >
            {item.icon}
            {item.label}
          </button>
        ))}
      </div>

    </div>
  );
};

export default FeedFilterBar;
