/**
 * 我的账户：账号信息与令牌有效期、读者档案（READER）、修改密码、退出登录。
 * 无状态 JWT：改密后旧令牌在过期前仍然有效（后端已知取舍）。
 */
import { api } from '../core/api.js';
import { clearSession, getSession } from '../core/session.js';
import { navigate } from '../core/router.js';
import { esc, money, dateOnly, relativeDays } from '../ui/format.js';
import { countdownHtml } from '../ui/clock.js';
import { icon } from '../ui/icons.js';
import { setBusy } from '../ui/dom.js';
import { toastOk, toastError } from '../ui/toast.js';
import {
  applyFieldErrors,
  clearFieldErrors,
  field,
  pill,
  readerStatusPill,
  emptyHtml,
} from '../ui/components.js';
import { confirmDialog } from '../ui/modal.js';

export function render(root, ctx) {
  const session = getSession() || ctx.session;
  const admin = session.role === 'ADMIN';
  let disposed = false;

  const tokenDeadline = new Date(session.expiresAt).toISOString();

  root.innerHTML = `
    <div class="split">
      <div class="stack">
        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">账号</h2>
            <span class="role-badge">${admin ? 'ADMIN' : 'READER'}</span>
          </div>
          <div class="panel__body">
            <dl class="desc">
              <dt>用户名</dt><dd><b>${esc(session.username)}</b></dd>
              <dt>角色</dt><dd>${admin ? '管理员' : '读者'}</dd>
              ${admin ? '<dt>读者档案</dt><dd class="muted">管理员账号不绑定读者档案</dd>' : ''}
              <dt>令牌到期</dt>
              <dd>
                ${countdownHtml(tokenDeadline)}
                <div class="muted" style="font-size:11.5px">到期后需重新登录（有效期 2 小时，无状态 JWT 不可续期）</div>
              </dd>
            </dl>
          </div>
        </section>

        <section class="panel">
          <div class="panel__head">
            <h2 class="panel__title">修改密码</h2>
            <span class="muted" style="font-size:11.5px">修改后旧令牌在过期前仍有效</span>
          </div>
          <div class="panel__body">
            <form novalidate data-role="pwd-form">
              ${field({ name: 'oldPassword', label: '原密码', type: 'password', required: true, attrs: 'autocomplete="current-password"' })}
              <div class="field-row">
                ${field({ name: 'newPassword', label: '新密码', type: 'password', required: true, hint: '6-50 个字符', attrs: 'autocomplete="new-password"' })}
                ${field({ name: 'confirmPassword', label: '确认新密码', type: 'password', required: true, attrs: 'autocomplete="new-password"' })}
              </div>
              <button class="btn btn--primary" type="submit" data-role="pwd-submit">保存新密码</button>
            </form>
          </div>
        </section>
      </div>

      <div class="stack" data-role="reader-panel">
        ${
          admin
            ? `<section class="panel">
                 <div class="panel__head"><h2 class="panel__title">管理员工作台</h2></div>
                 <div class="panel__body stack--sm">
                   <p class="muted" style="margin:0;font-size:12.5px;line-height:1.8">
                     管理员可维护图书与读者、代办借还、缴纳罚款。读者账号通过自助注册创建，
                     注册后自动绑定读者档案。
                   </p>
                   <button class="btn btn--sm" type="button" data-action="logout">
                     ${icon('logout', 14)} 退出登录
                   </button>
                 </div>
               </section>`
            : `<section class="panel">
                 <div class="panel__head"><h2 class="panel__title">读者档案</h2></div>
                 <div class="panel__body" data-role="reader-detail">
                   <div class="muted" style="font-size:12.5px">读取中…</div>
                 </div>
               </section>`
        }
      </div>
    </div>
  `;

  /* ---------- 改密 ---------- */

  const form = root.querySelector('[data-role="pwd-form"]');
  const submit = root.querySelector('[data-role="pwd-submit"]');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearFieldErrors(form);
    const value = (name) => String(form.elements.namedItem(name).value || '');
    const oldPassword = value('oldPassword');
    const newPassword = value('newPassword');
    const confirmPassword = value('confirmPassword');

    const errors = {};
    if (!oldPassword) errors.oldPassword = '请输入原密码';
    if (!newPassword) errors.newPassword = '请输入新密码';
    else if (newPassword.length < 6 || newPassword.length > 50) errors.newPassword = '密码长度 6-50 个字符';
    else if (newPassword === oldPassword) errors.newPassword = '新密码不能与原密码相同';
    if (!confirmPassword) errors.confirmPassword = '请再次输入新密码';
    else if (confirmPassword !== newPassword) errors.confirmPassword = '两次输入的新密码不一致';
    if (Object.keys(errors).length) {
      applyFieldErrors(form, errors);
      return;
    }

    setBusy(submit, true, '保存中…');
    try {
      await api.changePassword(oldPassword, newPassword);
      form.reset();
      toastOk('密码已修改', '下次登录请使用新口令；当前令牌到期前仍然有效');
    } catch (error) {
      if (error?.code === 40002 && applyFieldErrors(form, { oldPassword: '原密码错误' })) {
        // 已内联
      } else {
        toastError(error);
      }
    } finally {
      setBusy(submit, false);
    }
  });

  /* ---------- 读者档案 ---------- */

  async function loadReader() {
    const host = root.querySelector('[data-role="reader-detail"]');
    if (!host) return;
    if (!session.readerId) {
      host.innerHTML = `<div class="callout callout--warn">当前账号未绑定读者档案，无法借阅。</div>`;
      return;
    }
    try {
      const detail = await api.getReader(session.readerId);
      if (disposed) return;
      const loans = detail.onLoans || [];
      const hasDebt = Number(detail.unpaidFineCents) > 0;

      host.innerHTML = `
        <dl class="desc">
          <dt>姓名</dt><dd><b>${esc(detail.name)}</b></dd>
          <dt>手机号</dt><dd class="mono">${esc(detail.phone)}</dd>
          <dt>状态</dt><dd>${readerStatusPill(detail.status)}</dd>
          <dt>注册时间</dt><dd>${dateOnly(detail.createdAt)}</dd>
          <dt>待缴罚款</dt>
          <dd>${
            hasDebt
              ? `<span class="mono" style="color:var(--danger)">${money(detail.unpaidFineCents)}</span>`
              : '<span class="muted">无</span>'
          }</dd>
        </dl>

        ${hasDebt ? `<div class="callout callout--warn" style="margin-top:14px">
            ${icon('alert', 15)}
            <div class="callout__body">存在待缴罚款，缴清前无法借书与预约，请到馆缴纳。</div>
          </div>` : ''}

        <div class="section-head" style="margin:18px 0 10px">
          <h2 style="font-size:13.5px">在借图书（${loans.length}）</h2>
        </div>
        ${
          loans.length
            ? `<div class="stack--sm">
                 ${loans
                   .map(
                     (loan) => `<div class="between" style="padding:9px 0;border-bottom:1px dashed var(--line)">
                       <div style="min-width:0">
                         <div style="font-weight:600;font-size:13px">${esc(loan.bookTitle)}</div>
                         <div class="muted" style="font-size:11.5px">应还 ${dateOnly(loan.dueAt)}</div>
                       </div>
                       ${
                         loan.overdue
                           ? pill('已逾期', 'danger', true)
                           : `<span class="muted" style="font-size:11.5px">${relativeDays(loan.dueAt)}</span>`
                       }
                     </div>`,
                   )
                   .join('')}
               </div>`
            : emptyHtml({ title: '当前没有在借图书', desc: '去「图书检索」借一本。', iconName: 'book' })
        }

        <div class="inline" style="margin-top:18px;gap:8px">
          <button class="btn btn--sm" type="button" data-action="logout">
            ${icon('logout', 14)} 退出登录
          </button>
        </div>
      `;
    } catch (error) {
      if (disposed) return;
      host.innerHTML = `<div class="callout callout--danger">${esc(
        error.friendly || '读者档案读取失败',
      )}</div>`;
    }
  }

  /* ---------- 退出 ---------- */

  root.addEventListener('click', async (event) => {
    const target = event.target.closest('[data-action="logout"]');
    if (!target) return;
    const ok = await confirmDialog({
      title: '退出登录',
      message: '确认退出当前账号？',
      detail: '退出只清理本地令牌，服务端无状态、无需额外登出接口。',
      confirmText: '退出',
    });
    if (!ok) return;
    clearSession();
    toastOk('已退出登录');
    navigate('/login');
  });

  if (!admin) loadReader();

  return () => {
    disposed = true;
  };
}
