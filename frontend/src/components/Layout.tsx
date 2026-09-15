import { useState } from "react";
import { Outlet } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

/** App shell: collapsible sidebar (mobile) + header + routed page content. */
export function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="app-shell">
      <Sidebar open={sidebarOpen} onNavigate={() => setSidebarOpen(false)} />

      {sidebarOpen && (
        <div
          className="sidebar-backdrop"
          role="button"
          tabIndex={0}
          aria-label="Close navigation menu"
          onClick={() => setSidebarOpen(false)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              setSidebarOpen(false);
            }
          }}
        />
      )}

      <div className="app-main">
        <Header onToggleSidebar={() => setSidebarOpen((open) => !open)} />
        <main className="app-content" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
