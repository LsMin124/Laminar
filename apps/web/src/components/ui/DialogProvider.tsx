import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import "./DialogProvider.css";

interface PromptOpts {
  title: string;
  message?: string;
  placeholder?: string;
  defaultValue?: string;
  confirmLabel?: string;
}
interface ConfirmOpts {
  title: string;
  message?: string;
  confirmLabel?: string;
  danger?: boolean;
}
interface AlertOpts {
  title: string;
  message?: string;
}

type Pending =
  | { kind: "prompt"; opts: PromptOpts; resolve: (v: string | null) => void }
  | { kind: "confirm"; opts: ConfirmOpts; resolve: (v: boolean) => void }
  | { kind: "alert"; opts: AlertOpts; resolve: () => void };

interface DialogApi {
  prompt: (opts: PromptOpts | string) => Promise<string | null>;
  confirm: (opts: ConfirmOpts | string) => Promise<boolean>;
  alert: (opts: AlertOpts | string) => Promise<void>;
}

const DialogContext = createContext<DialogApi | null>(null);

// eslint-disable-next-line react-refresh/only-export-components
export function useDialogs(): DialogApi {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error("useDialogs must be used within DialogProvider");
  return ctx;
}

/**
 * 앱 전역 다이얼로그(prompt/confirm/alert) — 브라우저 기본 팝업 대체.
 * Promise 기반이라 호출부는 `const v = await prompt("제목")`처럼 거의 그대로 쓴다.
 */
export function DialogProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<Pending | null>(null);
  const [value, setValue] = useState("");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  const api = useMemo<DialogApi>(
    () => ({
      prompt: (o) =>
        new Promise((resolve) => {
          const opts = typeof o === "string" ? { title: o } : o;
          setValue(opts.defaultValue ?? "");
          setPending({ kind: "prompt", opts, resolve });
        }),
      confirm: (o) =>
        new Promise((resolve) => {
          const opts = typeof o === "string" ? { title: o } : o;
          setPending({ kind: "confirm", opts, resolve });
        }),
      alert: (o) =>
        new Promise((resolve) => {
          const opts = typeof o === "string" ? { title: o } : o;
          setPending({ kind: "alert", opts, resolve });
        }),
    }),
    [],
  );

  useEffect(() => {
    const d = dialogRef.current;
    if (!d) return;
    if (pending && !d.open) d.showModal();
    if (!pending && d.open) d.close();
    if (pending) {
      requestAnimationFrame(() => {
        if (pending.kind === "prompt") inputRef.current?.focus();
        else confirmRef.current?.focus();
      });
    }
  }, [pending]);

  const cancel = useCallback(() => {
    setPending((p) => {
      if (!p) return null;
      if (p.kind === "prompt") p.resolve(null);
      else if (p.kind === "confirm") p.resolve(false);
      else p.resolve();
      return null;
    });
  }, []);

  const accept = useCallback(() => {
    setPending((p) => {
      if (!p) return null;
      if (p.kind === "prompt") p.resolve(value);
      else if (p.kind === "confirm") p.resolve(true);
      else p.resolve();
      return null;
    });
  }, [value]);

  useEffect(() => {
    const d = dialogRef.current;
    if (!d) return;
    function onCancel(e: Event) {
      e.preventDefault();
      cancel();
    }
    d.addEventListener("cancel", onCancel);
    return () => d.removeEventListener("cancel", onCancel);
  }, [cancel]);

  const confirmLabel =
    pending && pending.kind !== "alert"
      ? (pending.opts.confirmLabel ?? "확인")
      : "확인";

  return (
    <DialogContext.Provider value={api}>
      {children}
      <dialog
        ref={dialogRef}
        className="app-dialog"
        onClick={(e) => {
          if (e.target === dialogRef.current) cancel();
        }}
      >
        {pending && (
          <form
            className="app-dialog-inner"
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault();
              accept();
            }}
          >
            <h2 className="app-dialog-title">{pending.opts.title}</h2>
            {pending.opts.message && (
              <p className="app-dialog-msg">{pending.opts.message}</p>
            )}
            {pending.kind === "prompt" && (
              <input
                ref={inputRef}
                className="app-dialog-input"
                value={value}
                placeholder={pending.opts.placeholder}
                onChange={(e) => setValue(e.target.value)}
              />
            )}
            <div className="app-dialog-actions">
              {pending.kind !== "alert" && (
                <button
                  type="button"
                  className="app-dialog-btn cancel"
                  onClick={cancel}
                >
                  취소
                </button>
              )}
              <button
                ref={confirmRef}
                type="submit"
                className={`app-dialog-btn confirm${
                  pending.kind === "confirm" && pending.opts.danger
                    ? " danger"
                    : ""
                }`}
              >
                {confirmLabel}
              </button>
            </div>
          </form>
        )}
      </dialog>
    </DialogContext.Provider>
  );
}
