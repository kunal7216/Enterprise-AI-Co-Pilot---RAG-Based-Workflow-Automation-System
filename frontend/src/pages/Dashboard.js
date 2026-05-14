import { useEffect, useState } from "react";
import API from "../api/axios";

export default function Dashboard() {
  const [data, setData] = useState([]);

  useEffect(() => {
    fetchWorkflows();
  }, []);

  const fetchWorkflows = async () => {
    try {
      const res = await API.get("/api/v1/workflows?page=0&size=10");
      setData(res.data.content);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div>
      <h2>Workflows</h2>
      {data.map((item) => (
        <div key={item.id}>
          <p>{item.documentName}</p>
          <p>Status: {item.status}</p>
        </div>
      ))}
    </div>
  );
}