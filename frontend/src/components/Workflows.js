import React, { useEffect, useState } from "react";
import API from "../api/api";

export default function Workflows() {
  const [data, setData] = useState([]);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    const res = await API.get("/api/v1/workflows?page=0&size=20");
    setData(res.data.content || []);
  };

  return (
    <div>
      <h2>Workflows</h2>

      {data.map((w) => (
        <div key={w.id} style={{ padding: 10, borderBottom: "1px solid gray" }}>
          <p>#{w.id} - {w.documentName}</p>
          <p>Status: {w.status}</p>
          <p>Confidence: {w.confidenceScore}</p>
        </div>
      ))}
    </div>
  );
}