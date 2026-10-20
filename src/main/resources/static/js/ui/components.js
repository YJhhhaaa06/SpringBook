/** 通用 UI 片段：分页、空态、骨架、状态徽标、字段级报错 */
import { esc } from './format.js';
import { icon } from './icons.js';

export function pagerHtml({ page, size, total }, unit = '条') {
  const totalPages = Math.max(1, Math.ceil((Number(total) || 0) / size));
  return `
    <span>共 <b class="num">${Number(total) || 0}</b> ${unit} · 每页 ${size} ${unit}</span>
    <div class="pager__controls">
      <button class="btn btn--sm" type="button" data-action="page" data-page="${page - 1}"
        ${page <= 1 ? 'disabled' : ''}>${icon('left', 13)} 上一页</button>
      <span class="pager__page">第 ${page} / ${totalPages} 页</span>
      <button class="btn btn--sm" type="button" data-action="page" data-page="${page + 1}"
        ${page >= totalPages ? 'disabled' : ''}>下一页 ${icon('right', 13)}</button>
    </div>
  `;
}

export function emptyHtml({ title, desc = '', iconName = 'inbox' }) {
  return `<div class="empty">
    <span class="empty__icon">${icon(iconName, 32)}</span>
    <div class="empty__title">${esc(title)}</div>
    ${desc ? `<div class="empty__desc">${esc(desc)}</div>` : ''}
  </div>`;
}

export function skeletonCards(count = 6) {
  return `<div class="book-grid">${Array.from({ length: count }, () => '<div class="skeleton skeleton--card"></div>').join('')}</div>`;
}

export function skeletonRows(count = 5) {
  return Array.from({ length: count }, () => '<div class="skeleton skeleton--row"></div>').join('');
}

/* ---------- 状态徽标 ---------- */

const LOAN_STATUS = {
  BORROWED: ['在借', 'info'],
  RETURNED: ['已归还', 'mute'],
};

const FINE_STATUS = {
  NONE: ['无罚款', 'mute'],
  UNPAID: ['待缴', 'danger'],
  PAID: ['已缴', 'ok'],
};

const RESERVATION_STATUS = {
  WAITING: ['排队中', 'info'],
  READY: ['到书待取', 'warn'],
  FULFILLED: ['已借出', 'ok'],
  CANCELLED: ['已取消', 'mute'],
  EXPIRED: ['已过期', 'mute'],
};

const READER_STATUS = {
  ACTIVE: ['正常', 'ok'],
  FROZEN: ['已冻结', 'danger'],
};

export function pill(text, tone = 'mute', dot = false) {
  return `<span class="pill pill--${tone}">${dot ? '<span class="pill__dot"></span>' : ''}${esc(text)}</span>`;
}

export function loanStatusPill(loan) {
  if (loan.status === 'BORROWED' && loan.overdue) return pill('逾期未还', 'danger', true);
  const [text, tone] = LOAN_STATUS[loan.status] || [loan.status, 'mute'];
  return pill(text, tone, loan.status === 'BORROWED');
}

export function fineStatusPill(fineStatus, fineCents) {
  if (!fineCents || fineStatus === 'NONE') return '<span class="muted">—</span>';
  const [text, tone] = FINE_STATUS[fineStatus] || [fineStatus, 'mute'];
  return pill(text, tone, fineStatus === 'UNPAID');
}

export function reservationStatusPill(status) {
  const [text, tone] = RESERVATION_STATUS[status] || [status, 'mute'];
  return pill(text, tone, status === 'READY');
}

export function readerStatusPill(status) {
  const [text, tone] = READER_STATUS[status] || [status, 'mute'];
  return pill(text, tone, status === 'FROZEN');
}

/* ---------- 字段级报错 ---------- */

export function clearFieldErrors(form) {
  if (!form) return;
  form.querySelectorAll('[aria-invalid="true"]').forEach((el) => el.removeAttribute('aria-invalid'));
  form.querySelectorAll('.field__err').forEach((el) => {
    el.textContent = '';
    el.classList.remove('is-shown');
  });
}

/**
 * 把 40001 的 {字段: 提示} 落到表单上；返回是否命中至少一个字段。
 * 命中则聚焦第一个出错字段，未命中时由调用方退回 toast。
 */
export function applyFieldErrors(form, errors) {
  if (!form || !errors || typeof errors !== 'object') return false;
  clearFieldErrors(form);
  let first = null;
  for (const [field, message] of Object.entries(errors)) {
    const input = form.querySelector(`[data-field="${field}"]`);
    if (!input) continue;
    input.setAttribute('aria-invalid', 'true');
    const holder = input.closest('.field');
    const errEl = holder ? holder.querySelector('.field__err') : null;
    if (errEl) {
      errEl.textContent = String(message);
      errEl.classList.add('is-shown');
    }
    if (!first) first = input;
  }
  if (first) {
    first.focus();
    return true;
  }
  return false;
}

/** 表单字段模板：label + 控件 + 提示 + 报错占位 */
export function field({
  name,
  label,
  type = 'text',
  value = '',
  placeholder = '',
  hint = '',
  required = false,
  attrs = '',
  options = null,
}) {
  let control;
  const id = `f-${name}`;
  if (type === 'select') {
    control = `<select class="select" id="${id}" name="${name}" data-field="${name}" ${attrs}>
      ${options.map(([v, t]) => `<option value="${esc(v)}">${esc(t)}</option>`).join('')}
    </select>`;
  } else {
    control = `<input class="input" id="${id}" type="${type}" name="${name}" data-field="${name}"
      value="${esc(value)}" placeholder="${esc(placeholder)}" ${attrs} />`;
  }
  return `<div class="field">
    <label class="field__label" for="${id}">${esc(label)}${required ? '<span class="req">*</span>' : ''}</label>
    ${control}
    ${hint ? `<span class="field__hint">${esc(hint)}</span>` : ''}
    <span class="field__err"></span>
  </div>`;
}
