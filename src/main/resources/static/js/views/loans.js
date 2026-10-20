/**
 * 借阅记录：READER 看自己的全部借阅与还书入口；ADMIN 看全量、可代办还书与缴纳罚款。
 * 逾期由后端按 dueAt 实时派生；罚款在归还那一刻结算，fineStatus = NONE / UNPAID / PAID。
 */
import { api } from '../core/api.js';
import { esc, money, datetime, relativeDays } from '../ui/format.js';
import { icon } from '../ui/icons.js';
import { delegate, setBusy } from '../ui/dom.js';
import { toastOk, toastError } from '../ui/toast.js';
import { emptyHtml, skeletonRows, loanStatusPill, fineStatusPill } from '../ui/components.js';
import { openDialog, confirmDialog } from '../ui/modal.js';
import { createReaderPicker } from '../ui/reader-picker.js';

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'borrowed', label: '在借' },
  { key: 'returned', label: '已还' },
  { key: 'overdue', label: '逾期未还' },
];

export function render(root, ctx) {
  const { session, query } = ctx;
  const admin = session.role === 'ADMIN';
  const state = {
    filter: 'all',
    readerId: admin && query.readerId ? Number(query.readerId) : null,
    readerName: '',
    records: [],
    seq: 0,
  };
  let disposed = false;

  root.innerHTML = `
    <div class="toolbar">
      <div class="chips" data-role="filters">
        ${FILTERS.map(
          (f) => `<button class="chip ${f.key === 'all' ? 'is-active' : ''}" type="button"
            data-action="filter" data-key="${f.key}">${f.label}</button>`,
        ).join('')}
      </div>
      <div class="toolbar__spacer"></div>
      ${
        admin
          ? `<button class="btn btn--sm" type="button" data-action="pick-reader">
               ${icon('users', 14)} <span data-role="reader-label">按读者筛选</span>
             </button>`
          : ''
      }
      <button class="btn btn--sm btn--ghost btn--icon" type="button" data-action="reload" title="刷新">
        ${icon('refresh', 15)}
      </button>
    </div>
    <div class="panel"><div class="table-wrap" data-role="table"></div></div>
  `;

  const tableHost = root.querySelector('[data-role="table"]');
  const readerLabel = root.querySelector('[data-role="reader-label"]');

  function currentQuery() {
    const queryObject = {};
    if (state.filter === 'overdue') queryObject.overdue = true;
    else if (state.filter === 'borrowed') queryObject.status = 'BORROWED';
    else if (state.filter === 'returned') queryObject.status = 'RETURNED';
    if (admin && state.readerId) queryObject.readerId = state.readerId;
    return queryObject;
  }

  async function load() {
    const seq = ++state.seq;
    tableHost.innerHTML = `<div style="padding:14px">${skeletonRows(5)}</div>`;
    try {
      const records = await api.listLoans(currentQuery());
      if (disposed || seq !== state.seq) return;
      state.records = records || [];
      paint();
    } catch (error) {
      if (disposed || seq !== state.seq) return;
      tableHost.innerHTML = `<div style="padding:16px"><div class="callout callout--danger">
        ${esc(error.friendly || '借阅记录加载失败')}</div></div>`;
    }
  }

  function paint() {
    if (!state.records.length) {
      tableHost.innerHTML = `<div style="padding:16px">${emptyHtml({
        title: '没有符合条件的借阅记录',
        desc:
          state.filter === 'overdue'
            ? '当前没有逾期未还的记录。'
            : admin
              ? '试试切换筛选条件，或按读者筛选。'
              : '去「图书检索」借一本，或切换筛选条件。',
        iconName: 'loan',
      })}</div>`;
      return;
    }

    const rows = state.records.map((loan) => {
      const isOpen = loan.status === 'BORROWED';
      const actions = [];
      if (isOpen) {
        actions.push(
          `<button class="btn btn--sm" type="button" data-action="return" data-id="${loan.id}">还书</button>`,
        );
      }
      if (admin && loan.fineStatus === 'UNPAID') {
        actions.push(
          `<button class="btn btn--sm btn--primary" type="button" data-action="pay" data-id="${loan.id}">缴罚款</button>`,
        );
      }

      const fineCell = loan.fineCents
        ? `<span class="mono">${money(loan.fineCents)}</span>
           <div style="margin-top:3px">${fineStatusPill(loan.fineStatus, loan.fineCents)}</div>`
        : '<span class="muted">—</span>';

      return `<tr>
        <td class="t-title">
          <span>${esc(loan.bookTitle || `#${loan.bookId}`)}</span>
          ${
            admin
              ? `<span class="table__sub">${esc(loan.readerName || '—')} · 读者 #${loan.readerId}</span>`
              : `<span class="table__sub">借阅 #${loan.id}</span>`
          }
        </td>
        <td class="num">${datetime(loan.borrowedAt)}</td>
        <td>
          <span class="num">${datetime(loan.dueAt)}</span>
          <div class="table__sub">${
            isOpen
              ? relativeDays(loan.dueAt)
              : loan.returnedAt
                ? `已还于 ${datetime(loan.returnedAt)}`
                : '已归还'
          }</div>
        </td>
        <td>${loanStatusPill(loan)}</td>
        <td>${fineCell}</td>
        <td class="t-actions"><span class="row-actions">${actions.join('') || '<span class="muted">—</span>'}</span></td>
      </tr>`;
    });

    tableHost.innerHTML = `<table class="table">
      <thead>
        <tr>
          <th>图书${admin ? ' / 读者' : ''}</th>
          <th>借出时间</th>
          <th>应还时间</th>
          <th>状态</th>
          <th>罚款</th>
          <th class="t-right">操作</th>
        </tr>
      </thead>
      <tbody>${rows.join('')}</tbody>
    </table>
    <div class="pager" style="padding:10px 14px">
      <span>共 <b class="num">${state.records.length}</b> 条${
        state.filter === 'overdue' ? '逾期记录' : ''
      }</span>
      <span class="muted">逾期按不足一天按一天计算，罚款 0.50 元/天（封顶为书价）</span>
    </div>`;
  }

  /* ---------- 还书 ---------- */

  async function returnLoan(loan, button) {
    const ok = await confirmDialog({
      title: '确认归还',
      message: `归还《${loan.bookTitle}》？`,
      detail:
        loan.overdue || new Date(loan.dueAt) < new Date()
          ? '该借阅已逾期，归还时按逾期天数结算罚款，读者会被自动冻结、缴清罚款后自动解冻。'
          : '未逾期，归还后库存立即回补；若有预约队列，队首将转为「到书待取」。',
      confirmText: '确认归还',
    });
    if (!ok) return;

    setBusy(button, true, '归还中…');
    try {
      const result = await api.returnLoan(loan.id);
      showReturnResult(loan, result);
      load();
    } catch (error) {
      setBusy(button, false);
      toastError(error);
    }
  }

  function showReturnResult(loan, result) {
    const fine = Number(result.fineCents) || 0;
    const rows = [
      ['图书', loan.bookTitle],
      ['借出 / 应还', `${datetime(result.borrowedAt)} → ${datetime(result.dueAt)}`],
      ['归还时间', datetime(result.returnedAt)],
      ['逾期天数', result.overdueDays > 0 ? `${result.overdueDays} 天` : '未逾期'],
    ];

    const dialog = openDialog({
      title: fine > 0 ? '已归还 · 产生罚款' : '已归还',
      subtitle: loan.bookTitle,
      body: `
        <dl class="desc" style="margin-bottom:14px">
          ${rows.map(([k, v]) => `<dt>${k}</dt><dd>${esc(v)}</dd>`).join('')}
        </dl>
        ${
          fine > 0
            ? `<div class="callout callout--warn">
                 <span>${icon('money', 16)}</span>
                 <div class="callout__body">
                   <div class="callout__title">本次罚款 ${money(fine)}</div>
                   罚款状态为「待缴」，缴清前该读者无法借书与预约；逾期归还已自动冻结读者。
                 </div>
               </div>`
            : `<div class="callout callout--info">
                 <span>${icon('check', 16)}</span>
                 <div class="callout__body">未产生罚款。若该书有预约队列，队首已转为「到书待取」并保留 48 小时。</div>
               </div>`
        }
      `,
    });

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn btn--primary';
    close.textContent = '知道了';
    close.addEventListener('click', dialog.close);

    if (admin && fine > 0) {
      const pay = document.createElement('button');
      pay.type = 'button';
      pay.className = 'btn';
      pay.textContent = '立即缴纳';
      pay.addEventListener('click', async () => {
        setBusy(pay, true, '缴纳中…');
        try {
          await api.payFine(loan.id);
          dialog.close();
          toastOk('罚款已缴纳', '若读者无其它欠款与逾期，将自动解冻');
          load();
        } catch (error) {
          setBusy(pay, false);
          toastError(error);
        }
      });
      dialog.foot.append(pay);
    }

    dialog.foot.append(close);
    setTimeout(() => close.focus(), 60);
  }

  /* ---------- 缴罚款 ---------- */

  async function payFine(loan, button) {
    const ok = await confirmDialog({
      title: '确认收款',
      message: `为《${loan.bookTitle}》缴纳罚款 ${money(loan.fineCents)}？`,
      detail: '缴纳后罚款状态置为「已缴」；若该读者已冻结且无其它欠款与逾期，将自动解冻。',
      confirmText: '确认已收款',
    });
    if (!ok) return;

    setBusy(button, true, '处理中…');
    try {
      await api.payFine(loan.id);
      toastOk('罚款已缴纳', '读者若满足条件已自动解冻');
      load();
    } catch (error) {
      setBusy(button, false);
      toastError(error);
    }
  }

  /* ---------- 读者筛选（ADMIN） ---------- */

  function openReaderFilter() {
    const dialog = openDialog({ title: '按读者筛选', subtitle: '选择一位读者，查看其全部借阅' });
    const picker = createReaderPicker({
      selected: state.readerId ? { id: state.readerId, name: state.readerName || `#${state.readerId}` } : null,
    });
    dialog.body.appendChild(picker.el);

    const clear = document.createElement('button');
    clear.type = 'button';
    clear.className = 'btn';
    clear.textContent = '清除筛选';
    clear.addEventListener('click', () => {
      state.readerId = null;
      state.readerName = '';
      syncReaderLabel();
      dialog.close();
      load();
    });

    const apply = document.createElement('button');
    apply.type = 'button';
    apply.className = 'btn btn--primary';
    apply.textContent = '应用筛选';
    apply.addEventListener('click', () => {
      const selected = picker.getSelected();
      state.readerId = selected ? selected.id : null;
      state.readerName = selected ? selected.name : '';
      syncReaderLabel();
      dialog.close();
      load();
    });

    dialog.foot.append(clear, apply);
    setTimeout(() => picker.focus(), 60);
  }

  function syncReaderLabel() {
    if (!readerLabel) return;
    readerLabel.textContent = state.readerId
      ? `${state.readerName || `读者 #${state.readerId}`}`
      : '按读者筛选';
  }

  /* ---------- 事件 ---------- */

  delegate(root, 'click', '[data-action]', async (event, target) => {
    const action = target.dataset.action;
    const id = Number(target.dataset.id);
    const loan = state.records.find((item) => item.id === id);

    switch (action) {
      case 'filter': {
        state.filter = target.dataset.key;
        root.querySelectorAll('.chip').forEach((chip) => {
          chip.classList.toggle('is-active', chip.dataset.key === state.filter);
        });
        load();
        return;
      }
      case 'pick-reader':
        return openReaderFilter();
      case 'reload':
        return load();
      case 'return':
        if (loan) await returnLoan(loan, target);
        return;
      case 'pay':
        if (loan) await payFine(loan, target);
        return;
      default:
        return;
    }
  });

  if (admin && state.readerId) {
    // 从读者管理跳转过来时把姓名补上（失败不影响筛选）
    api
      .getReader(state.readerId)
      .then((detail) => {
        state.readerName = detail?.name || '';
        syncReaderLabel();
      })
      .catch(() => syncReaderLabel());
  }

  load();

  return () => {
    disposed = true;
  };
}
