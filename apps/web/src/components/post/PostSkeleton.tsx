import Skeleton from "../ui/Skeleton";

const PostSkeleton = () => {
  return (
    <div className="w-full rounded-3xl border border-base-300 bg-base-200 p-6 space-y-4 animate-pulse shadow-sm flex flex-col justify-between">
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <Skeleton className="h-10 w-10 rounded-full" />
          <div className="space-y-1.5 flex-1">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/4 opacity-50" />
          </div>
        </div>
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-5/6" />
        <Skeleton className="h-4 w-4/6 opacity-70" />
      </div>
      <div className="flex gap-4 pt-4 border-t border-base-300/50">
        <Skeleton className="h-8 w-16 rounded-lg" />
        <Skeleton className="h-8 w-16 rounded-lg" />
        <Skeleton className="h-8 w-16 rounded-lg" />
      </div>
    </div>
  );
};

export default PostSkeleton;
