import { useEffect } from "react";
import type { ReactNode } from "react";
import { XIcon } from "./icons";

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  // 기본 모달보다 넓게 써야 하는 화면(예: 상세조회)에서만 "modal-lg" 등을 추가로 전달한다.
  className?: string;
}

export function Modal({ title, onClose, children, className }: ModalProps) {
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className={["modal", "card", className].filter(Boolean).join(" ")}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="닫기">
            <XIcon />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
