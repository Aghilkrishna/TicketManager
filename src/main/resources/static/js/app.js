function ensureAlertHost() {
  let host = document.getElementById('appAlertHost');
  if (host) return host;
  host = document.createElement('div');
  host.id = 'appAlertHost';
  host.className = 'app-alert-host';
  document.body.appendChild(host);
  return host;
}

function showAppAlert(message, variant = 'danger') {
  const host = ensureAlertHost();
  const alert = document.createElement('div');
  alert.className = `app-alert app-alert-${variant}`;
  alert.innerHTML = `
    <div class="app-alert-icon"><i class="bi ${variant === 'danger' ? 'bi-shield-exclamation' : 'bi-info-circle'}"></i></div>
    <div class="app-alert-body">
      <div class="app-alert-title">${variant === 'danger' ? 'Access Denied' : 'Notice'}</div>
      <div class="app-alert-message">${message}</div>
    </div>
    <button class="app-alert-close" type="button" aria-label="Close"><i class="bi bi-x-lg"></i></button>
  `;
  host.appendChild(alert);
  const close = () => {
    alert.classList.add('is-leaving');
    window.setTimeout(() => alert.remove(), 180);
  };
  alert.querySelector('.app-alert-close').addEventListener('click', close);
  window.setTimeout(close, 4200);
}

(function patchFetchForAccessDenied() {
  if (window.__tmFetchPatched) return;
  window.__tmFetchPatched = true;
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (...args) => {
    const response = await nativeFetch(...args);
    if (response.status === 403) {
      let message = 'You do not have permission to perform this action.';
      try {
        const payload = await response.clone().json();
        if (payload && payload.message) {
          message = payload.message;
        }
      } catch (_ignored) {
      }
      showAppAlert(message, 'danger');
    }
    return response;
  };
})();

function initAppShell() {
  const logoutBtn = document.getElementById('logoutBtn');
  const notificationToggle = document.getElementById('notificationToggle');
  const notificationPanel = document.getElementById('notificationPanel');
  const notificationList = document.getElementById('notificationList');
  const notificationCount = document.getElementById('notificationCount');
  const sidebar = document.querySelector('.sidebar');
  const sidebarToggle = document.getElementById('sidebarToggle');
  const sidebarCollapse = document.getElementById('sidebarCollapse');

  if (sidebarCollapse && sidebar) {
    const saved = localStorage.getItem('tm-sidebar-collapsed') === 'true';
    sidebar.classList.toggle('collapsed', saved);
    sidebarCollapse.addEventListener('click', () => {
      const collapsed = !sidebar.classList.contains('collapsed');
      sidebar.classList.toggle('collapsed', collapsed);
      localStorage.setItem('tm-sidebar-collapsed', collapsed);
    });
  }

  if (sidebarToggle && sidebar) {
    sidebarToggle.addEventListener('click', () => sidebar.classList.toggle('is-open'));
  }

  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      await fetch('/api/auth/logout', {method: 'POST'});
      window.location.href = '/login';
    });
  }

  async function loadNotifications() {
    if (!notificationList || !notificationCount) return;
    const res = await fetch('/api/notifications');
    if (!res.ok) return;
    const items = await res.json();
    notificationCount.textContent = '0';
    notificationCount.classList.add('d-none');
    notificationList.innerHTML = items.length ? items.map(item => `
      <div class="notification-item">
        <div>${item.message}</div>
        <div class="notification-time">${new Date(item.createdAt).toLocaleString()}</div>
      </div>
    `).join('') : `<div class="empty-state">No unseen notifications.</div>`;
  }

  async function loadNotificationCount() {
    if (!notificationCount) return;
    const res = await fetch('/api/notifications/count');
    if (!res.ok) return;
    const data = await res.json();
    notificationCount.textContent = data.count;
    notificationCount.classList.toggle('d-none', !data.count);
  }

  if (notificationToggle && notificationPanel) {
    notificationToggle.addEventListener('click', async () => {
      await loadNotifications();
      notificationPanel.classList.toggle('d-none');
    });
  }

  loadNotificationCount();

  if (typeof SockJS !== 'undefined' && typeof Stomp !== 'undefined') {
    const socket = new SockJS('/ws');
    const notificationClient = Stomp.over(socket);
    notificationClient.connect({}, () => {
      notificationClient.subscribe('/user/queue/notifications', ({body}) => {
        const item = JSON.parse(body);
        const current = Number(notificationCount.textContent || '0') || 0;
        notificationCount.textContent = String(current + 1);
        notificationCount.classList.remove('d-none');
        if (notificationPanel && !notificationPanel.classList.contains('d-none') && notificationList) {
          notificationList.insertAdjacentHTML('afterbegin', `
            <div class="notification-item">
              <div>${item.message}</div>
              <div class="notification-time">${new Date(item.createdAt).toLocaleString()}</div>
            </div>
          `);
        }
      });
    });
  }
}

document.addEventListener('DOMContentLoaded', initAppShell);
