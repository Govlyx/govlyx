type Props = {
  label: string;
  type?: "text" | "email" | "password" | "number";
  placeholder?: string;
  helperText?: string;
  // Added for form control
  name?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
};

const AuthInput = ({
  label,
  type = "text",
  placeholder,
  helperText,
  name,
  value,
  onChange,
}: Props) => {
  return (
    <div className="space-y-0.5">
      {/* Label */}
      <label className="text-xs sm:text-sm font-semibold opacity-85 block">
        {label}
      </label>

      {/* Input */}
      <input
        type={type}
        placeholder={placeholder}
        name={name}
        value={value}
        onChange={onChange}
        className="input input-bordered w-full h-10.5 sm:h-11 text-xs sm:text-sm px-3.5 rounded-xl focus:border-blue-700 focus:outline-none"
      />

      {/* Helper text (optional) */}
      {helperText && (
        <p className="text-[11px] sm:text-xs opacity-60 mt-0.5">
          {helperText}
        </p>
      )}
    </div>
  );
};

export default AuthInput;