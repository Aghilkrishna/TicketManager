function ensureAlertHost() {
  let host = document.getElementById('appAlertHost');
  if (host) return host;
  host = document.createElement('div');
  host.id = 'appAlertHost';
  host.className = 'app-alert-host';
  document.body.appendChild(host);
  return host;
}

function alertMeta(variant, title) {
  if (title) {
    return {
      title,
      icon: variant === 'success' ? 'bi-check-circle' : variant === 'warning' ? 'bi-exclamation-triangle' : variant === 'info' ? 'bi-info-circle' : 'bi-shield-exclamation'
    };
  }
  if (variant === 'success') {
    return {title: 'Success', icon: 'bi-check-circle'};
  }
  if (variant === 'warning') {
    return {title: 'Warning', icon: 'bi-exclamation-triangle'};
  }
  if (variant === 'info') {
    return {title: 'Notice', icon: 'bi-info-circle'};
  }
  return {title: 'Access Denied', icon: 'bi-shield-exclamation'};
}

function showAppAlert(message, variant = 'danger', title = null) {
  const host = ensureAlertHost();
  const meta = alertMeta(variant, title);
  const alert = document.createElement('div');
  alert.className = `app-alert app-alert-${variant}`;
  alert.innerHTML = `
    <div class="app-alert-icon"><i class="bi ${meta.icon}"></i></div>
    <div class="app-alert-body">
      <div class="app-alert-title">${meta.title}</div>
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

// Device detection function
function isMobileDevice() {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) ||
         (window.innerWidth <= 768 && 'ontouchstart' in window);
}

// Build appropriate map URL based on device
function buildMapUrl(latitude, longitude) {
  const coords = latitude + ',' + longitude;
  const encodedCoords = encodeURIComponent(coords);
  
  if (isMobileDevice()) {
    // For mobile devices, use Google Maps app URL scheme
    return `https://maps.google.com/maps?q=${encodedCoords}`;
  } else {
    // For desktop, use web Google Maps
    return `https://www.google.com/maps?q=${encodedCoords}`;
  }
}

// Handle external map links with device detection
function handleMapLink(url) {
  if (isMobileDevice()) {
    // For mobile devices, try to open in Google Maps app
    // Convert web URL to app URL if needed
    if (url.includes('maps.google.com')) {
      // Already a Google Maps URL, use as-is
      return url;
    } else if (url.includes('google.com/maps')) {
      // Convert to mobile app URL
      return url.replace('www.google.com/maps', 'maps.google.com/maps');
    }
  }
  // For desktop or if URL is already properly formatted, use as-is
  return url;
}

function showInlineAlert(target, message, variant = 'danger', title = null) {
  if (!target) return;
  const meta = alertMeta(variant, title);
  target.hidden = false;
  target.className = `app-inline-alert app-inline-alert-${variant}`;
  target.innerHTML = `
    <div class="app-inline-alert-icon"><i class="bi ${meta.icon}"></i></div>
    <div class="app-inline-alert-copy">
      <div class="app-inline-alert-title">${meta.title}</div>
      <div class="app-inline-alert-text">${message}</div>
    </div>
  `;
}

function clearInlineAlert(target) {
  if (!target) return;
  target.hidden = true;
  target.className = '';
  target.innerHTML = '';
}

function setButtonLoading(button, loading, loadingText = 'Processing...') {
  if (!button) return;
  if (loading) {
    if (!button.dataset.originalHtml) {
      button.dataset.originalHtml = button.innerHTML;
    }
    button.disabled = true;
    button.innerHTML = `<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>${loadingText}`;
    return;
  }
  button.disabled = false;
  if (button.dataset.originalHtml) {
    button.innerHTML = button.dataset.originalHtml;
  }
}

function debounce(fn, wait = 250) {
  let timeoutId = null;
  return (...args) => {
    window.clearTimeout(timeoutId);
    timeoutId = window.setTimeout(() => fn(...args), wait);
  };
}

window.showAppAlert = showAppAlert;
window.showInlineAlert = showInlineAlert;
window.clearInlineAlert = clearInlineAlert;
window.setButtonLoading = setButtonLoading;
window.debounce = debounce;

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

  if (sidebar) {
    const subnavToggles = Array.from(sidebar.querySelectorAll('[data-subnav-toggle]'));

    const closeSubnav = toggle => {
      const subnavId = toggle.getAttribute('data-subnav-toggle');
      const subnav = subnavId ? document.getElementById(subnavId) : null;
      toggle.setAttribute('aria-expanded', 'false');
      if (subnav) {
        subnav.classList.remove('is-open');
      }
    };

    const openSubnav = toggle => {
      const subnavId = toggle.getAttribute('data-subnav-toggle');
      const subnav = subnavId ? document.getElementById(subnavId) : null;
      toggle.setAttribute('aria-expanded', 'true');
      if (subnav) {
        subnav.classList.add('is-open');
      }
    };

    const closeOtherSubnavs = currentToggle => {
      subnavToggles.forEach(toggle => {
        if (toggle !== currentToggle) {
          closeSubnav(toggle);
        }
      });
    };

    subnavToggles.forEach(toggle => {
      const subnavId = toggle.getAttribute('data-subnav-toggle');
      const subnav = subnavId ? document.getElementById(subnavId) : null;
      if (!subnav) return;

      // Keep initial state aligned between ARIA and CSS class.
      const isExpanded = toggle.getAttribute('aria-expanded') === 'true';
      subnav.classList.toggle('is-open', isExpanded);

      toggle.addEventListener('click', event => {
        event.preventDefault();
        const expanded = toggle.getAttribute('aria-expanded') === 'true';
        if (expanded) {
          closeSubnav(toggle);
          return;
        }
        closeOtherSubnavs(toggle);
        openSubnav(toggle);
      });
    });

    // If multiple sections are marked open by markup, keep only the first as open.
    const initiallyExpanded = subnavToggles.filter(toggle => toggle.getAttribute('aria-expanded') === 'true');
    if (initiallyExpanded.length > 1) {
      initiallyExpanded.slice(1).forEach(closeSubnav);
    }
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

function initDefaultDateFields() {
  const now = new Date();
  const localNow = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  const currentDate = localNow.toISOString().slice(0, 10);
  const currentDateTime = localNow.toISOString().slice(0, 16);
  document.querySelectorAll('input[type="date"]').forEach(input => {
    if (!input.value) {
      input.value = currentDate;
    }
  });
  document.querySelectorAll('input[type="datetime-local"]').forEach(input => {
    if (!input.value) {
      input.value = currentDateTime;
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  initAppShell();
  initDefaultDateFields();
});
