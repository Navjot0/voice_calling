interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  return (
    <header className="app-header">
      <button
        type="button"
        className="icon-button sidebar-toggle"
        onClick={onToggleSidebar}
        aria-label="Toggle navigation menu"
      >
        <span aria-hidden="true">☰</span>
      </button>
      <div className="app-header-title">
        <h1>extension-calling Console</h1>
        <p>Programmable voice calling &amp; extension provisioning</p>
      </div>
    </header>
  );
}
