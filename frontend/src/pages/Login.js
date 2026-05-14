import { useState } from "react";
import API from "../api/axios";

export default function Login() {
  const [form, setForm] = useState({
    username: "",
    password: ""
  });

  const handleLogin = async () => {
    try {
      const res = await API.post("/api/auth/login", form);

      localStorage.setItem("token", res.data.token);

      alert("Login Success");
      window.location.href = "/dashboard";

    } catch (err) {
      alert("Login Failed");
    }
  };

  return (
    <div>
      <input
        placeholder="Username"
        onChange={(e) => setForm({ ...form, username: e.target.value })}
      />
      <input
        type="password"
        placeholder="Password"
        onChange={(e) => setForm({ ...form, password: e.target.value })}
      />
      <button onClick={handleLogin}>Login</button>
    </div>
  );
}