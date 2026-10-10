/**
 * Dynamically loads the Razorpay checkout script.
 * Returns a promise that resolves when the script is loaded successfully,
 * or rejects if it fails.
 */
export const loadRazorpayScript = (): Promise<boolean> => {
  return new Promise((resolve) => {
    // If Razorpay script is already present on the page, resolve immediately
    if (window.hasOwnProperty("Razorpay")) {
      resolve(true);
      return;
    }

    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => {
      resolve(true);
    };
    script.onerror = () => {
      resolve(false);
    };
    document.body.appendChild(script);
  });
};
