export type ToastKind = "info" | "success" | "error";
export interface Toast { id: number; message: string; kind: ToastKind }

let next = 1;
export const toasts = $state<Toast[]>([]);

export function toast(message: unknown, kind: ToastKind = "info", ms = 4200) {
  const id = next++;
  const text = typeof message === "string" ? message : message instanceof Error ? message.message : String(message);
  toasts.push({ id, message: text, kind });
  setTimeout(() => dismiss(id), kind === "error" ? ms + 2500 : ms);
}

export function dismiss(id: number) {
  const i = toasts.findIndex((t) => t.id === id);
  if (i >= 0) toasts.splice(i, 1);
}
