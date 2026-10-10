import { createContext, useContext, useState, useCallback, type ReactNode } from "react";

interface ModalContextValue {
  isAnyModalOpen: boolean;
  openModal: () => void;
  closeModal: () => void;
}

const ModalContext = createContext<ModalContextValue>({
  isAnyModalOpen: false,
  openModal: () => {},
  closeModal: () => {},
});

export const ModalProvider = ({ children }: { children: ReactNode }) => {
  const [count, setCount] = useState(0);

  const openModal = useCallback(() => setCount((c) => c + 1), []);
  const closeModal = useCallback(() => setCount((c) => Math.max(0, c - 1)), []);

  return (
    <ModalContext.Provider value={{ isAnyModalOpen: count > 0, openModal, closeModal }}>
      {children}
    </ModalContext.Provider>
  );
};

export const useModal = () => useContext(ModalContext);
