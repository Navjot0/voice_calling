import { NavLink } from "react-router-dom";

interface SidebarProps {
  open: boolean;
  onNavigate: () => void;
}

interface NavLinkItem {
  to: string;
  label: string;
}

interface NavSection {
  label: string;
  links: NavLinkItem[];
}

const NAV_SECTIONS: NavSection[] = [
  {
    label: "Overview",
    links: [{ to: "/", label: "Dashboard" }],
  },
  {
    label: "Calls",
    links: [
      { to: "/calls", label: "All Calls" },
      { to: "/calls/new", label: "Make Call" },
    ],
  },
  {
    label: "Voice Users",
    links: [
      { to: "/users", label: "All Users" },
      { to: "/users/create", label: "Create User" },
    ],
  },
];

export function Sidebar({ open, onNavigate }: SidebarProps) {
  return (
    <nav className={`sidebar ${open ? "sidebar-open" : ""}`} aria-label="Primary navigation">
      <div className="sidebar-brand">
        <span className="sidebar-brand-mark" aria-hidden="true">
          ◎
        </span>
        <span>Voice Platform</span>
      </div>

      {NAV_SECTIONS.map((section) => (
        <div className="sidebar-section" key={section.label}>
          <p className="sidebar-section-label">{section.label}</p>
          <ul>
            {section.links.map((link) => (
              <li key={link.to}>
                <NavLink
                  to={link.to}
                  end
                  onClick={onNavigate}
                  className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}
                >
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
