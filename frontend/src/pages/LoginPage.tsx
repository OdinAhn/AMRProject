import { Navigate, useNavigate } from "react-router";
import { LoginForm } from "../components/auth/LoginForm";
import { homePathFor, useAuth } from "../lib/auth";

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  if (user) {
    return <Navigate to={homePathFor(user)} replace />;
  }

  const handleLogin = async (username: string, password: string) => {
    const loggedInUser = await login(username, password);
    navigate(homePathFor(loggedInUser), { replace: true });
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50">
      <LoginForm onSubmit={handleLogin} />
    </div>
  );
}
