import { Info, ShieldCheck } from "lucide-react";

const CommunitySidebar = () => {
  return (
    <div className="space-y-4">

      {/* About card */}
      <div className="rounded-xl border border-base-300 bg-base-200 p-4">
        <div className="flex items-center gap-2 mb-2">
          <Info size={16} />
          <h3 className="font-semibold text-sm">About</h3>
        </div>

        <p className="text-sm opacity-70">
          This community is focused on local discussions, updates,
          and helpful resources shared by members.
        </p>
      </div>

      {/* Rules / Safety */}
      <div className="rounded-xl border border-base-300 bg-base-200 p-4">
        <div className="flex items-center gap-2 mb-2">
          <ShieldCheck size={16} />
          <h3 className="font-semibold text-sm">Community Rules</h3>
        </div>

        <ul className="text-sm opacity-70 space-y-1 list-disc list-inside">
          <li>Be respectful</li>
          <li>No spam or ads</li>
          <li>Follow community guidelines</li>
        </ul>
      </div>

    </div>
  );
};

export default CommunitySidebar;
