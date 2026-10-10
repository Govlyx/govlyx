import { Search } from "lucide-react";

type Props = {
  placeholder?: string;
};

const SearchBar = ({ placeholder = "Search posts, communities…" }: Props) => {
  return (
    <div className="relative w-full">
      <Search
        size={16}
        className="absolute left-3 top-1/2 -translate-y-1/2 opacity-60"
      />
      <input
        type="text"
        placeholder={placeholder}
        className="input input-bordered w-full pl-9 focus:border-blue-700"
      />
    </div>
  );
};

export default SearchBar;
