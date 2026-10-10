import { motion } from "framer-motion";

const dots = Array.from({ length: 9 });

const PulseLoader = () => {
  return (
    <div className="flex w-full justify-center py-12">
      <div className="grid grid-cols-3 gap-2">
        {dots.map((_, i) => (
          <motion.div
            key={i}
            className="h-10 w-10 rounded bg-blue-700"
            animate={{
              opacity: [0.3, 1, 0.3],
              scale: [0.5, 1, 0.8],
            }}
            transition={{
              duration: 1,
              repeat: Infinity,
              delay: i * 0.2,
              ease: "easeInOut",
            }}
          />
        ))}
      </div>
    </div>
  );
};

export default PulseLoader;
