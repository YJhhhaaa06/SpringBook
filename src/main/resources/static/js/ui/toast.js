/** 顶部右侧 toast 堆栈：成功/错误/提醒/信息，错误自动附上错误码提示（hint） */
import { esc } from './format.js';
import { icon } from './icons.js';

const MAX_VISIBLE = 4;

export function toast(message, { type = 'info', hint = '', duration = 3800 } = {}) {
  const host = document.getElementById('toast-host');
  if (!host) return;

  while (host.children.length >= MAX_VISIBLE) host.firstElementChild?.remove();

  const el = document.createElement('div');
  el.className = `toast toast--${type}`;
  el.setAttribute('role', type === 'error' ? 'alert' : 'status');
  el.innerHTML = `
    <div class="toast__body">
      <div class="toast__title">${esc(message)}</div>
      ${hint ? `<div class="toast__hint">${esc(hint)}</div>` : ''}
    </div>
    <button class="toast__close" type="button" aria-label="关闭提示">×</button>
  `;
  host.appendChild(el);

  let timer = null;
  const dismiss = () => {
    if (!el.isConnected) return;
    clearTimeout(timer);
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 180);
  };
  el.querySelector('.toast__close').addEventListener('click', dismiss);
  if (duration > 0) timer = setTimeout(dismiss, duration);
  return dismiss;
}

export const toastOk = (message, hint = '') => toast(message, { type: 'success', hint });
export const toastWarn = (message, hint = '') => toast(message, { type: 'warn', hint, duration: 4600 });

/** 统一错误提示：业务错误码 → 中文提示 + 下一步建议 */
export function toastError(error, fallback = '操作失败') {
  if (!error) return toast(fallback, { type: 'error' });
  const message = error.friendly || error.message || fallback;
  const hint = error.hint || '';
  return toast(message, { type: 'error', hint, duration: 5000 });
}

/** 内联图标提示（用于空态/说明块） */
export function iconCallout(type, title, body) {
  return `<div class="callout callout--${type}">
    <span aria-hidden="true">${icon(type === 'danger' ? 'alert' : 'clock', 16)}</span>
    <div class="callout__body">
      <div class="callout__title">${esc(title)}</div>
      <div>${esc(body)}</div>
    </div>
  </div>`;
}
