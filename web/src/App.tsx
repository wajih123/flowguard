import React, { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { Loader } from "@/components/ui/Loader";

const ClientApp = lazy(() => import("./client/ClientApp"));
const AdminApp = lazy(() => import("./admin/AdminApp"));

const App: React.FC = () => (
  <Suspense fallback={<Loader fullScreen />}>
    <Routes>
      <Route path="/admin/*" element={<AdminApp />} />
      <Route path="/*" element={<ClientApp />} />
    </Routes>
  </Suspense>
);

export default App;
