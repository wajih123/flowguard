import React, { useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Modal } from "./Modal";
import { Button } from "./Button";

interface ConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  requireTyping?: string; // if set, user must type this word to confirm
  isLoading?: boolean;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel = "Confirmer",
  cancelLabel = "Annuler",
  danger = false,
  requireTyping,
  isLoading = false,
}) => {
  const [typedValue, setTypedValue] = useState("");

  const canConfirm = requireTyping ? typedValue === requireTyping : true;

  const handleClose = () => {
    setTypedValue("");
    onClose();
  };

  const handleConfirm = async () => {
    if (!canConfirm) return;
    await onConfirm();
    setTypedValue("");
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={title} size="sm">
      <div className="space-y-4">
        {danger && (
          <div className="flex items-center gap-3 p-3 rounded-lg bg-danger/10 border border-danger/20">
            <AlertTriangle size={18} className="text-danger shrink-0" />
            <p className="text-sm text-danger">{description}</p>
          </div>
        )}
        {!danger && (
          <p className="text-sm text-text-secondary">{description}</p>
        )}

        {requireTyping && (
          <div>
            <p className="text-xs text-text-secondary mb-1.5">
              Tapez{" "}
              <span className="font-mono text-white font-medium">
                {requireTyping}
              </span>{" "}
              pour confirmer
            </p>
            <input
              type="text"
              value={typedValue}
              onChange={(e) => setTypedValue(e.target.value)}
              className="w-full px-3 py-2 bg-white/[0.06] border border-white/[0.10] rounded-lg text-white text-sm placeholder-text-muted focus:outline-none focus:border-primary/60 transition-colors"
              placeholder={requireTyping}
              autoComplete="off"
            />
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleClose}
            disabled={isLoading}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={danger ? "danger" : "primary"}
            size="sm"
            onClick={handleConfirm}
            disabled={!canConfirm}
            isLoading={isLoading}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
};
