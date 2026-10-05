import { motion } from 'framer-motion';
import { Activity, AlertCircle, CheckCircle2, HelpCircle } from 'lucide-react';
import type { AreaPulseDto } from '../../api/sidebarService';

interface Props {
  data?: AreaPulseDto;
}

const AreaPulseWidget = ({ data }: Props) => {
  if (!data) return null;

  const stats = [
    {
      label: 'Total Issues',
      value: data.totalIssuesThisWeek,
      icon: AlertCircle,
      color: 'text-white bg-[#1D4ED8]',
      borderColor: 'border-base-content/5',
    },
    {
      label: 'Resolved',
      value: data.resolvedIssuesThisWeek,
      icon: CheckCircle2,
      color: 'text-white bg-[#1D4ED8]',
      borderColor: 'border-base-content/5',
    },
    {
      label: 'Unanswered',
      value: data.unansweredQuestions,
      icon: HelpCircle,
      color: 'text-white bg-[#1D4ED8]',
      borderColor: 'border-base-content/5',
    },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-2.5 rounded-xl bg-base-200 shadow-sm border border-base-content/5 relative overflow-hidden group w-full"
    >
      <div className="relative z-10 flex flex-col gap-1.5">
        <div className="flex items-center gap-1.5 border-b border-base-content/5 pb-1">
          <div className="w-5 h-5 rounded-md bg-[#1D4ED8] flex items-center justify-center text-white shrink-0">
            <Activity size={12} className="animate-pulse" />
          </div>
          <span className="text-[10px] font-black text-base-content uppercase tracking-[0.15em]">
            Area Pulse
          </span>
        </div>

        <div className="flex flex-col gap-1">
          {stats.map((stat, i) => {
            const Icon = stat.icon;
            return (
              <div
                key={i}
                className={`flex items-center justify-between py-1.5 px-2 rounded-lg bg-base-200/40 border ${stat.borderColor} hover:bg-base-200/70 transition-colors w-full`}
              >
                <div className="flex items-center gap-2">
                  <div
                    className={`w-5 h-5 rounded-md flex items-center justify-center ${stat.color}`}
                  >
                    <Icon size={12} />
                  </div>
                  <span className="text-[10px] font-bold text-base-content/85 uppercase tracking-wider">
                    {stat.label}
                  </span>
                </div>
                <span className="text-[13px] font-black text-base-content px-1">
                  {stat.value}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </motion.div>
  );
};

export default AreaPulseWidget;
