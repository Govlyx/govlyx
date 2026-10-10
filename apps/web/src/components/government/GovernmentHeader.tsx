import { Building2, ShieldCheck } from "lucide-react";

type GovernmentHeaderProps = {
  department: string;
  description: string;
};

const GovernmentHeader = ({
  department,
  description,
}: GovernmentHeaderProps) => {
  return (
    <div className="rounded-xl border border-blue-700/30 bg-blue-700/5 p-4">
      <div className="flex items-start gap-3">
        <div className="rounded-lg bg-blue-700 p-2 text-white">
          <Building2 size={20} />
        </div>

        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-semibold">{department}</h1>
            <ShieldCheck size={16} className="text-blue-700" />
          </div>

          <p className="mt-1 text-sm opacity-70">
            {description}
          </p>
        </div>
      </div>
    </div>
  );
};

export default GovernmentHeader;
