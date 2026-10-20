/**
 * 读者管理（ADMIN）：分页检索、新增、资料修改、冻结/解冻、详情（欠款与在借）。
 * 手机号前端做格式校验（后端从简，仅校验长度与唯一性）。
 */
import { api } from '../core/api.js';
import { navigate } from '../core/router.js';
import { esc, money, datetime, dateOnly, relativeDays } from '../ui/format.js';
import { icon } from '../ui/icons.js';
import { delegate, setBusy } from '../ui/dom.js';
import { toastOk, toastError } from '../ui/toast.js';
import {
  emptyHtml,
  skeletonRows,
  pagerHtml,
  field,
  applyFieldErrors,
  readerStatusPill,
  pill,
} from '../ui/components.js';
import { openDrawer, openFormModal, confirmDialog } from '../ui/modal.js';

const PAGE_SIZE = 10;
const PHONE_RE = /^1[3-9]\d{9}$/;

export function render(root, ctx) {
  const state = { keyword: '', page: 1, size: PAGE_SIZE, total: 0, records: [], seq: 0 };
  let disposed = false;

  root.innerHTML = `
    <div class="toolbar">
      <form class="search" data-role="search">
        <span class="search__icon">${icon('search', 16)}</span>
        <input class="input" name="keyword" type="search" autocomplete="off"
          placeholder="按姓名或手机号检索" aria-label="检索读者" />
      </form>
      <div class="toolbar__spacer"></div>
      <button class="btn btn--primary" type="button" data-action="create">
        ${icon('plus', 14)} 新增读者
      </button>
    </div>
    <div class="panel"><div class="table-wrap" data-role="table"></div></div>
    <div class="pager" data-role="pager"></div>
  `;

  const tableHost = root.querySelector('[data-role="table"]');
  const pagerHost = root.querySelector('[data-role="pager"]');
  const searchForm = root.querySelector('[data-role="search"]');

  async function load() {
    const seq = ++state.seq;
    tableHost.innerHTML = `<div style="padding:14px">${skeletonRows(6)}</div>`;
    pagerHost.innerHTML = '';
    try {
      const page = await api.listReaders({ keyword: state.keyword, page: state.page, size: state.size });
      if (disposed || seq !== state.seq) return;
      state.total = page?.total || 0;
      state.records = page?.records || [];
      paint();
    } catch (error) {
      if (disposed || seq !== state.seq) return;
      tableHost.innerHTML = `<div style="padding:16px"><div class="callout callout--danger">
        ${esc(error.friendly || '读者列表加载失败')}</div></div>`;
    }
  }

  function paint() {
    if (!state.records.length) {
      tableHost.innerHTML = `<div style="padding:16px">${emptyHtml({
        title: state.keyword ? `没有匹配「${state.keyword}」的读者` : '还没有读者档案',
        desc: state.keyword ? '换姓名或手机号关键词试试。' : '新增读者后会出现在这里；读者也可自助注册。',
        iconName: 'users',
      })}</div>`;
      pagerHost.innerHTML = '';
      return;
    }

    tableHost.innerHTML = `<table class="table">
      <thead>
        <tr>
          <th>读者</th>
          <th>手机号</th>
          <th>状态</th>
          <th>注册时间</th>
          <th class="t-right">操作</th>
        </tr>
      </thead>
      <tbody>
        ${state.records
          .map(
            (reader) => `<tr>
              <td class="t-title">
                <span>${esc(reader.name)}</span>
                <span class="table__sub">#${reader.id}</span>
              </td>
              <td class="num">${esc(reader.phone)}</td>
              <td>${readerStatusPill(reader.status)}</td>
              <td class="num">${dateOnly(reader.createdAt)}</td>
              <td class="t-actions">
                <span class="row-actions">
                  <button class="btn btn--sm btn--ghost" type="button" data-action="detail" data-id="${reader.id}">详情</button>
                  <button class="btn btn--sm btn--ghost" type="button" data-action="edit" data-id="${reader.id}">编辑</button>
                  <button class="btn btn--sm ${reader.status === 'FROZEN' ? '' : 'btn--danger'}" type="button"
                    data-action="toggle" data-id="${reader.id}" data-status="${reader.status}">
                    ${reader.status === 'FROZEN' ? '解冻' : '冻结'}
                  </button>
                </span>
              </td>
            </tr>`,
          )
          .join('')}
      </tbody>
    </table>`;

    pagerHost.innerHTML = pagerHtml({ page: state.page, size: state.size, total: state.total }, '位');
  }

  /* ---------- 表单 ---------- */

  function readerFormBody(reader) {
    return `
      ${field({
        name: 'name',
        label: '姓名',
        required: true,
        value: reader?.name || '',
        attrs: 'maxlength="50"',
        placeholder: '张三',
      })}
      ${field({
        name: 'phone',
        label: '手机号',
        required: true,
        value: reader?.phone || '',
        attrs: 'maxlength="20" inputmode="numeric"',
        placeholder: '13800001111',
        hint: '手机号在读者间唯一，用于识别档案',
      })}
    `;
  }

  function openCreateForm() {
    openFormModal({
      title: '新增读者',
      subtitle: '仅创建读者档案；账号需读者自助注册后绑定',
      submitText: '创建',
      body: readerFormBody(null),
      onSubmit: async ({ form, values }) => {
        const errors = {};
        if (!values.name) errors.name = '姓名不能为空';
        if (!values.phone) errors.phone = '手机号不能为空';
        else if (!PHONE_RE.test(values.phone)) errors.phone = '请输入 11 位中国大陆手机号';
        if (Object.keys(errors).length) return void applyFieldErrors(form, errors);

        const created = await api.createReader({ name: values.name, phone: values.phone });
        toastOk(`读者「${created.name}」已创建`, `读者 #${created.id}`);
        load();
      },
    });
  }

  function openEditForm(reader) {
    openFormModal({
      title: '修改读者资料',
      subtitle: `#${reader.id} · ${reader.name}`,
      submitText: '保存',
      body: readerFormBody(reader),
      onSubmit: async ({ form, values }) => {
        const errors = {};
        if (!values.name) errors.name = '姓名不能为空';
        if (!values.phone) errors.phone = '手机号不能为空';
        else if (!PHONE_RE.test(values.phone)) errors.phone = '请输入 11 位中国大陆手机号';
        if (Object.keys(errors).length) return void applyFieldErrors(form, errors);

        await api.updateReader(reader.id, { name: values.name, phone: values.phone });
        toastOk('读者资料已更新');
        load();
      },
    });
  }

  async function toggleStatus(reader, button) {
    const freeze = reader.status !== 'FROZEN';
    const ok = await confirmDialog({
      title: freeze ? '冻结读者' : '解冻读者',
      message: freeze
        ? `冻结「${reader.name}」？冻结后该读者无法借书与预约。`
        : `解冻「${reader.name}」？`,
      detail: freeze
        ? '已借出的图书不受影响，仍可正常归还。'
        : '注意：解冻绕不过罚款禁借——存在待缴罚款或逾期在借的读者即使状态为正常，仍无法借阅与预约。',
      confirmText: freeze ? '冻结' : '解冻',
      danger: freeze,
    });
    if (!ok) return;

    setBusy(button, true, '处理中…');
    try {
      await api.updateReaderStatus(reader.id, freeze ? 'FROZEN' : 'ACTIVE');
      toastOk(freeze ? '读者已冻结' : '读者已解冻');
      load();
    } catch (error) {
      setBusy(button, false);
      toastError(error);
    }
  }

  /* ---------- 详情抽屉 ---------- */

  async function openDetail(reader) {
    const dialog = openDrawer({
      title: '读者详情',
      subtitle: `#${reader.id}`,
      body: '<div class="muted" style="font-size:12.5px">正在读取…</div>',
    });

    try {
      const detail = await api.getReader(reader.id);
      const loans = detail.onLoans || [];
      const hasDebt = Number(detail.unpaidFineCents) > 0;

      dialog.body.innerHTML = `
        <section>
          <div class="drawer__section-title">档案</div>
          <dl class="desc">
            <dt>姓名</dt><dd><b>${esc(detail.name)}</b></dd>
            <dt>手机号</dt><dd class="mono">${esc(detail.phone)}</dd>
            <dt>状态</dt><dd>${readerStatusPill(detail.status)}</dd>
            <dt>注册时间</dt><dd>${datetime(detail.createdAt)}</dd>
            <dt>待缴罚款</dt>
            <dd>${
              hasDebt
                ? `<span class="mono" style="color:var(--danger)">${money(detail.unpaidFineCents)}</span>`
                : '<span class="muted">无</span>'
            }</dd>
          </dl>
        </section>

        ${
          hasDebt
            ? `<div class="callout callout--warn">
                 ${icon('alert', 15)}
                 <div class="callout__body">存在待缴罚款，该读者当前无法借书与预约。</div>
               </div>`
            : ''
        }

        <section>
          <div class="drawer__section-title">在借（${loans.length}）</div>
          ${
            loans.length
              ? `<div class="panel"><div class="table-wrap"><table class="table">
                   <thead><tr><th>图书</th><th>应还</th><th>状态</th></tr></thead>
                   <tbody>${loans
                     .map(
                       (loan) => `<tr>
                         <td>${esc(loan.bookTitle)}</td>
                         <td class="num">${dateOnly(loan.dueAt)}</td>
                         <td>${
                           loan.overdue
                             ? pill('已逾期', 'danger', true)
                             : `<span class="muted" style="font-size:12px">${relativeDays(loan.dueAt)}</span>`
                         }</td>
                       </tr>`,
                     )
                     .join('')}</tbody>
                 </table></div></div>`
              : '<div class="callout">当前没有在借图书。</div>'
          }
        </section>

        <div class="inline" style="gap:8px;flex-wrap:wrap">
          <button class="btn btn--sm" type="button" data-action="goto-loans">查看其借阅记录</button>
          <button class="btn btn--sm" type="button" data-action="edit-from-drawer">修改资料</button>
        </div>
      `;

      dialog.panel.addEventListener('click', (event) => {
        const target = event.target.closest('[data-action]');
        if (!target) return;
        if (target.dataset.action === 'goto-loans') {
          dialog.close();
          navigate('/loans', { readerId: detail.id });
        }
        if (target.dataset.action === 'edit-from-drawer') {
          openEditForm(detail);
        }
      });
    } catch (error) {
      dialog.body.innerHTML = `<div class="callout callout--danger">${esc(
        error.friendly || '读者详情读取失败',
      )}</div>`;
    }
  }

  /* ---------- 事件 ---------- */

  searchForm.addEventListener('submit', (event) => {
    event.preventDefault();
    state.keyword = searchForm.elements.namedItem('keyword').value.trim();
    state.page = 1;
    load();
  });

  delegate(root, 'click', '[data-action]', async (event, target) => {
    const action = target.dataset.action;
    const id = Number(target.dataset.id);

    if (action === 'create') return openCreateForm();
    if (action === 'page') {
      const next = Number(target.dataset.page);
      if (!Number.isFinite(next) || next < 1) return;
      state.page = next;
      load();
      return;
    }

    const reader = state.records.find((item) => item.id === id);
    if (!reader) return;

    switch (action) {
      case 'detail':
        return openDetail(reader);
      case 'edit':
        return openEditForm(reader);
      case 'toggle':
        return toggleStatus(reader, target);
      default:
        return undefined;
    }
  });

  load();

  return () => {
    disposed = true;
  };
}
