import React from "react";
import "./sidebar.css";

const Sidebar = ({ onSelect }) => {
  const menu = [
    { name: "Dashboard", icon: "📊" },
    { name: "AI Chat", icon: "🤖" },
    { name: "Documents", icon: "📄" },
    { name: "Fraud Detection", icon: "🚨" },
    { name: "Analytics", icon: "📈" },
    { name: "Settings", icon: "⚙️" }
  ];

  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <h2>AI Copilot</h2>
      </div>

      <ul className="sidebar-menu">
        {menu.map((item, index) => (
          <li key={index} onClick={() => onSelect(item.name)}>
            <span className="icon">{item.icon}</span>
            <span>{item.name}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Sidebar;