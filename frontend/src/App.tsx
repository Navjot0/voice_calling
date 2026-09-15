import { Navigate, Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { Dashboard } from "./pages/Dashboard";
import { Calls } from "./pages/Calls";
import { MakeCall } from "./pages/MakeCall";
import { CallDetails } from "./pages/CallDetails";
import { Users } from "./pages/Users";
import { CreateUser } from "./pages/CreateUser";
import { UserDetails } from "./pages/UserDetails";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/dashboard" element={<Navigate to="/" replace />} />

        <Route path="/calls" element={<Calls />} />
        <Route path="/calls/new" element={<MakeCall />} />
        <Route path="/calls/:callId" element={<CallDetails />} />

        <Route path="/users" element={<Users />} />
        <Route path="/users/create" element={<CreateUser />} />
        <Route path="/users/:extension" element={<UserDetails />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

function NotFound() {
  return (
    <div className="page">
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist.</p>
    </div>
  );
}
