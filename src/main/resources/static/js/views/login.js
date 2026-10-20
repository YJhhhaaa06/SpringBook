/**
 * 登录 / 注册（匿名接口）：POST /api/auth/login、POST /api/auth/register。
 * 注册成功直接签发 token（注册即登录），与后端语义一致。
 */
import { api } from '../core/api.js';
import { startSession } from '../core/session.js';
import { navigate } from '../core/router.js';
import { esc } from '../ui/format.js';
import { toastOk, toastError } from '../ui/toast.js';
import { applyFieldErrors, clearFieldErrors } from '../ui/components.js';
import { setBusy } from '../ui/dom.js';

const PHONE_RE = /^1[3-9]\d{9}$/;

// 注意：form.name 会被 HTMLFormElement 自身的 name 属性遮蔽，统一走 elements.namedItem
function readValue(form, fieldName) {
  const el = form.elements.namedItem(fieldName);
  return el && 'value' in el ? String(el.value) : '';
}

let mode = 'login';

export function render(root, { notice = '' } = {}) {
  root.innerHTML = `
    <div class="auth">
      <aside class="auth__aside">
        <div class="auth__brand">
          <span class="auth__mark">藏</span>
          <span>
            <span class="auth__brand-name">藏书阁</span>
            <span class="auth__brand-sub">SpringBook Library</span>
          </span>
        </div>
        <div class="auth__pitch">
          <h1>让每一本书<br />都有迹可循</h1>
          <p>藏书、借阅、预约、归还与罚款结算，在同一条链路上闭环。
             逾期自动计费，到书保留 48 小时，库存与队列严丝合缝。</p>
          <div class="auth__points">
            <span class="auth__point">图书检索</span>
            <span class="auth__point">借还闭环</span>
            <span class="auth__point">预约排队</span>
            <span class="auth__point">罚款结算</span>
          </div>
        </div>
        <div class="auth__foot">SPRINGBOOK · 图书信息管理系统</div>
      </aside>

      <main class="auth__main">
        <div class="auth-card">
          <div class="auth-card__head">
            <h2 class="auth-card__title" data-role="title">${mode === 'login' ? '登录' : '注册读者账号'}</h2>
            <p class="auth-card__sub" data-role="sub">${
              mode === 'login' ? '使用读者账号或管理员账号进入控制台' : '填写资料即创建读者档案，注册后自动登录'
            }</p>
          </div>

          ${notice ? `<div class="callout callout--warn" style="margin-bottom:16px">${esc(notice)}</div>` : ''}

          <div class="tabs" role="tablist">
            <button class="tab ${mode === 'login' ? 'is-active' : ''}" type="button" data-tab="login">登录</button>
            <button class="tab ${mode === 'register' ? 'is-active' : ''}" type="button" data-tab="register">注册</button>
          </div>

          <form novalidate data-role="form">
            <div class="field">
              <label class="field__label" for="login-username">用户名<span class="req">*</span></label>
              <input class="input" id="login-username" name="username" data-field="username"
                autocomplete="username" placeholder="admin" />
              <span class="field__err"></span>
            </div>

            <div class="field" data-only="register" hidden>
              <label class="field__label" for="login-name">姓名<span class="req">*</span></label>
              <input class="input" id="login-name" name="name" data-field="name" placeholder="张三" />
              <span class="field__err"></span>
            </div>

            <div class="field" data-only="register" hidden>
              <label class="field__label" for="login-phone">手机号<span class="req">*</span></label>
              <input class="input" id="login-phone" name="phone" data-field="phone"
                inputmode="numeric" placeholder="13800001111" />
              <span class="field__hint">11 位中国大陆手机号，与读者档案一一对应</span>
              <span class="field__err"></span>
            </div>

            <div class="field">
              <label class="field__label" for="login-password">密码<span class="req">*</span></label>
              <input class="input" id="login-password" name="password" data-field="password"
                type="password" autocomplete="current-password" placeholder="至少 6 位" />
              <span class="field__err"></span>
            </div>

            <button class="btn btn--primary btn--block" type="submit" data-role="submit">
              ${mode === 'login' ? '进入控制台' : '注册并登录'}
            </button>
          </form>

          <div class="auth-hint">
            默认管理员 <code>admin</code> / <code>admin123</code>（可用环境变量
            <code>ADMIN_PASSWORD</code> 覆盖），首次登录后请到「我的账户」修改口令。
          </div>
        </div>
      </main>
    </div>
  `;

  const form = root.querySelector('[data-role="form"]');
  const submit = root.querySelector('[data-role="submit"]');
  const title = root.querySelector('[data-role="title"]');
  const sub = root.querySelector('[data-role="sub"]');

  const syncMode = () => {
    root.querySelectorAll('[data-only="register"]').forEach((el) => {
      el.hidden = mode !== 'register';
    });
    root.querySelectorAll('[data-tab]').forEach((tab) => {
      tab.classList.toggle('is-active', tab.dataset.tab === mode);
    });
    title.textContent = mode === 'login' ? '登录' : '注册读者账号';
    sub.textContent =
      mode === 'login' ? '使用读者账号或管理员账号进入控制台' : '填写资料即创建读者档案，注册后自动登录';
    submit.textContent = mode === 'login' ? '进入控制台' : '注册并登录';
    form.querySelector('[name="password"]').setAttribute(
      'autocomplete',
      mode === 'login' ? 'current-password' : 'new-password',
    );
    clearFieldErrors(form);
  };

  root.querySelectorAll('[data-tab]').forEach((tab) => {
    tab.addEventListener('click', () => {
      if (mode === tab.dataset.tab) return;
      mode = tab.dataset.tab;
      syncMode();
      form.querySelector('[name="username"]').focus();
    });
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearFieldErrors(form);
    const values = {
      username: readValue(form, 'username').trim(),
      password: readValue(form, 'password'),
      name: readValue(form, 'name').trim(),
      phone: readValue(form, 'phone').trim(),
    };

    const errors = validate(values);
    if (Object.keys(errors).length) {
      applyFieldErrors(form, errors);
      return;
    }

    setBusy(submit, true, mode === 'login' ? '登录中…' : '注册中…');
    try {
      const data =
        mode === 'login'
          ? await api.login(values.username, values.password)
          : await api.register({
              username: values.username,
              password: values.password,
              name: values.name,
              phone: values.phone,
            });
      const session = startSession(data.token, data.expiresInSeconds);
      toastOk(
        mode === 'login' ? `欢迎回来，${session.username}` : `注册成功，${values.name}`,
        mode === 'login' ? '' : '已为你创建读者档案并登录',
      );
      navigate('/books');
    } catch (error) {
      // 用户名/手机号重复落到对应输入框，其余走 toast
      const mapped = { 40915: 'username', 40904: 'phone' };
      const field = mapped[error?.code];
      if (field && applyFieldErrors(form, { [field]: error.friendly })) {
        // 已内联提示
      } else {
        toastError(error);
      }
      setBusy(submit, false);
    }
  });

  syncMode();
  form.querySelector('[name="username"]').focus();
}

function validate(values) {
  const errors = {};
  if (!values.username) errors.username = '请输入用户名';
  else if (values.username.length < 3 || values.username.length > 50)
    errors.username = '用户名长度 3-50 个字符';

  if (!values.password) errors.password = '请输入密码';
  else if (values.password.length < 6 || values.password.length > 50)
    errors.password = '密码长度 6-50 个字符';

  if (mode === 'register') {
    if (!values.name) errors.name = '请输入姓名';
    else if (values.name.length > 50) errors.name = '姓名最长 50 个字符';
    if (!values.phone) errors.phone = '请输入手机号';
    else if (!PHONE_RE.test(values.phone)) errors.phone = '请输入 11 位中国大陆手机号';
  }
  return errors;
}
