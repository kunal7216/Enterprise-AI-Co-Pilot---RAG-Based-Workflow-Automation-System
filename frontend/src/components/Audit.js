import React, { useState } from "react";
import API from "../api/api";

export default function Audit() {
  const [id, setId] = useState("");
  const [logs, setLogs] = useState([]);

  const load = async () => {
    const res = await API.get(`/api/v1/workflows/${id}/history`);
    setLogs(res.data);
  };

  return (
    <div>
      <h2>Audit Trail</h2>

      <input value={id} onChange={(e) => setId(e.target.value)} />
      <button onClick={load}>Load</button>

      {logs.map((l, i) => (
        <div key={i}>
          {l.fromStatus} → {l.toStatus}
        </div>
      ))}
    </div>
  );
}