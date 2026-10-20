/** DOM 小工具：事件委托、元素查询、按钮忙碌态 */

export function qs(scope, selector) {
  return scope ? scope.querySelector(selector) : null;
}

export function qsa(scope, selector) {
  return scope ? Array.from(scope.querySelectorAll(selector)) : [];
}

/**
 * 事件委托：只在挂载时绑定一次，之后容器内 HTML 怎么替换都有效。
 * 用法：delegate(root, 'click', '[data-action]', (event, el) => ...)
 */
export function delegate(root, eventName, selector, handler) {
  const listener = (event) => {
    const matched = event.target instanceof Element ? event.target.closest(selector) : null;
    if (!matched || !root.contains(matched)) return;
    if (matched.disabled || matched.getAttribute('aria-disabled') === 'true') return;
    handler(event, matched);
  };
  root.addEventListener(eventName, listener);
  return () => root.removeEventListener(eventName, listener);
}

/** 按钮忙碌态：保留原文本，恢复时还原 */
export function setBusy(button, busy, busyText = '处理中…') {
  if (!button) return;
  if (busy) {
    if (!button.dataset.idleText) button.dataset.idleText = button.innerHTML;
    button.disabled = true;
    button.textContent = busyText;
  } else {
    button.disabled = false;
    if (button.dataset.idleText) {
      button.innerHTML = button.dataset.idleText;
      delete button.dataset.idleText;
    }
  }
}

export function fromHtml(html) {
  const template = document.createElement('template');
  template.innerHTML = String(html).trim();
  return template.content.firstElementChild;
}

/** 表单取值（按 name） */
export function formValues(form) {
  const data = {};
  for (const el of form.elements) {
    if (!el.name) continue;
    if (el.type === 'checkbox') data[el.name] = el.checked;
    else data[el.name] = typeof el.value === 'string' ? el.value.trim() : el.value;
  }
  return data;
}
