/** 弹层：居中对话框、右侧抽屉、确认框、表单弹层 */
import { esc } from './format.js';
import { icon } from './icons.js';
import { applyFieldErrors, clearFieldErrors } from './components.js';
import { toastError } from './toast.js';
import { formValues } from './dom.js';

let openCount = 0;

function lockScroll() {
  openCount += 1;
  document.body.style.overflow = 'hidden';
}

function unlockScroll() {
  openCount = Math.max(0, openCount - 1);
  if (openCount === 0) document.body.style.overflow = '';
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * 打开弹层。
 * @param {object} options
 * @param {'center'|'right'} options.kind 形态：居中对话框 / 右侧抽屉
 * @param {string} options.className 内容容器类名（默认按形态取 modal / drawer）
 * @param {string|Element} options.body 正文
 * @param {Function} options.onClose 关闭回调（含 ESC、点遮罩、点关闭按钮）
 */
export function openDialog(options) {
  const {
    kind = 'center',
    className = '',
    title = '',
    subtitle = '',
    body = '',
    onClose = null,
  } = options;

  const host = document.getElementById('modal-host');
  const restoreFocus = document.activeElement;
  const overlay = document.createElement('div');
  overlay.className = `overlay overlay--${kind}`;
  host.appendChild(overlay);
  lockScroll();

  const panel = document.createElement('div');
  panel.className = className || (kind === 'right' ? 'drawer' : 'modal');
  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-modal', 'true');
  panel.innerHTML = `
    <div class="${kind === 'right' ? 'drawer__head' : 'modal__head'}">
      <div>
        <h2 class="modal__title">${esc(title)}</h2>
        ${subtitle ? `<p class="modal__sub">${esc(subtitle)}</p>` : ''}
      </div>
      <button class="btn btn--ghost btn--icon" type="button" data-close aria-label="关闭">
        ${icon('close', 16)}
      </button>
    </div>
    <div class="${kind === 'right' ? 'drawer__body' : 'modal__body'}"></div>
    ${kind === 'right' ? '' : '<div class="modal__foot" data-foot></div>'}
  `;

  const bodyHost = panel.querySelector(kind === 'right' ? '.drawer__body' : '.modal__body');
  if (body instanceof Element) bodyHost.appendChild(body);
  else bodyHost.innerHTML = body;

  overlay.appendChild(panel);

  let closed = false;
  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.stopPropagation();
      close();
      return;
    }
    if (event.key !== 'Tab') return;
    // 焦点兜在弹层内
    const items = Array.from(panel.querySelectorAll(FOCUSABLE)).filter((el) => el.offsetParent !== null);
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  function close() {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKeyDown, true);
    unlockScroll();
    overlay.style.pointerEvents = 'none';
    overlay.style.opacity = '0';
    setTimeout(() => overlay.remove(), 120);
    if (restoreFocus && typeof restoreFocus.focus === 'function') restoreFocus.focus();
    if (typeof onClose === 'function') onClose();
  }

  document.addEventListener('keydown', onKeyDown, true);
  overlay.addEventListener('mousedown', (event) => {
    if (event.target === overlay) close();
  });
  panel.querySelector('[data-close]').addEventListener('click', close);

  const focusTarget = panel.querySelector('input, select, textarea, thead, .btn--primary');
  setTimeout(() => (focusTarget || panel.querySelector('[data-close]'))?.focus(), 30);

  return { overlay, panel, body: bodyHost, foot: panel.querySelector('[data-foot]'), close };
}

export function openDrawer(options) {
  return openDialog({ ...options, kind: 'right' });
}

/** 确认框：返回 Promise<boolean>（ESC / 点遮罩 / 取消都算 false） */
export function confirmDialog({
  title,
  message,
  detail = '',
  confirmText = '确认',
  cancelText = '取消',
  danger = false,
}) {
  return new Promise((resolve) => {
    let settled = false;
    const dialog = openDialog({
      title,
      onClose: () => {
        if (!settled) {
          settled = true;
          resolve(false);
        }
      },
      body: `
        <p style="margin:0 0 12px;color:var(--ink-2);line-height:1.7">${esc(message)}</p>
        ${detail ? `<div class="callout ${danger ? 'callout--danger' : 'callout--info'}">${esc(detail)}</div>` : ''}
      `,
    });

    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'btn';
    cancel.textContent = cancelText;

    const confirm = document.createElement('button');
    confirm.type = 'button';
    confirm.className = `btn ${danger ? 'btn--danger-solid' : 'btn--primary'}`;
    confirm.textContent = confirmText;

    cancel.addEventListener('click', () => dialog.close());
    confirm.addEventListener('click', () => {
      settled = true;
      dialog.close();
      resolve(true);
    });

    dialog.foot.append(cancel, confirm);
    setTimeout(() => confirm.focus(), 50);
  });
}

/**
 * 表单弹层：统一处理提交忙碌态、40001 字段级内联报错、其他业务错误 toast。
 * onSubmit 抛错即保持打开；返回 false 也可阻止关闭。
 */
export function openFormModal({
  title,
  subtitle = '',
  body,
  submitText = '保存',
  cancelText = '取消',
  danger = false,
  onSubmit,
}) {
  const dialog = openDialog({ title, subtitle, body: `<form novalidate>${body}</form>` });
  const form = dialog.panel.querySelector('form');

  const cancel = document.createElement('button');
  cancel.type = 'button';
  cancel.className = 'btn';
  cancel.textContent = cancelText;

  const submit = document.createElement('button');
  submit.type = 'button';
  submit.className = `btn ${danger ? 'btn--danger-solid' : 'btn--primary'}`;
  submit.textContent = submitText;

  dialog.foot.append(cancel, submit);
  cancel.addEventListener('click', dialog.close);

  const doSubmit = async () => {
    if (submit.disabled) return;
    clearFieldErrors(form);
    submit.disabled = true;
    submit.textContent = '提交中…';
    try {
      const result = await onSubmit({ form, values: formValues(form), dialog });
      if (result !== false) dialog.close();
    } catch (error) {
      if (!applyFieldErrors(form, error?.fieldErrors)) toastError(error);
    } finally {
      if (submit.isConnected) {
        submit.disabled = false;
        submit.textContent = submitText;
      }
    }
  };

  submit.addEventListener('click', doSubmit);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    doSubmit();
  });

  setTimeout(() => form.querySelector('input, select, textarea')?.focus(), 50);
  return dialog;
}
