import React, { useState } from "react";
import Dashboard from "../components/Dashboard";
import Workflows from "../components/Workflows";
import Upload from "../components/Upload";
import Analytics from "../components/Analytics";
import Audit from "../components/Audit";

export default function AppLayout() {
  const [page, setPage] = useState("dashboard");

  const renderPage = () => {
    switch (page) {
      case "dashboard": return <Dashboard />;
      case "workflows": return <Workflows />;
      case "upload": return <Upload />;
      case "analytics": return <Analytics />;
      case "audit": return <Audit />;
      default: return <Dashboard />;
    }
  };

  return (
    <div style={{ display: "flex", height: "100vh", background: "#0a0a0f", color: "white" }}>

      {/* Sidebar */}
      <div style={{ width: 220, background: "#111118", padding: 20 }}>
        <h3>AI Co-Pilot</h3>

        <button onClick={() => setPage("dashboard")}>Dashboard</button>
        <button onClick={() => setPage("workflows")}>Workflows</button>
        <button onClick={() => setPage("upload")}>Upload</button>
        <button onClick={() => setPage("analytics")}>Analytics</button>
        <button onClick={() => setPage("audit")}>Audit</button>
      </div>

      {/* Content */}
      <div style={{ flex: 1, padding: 20 }}>
        {renderPage()}
      </div>
    </div>
  );
}