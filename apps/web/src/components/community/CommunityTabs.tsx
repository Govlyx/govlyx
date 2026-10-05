type CommunityTabsProps = {
  active: 'posts' | 'about' | 'chat' | 'manage';
  onChange: (tab: 'posts' | 'about' | 'chat' | 'manage') => void;
  onActiveClick?: (tab: 'posts' | 'about' | 'chat' | 'manage') => void;
  showManage?: boolean;
};

const CommunityTabs = ({
  active,
  onChange,
  onActiveClick,
  showManage,
}: CommunityTabsProps) => {
  const tabs = ['posts', 'chat', 'about'];
  if (showManage) {
    tabs.push('manage');
  }

  const handleClick = (tab: 'posts' | 'about' | 'chat' | 'manage') => {
    if (active === tab) {
      if (onActiveClick) {
        onActiveClick(tab);
      } else {
        onChange(tab);
      }
    } else {
      onChange(tab);
    }
  };

  const handleDoubleClick = (tab: 'posts' | 'about' | 'chat' | 'manage') => {
    if (onActiveClick) {
      onActiveClick(tab);
    } else {
      onChange(tab);
    }
  };

  return (
    <div className="flex gap-2 border-b border-base-300 overflow-x-auto scrollbar-hide">
      {tabs.map((tab) => (
        <button
          key={tab}
          onClick={() =>
            handleClick(tab as 'posts' | 'chat' | 'about' | 'manage')
          }
          onDoubleClick={() =>
            handleDoubleClick(tab as 'posts' | 'chat' | 'about' | 'manage')
          }
          className={`shrink-0 px-4 py-2 text-sm font-medium capitalize cursor-pointer transition-all ${
            active === tab
              ? 'border-b-2 border-red-500 text-red-500 font-bold'
              : 'opacity-70 hover:opacity-100'
          }`}
        >
          {tab}
        </button>
      ))}
    </div>
  );
};

export default CommunityTabs;
