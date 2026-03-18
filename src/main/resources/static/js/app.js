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
    notificationCount.textContent = items.length;
    notificationCount.classList.toggle('d-none', items.length === 0);
    notificationList.innerHTML = items.length ? items.map(item => `
      <div class="notification-item">
        <div>${item.message}</div>
        <div class="notification-time">${new Date(item.createdAt).toLocaleString()}</div>
      </div>
    `).join('') : `<div class="empty-state">No notifications yet</div>`;
  }

  if (notificationToggle && notificationPanel) {
    notificationToggle.addEventListener('click', async () => {
      await loadNotifications();
      notificationPanel.classList.toggle('d-none');
    });
  }

  loadNotifications();
}

document.addEventListener('DOMContentLoaded', initAppShell);
