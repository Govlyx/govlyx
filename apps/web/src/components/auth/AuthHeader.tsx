import GovlyxLogo from "../ui/GovlyxLogo";

type Props = {
  title: string;
  subtitle: string;
  hideLogoOnDesktop?: boolean;
};

const AuthHeader = ({ title, subtitle, hideLogoOnDesktop = true }: Props) => {
  return (
    <div className="mb-3.5 sm:mb-4 text-center lg:text-left">
      {/* Logo - visible on mobile, hidden on desktop when split screen is active */}
      <div className={hideLogoOnDesktop ? "lg:hidden flex justify-center mb-2.5" : "flex justify-center lg:justify-start mb-2.5"}>
        <GovlyxLogo size={48} />
      </div>

      {/* Title */}
      <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-base-content leading-tight">
        {title}
      </h1>

      {/* Subtitle */}
      <p className="mt-1 text-xs sm:text-sm opacity-75 font-medium">
        {subtitle}
      </p>
    </div>
  );
};

export default AuthHeader;
