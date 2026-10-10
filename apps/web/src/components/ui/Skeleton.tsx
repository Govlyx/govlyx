type Props = {
  className?: string;
};

const Skeleton = ({ className = "" }: Props) => {
  return (
    <div
      className={`animate-pulse rounded-md bg-base-300 ${className}`}
    />
  );
};

export default Skeleton;
