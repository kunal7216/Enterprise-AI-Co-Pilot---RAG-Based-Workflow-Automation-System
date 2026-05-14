import { useState } from "react";
import API from "../api/axios";

export default function Upload() {
  const [file, setFile] = useState(null);

  const handleUpload = async () => {
    const formData = new FormData();
    formData.append("file", file);

    try {
      await API.post("/api/v1/workflows/upload", formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      });

      alert("Uploaded successfully");
    } catch (err) {
      alert("Upload failed");
    }
  };

  return (
    <div>
      <input type="file" onChange={(e) => setFile(e.target.files[0])} />
      <button onClick={handleUpload}>Upload</button>
    </div>
  );
}